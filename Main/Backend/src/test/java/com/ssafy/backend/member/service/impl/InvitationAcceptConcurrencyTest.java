package com.ssafy.backend.member.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.entity.Invitation;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.repository.InvitationRepository;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.member.service.InvitationService;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.support.TeamRowLockProbe;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 초대 수락의 정원(스페이스당 10명) 검사가 동시 수락에서도 지켜지는지 검증한다.
 *
 * <p>정원 검사는 "멤버 수를 세고 → 10 미만이면 등록"이라, 두 수락이 같은 9명을 보고 둘 다 등록하면
 * 11명이 된다. 수락 경로가 teams 행 잠금을 잡아 직렬화되는지를 실제 PostgreSQL에서 확인한다.
 * 서비스가 자체 트랜잭션을 커밋하므로 테스트가 만든 행은 직접 지운다.</p>
 */
@Tag("integration")
@SpringBootTest
@DisplayName("초대 수락 정원 동시성 통합 테스트")
class InvitationAcceptConcurrencyTest {

    private static final int MAX_MEMBER_COUNT = 10;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> userIds = new ArrayList<>();
    private final List<UUID> invitationIds = new ArrayList<>();
    private Long ownerId;
    private Long teamId;

    @BeforeEach
    void setUp() {
        // 정원에 한 자리만 남은 스페이스(소유자 1 + 멤버 8 = 9명).
        ownerId = newUser();
        teamId = teamRepository.save(
                Team.builder().name("정원 테스트").ownerId(ownerId).color("#123456").build()).getId();
        memberRepository.save(Member.owner(ownerId, teamId, "소유자"));
        for (int i = 0; i < MAX_MEMBER_COUNT - 2; i++) {
            memberRepository.save(Member.invited(newUser(), teamId, "멤버" + i));
        }
    }

    @AfterEach
    void cleanUp() {
        // FK 순서: members가 users·teams를 참조하므로 member부터 지운다.
        // 거절된 쪽의 초대는 남는다(자리가 나면 다시 수락할 수 있어야 하므로). 수락된 초대는 이미 지워져 있다.
        invitationRepository.deleteAllById(invitationIds);
        memberRepository.deleteAll(memberRepository.findByTeamId(teamId));
        teamRepository.deleteById(teamId);
        userRepository.deleteAllById(userIds);
    }

    @Test
    @DisplayName("MEMBER-03 초대 수락은 teams 행 잠금이 풀릴 때까지 기다린 뒤 수행된다")
    void acceptInvitationWaitsForTeamRowLock() throws Exception {
        Long targetUserId = newUser();
        UUID invitationId = newInvitation(targetUserId);
        TeamRowLockProbe lockProbe = new TeamRowLockProbe(transactionManager, teamRepository);

        TeamRowLockProbe.Outcome<Object> outcome = lockProbe.callWhileTeamLocked(teamId, () -> {
            invitationService.acceptInvitation(targetUserId, invitationId.toString());
            return null;
        });

        assertThat(outcome.waitedForLock()).isTrue();
        assertThat(outcome.error()).isNull();
        assertThat(memberRepository.existsByTeamIdAndUserId(teamId, targetUserId)).isTrue();
    }

    @RepeatedTest(5)
    @DisplayName("한 자리 남은 스페이스에 두 명이 동시에 수락해도 한 명만 합류하고 정원 10명을 넘지 않는다")
    void concurrentAcceptsNeverExceedMemberLimit() throws Exception {
        Long firstUserId = newUser();
        Long secondUserId = newUser();
        UUID firstInvitationId = newInvitation(firstUserId);
        UUID secondInvitationId = newInvitation(secondUserId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ErrorCode> first = executor.submit(() -> {
                start.await();
                return acceptAndGetError(firstUserId, firstInvitationId);
            });
            Future<ErrorCode> second = executor.submit(() -> {
                start.await();
                return acceptAndGetError(secondUserId, secondInvitationId);
            });
            start.countDown();

            List<ErrorCode> errors = new ArrayList<>();
            errors.add(first.get(10, TimeUnit.SECONDS));
            errors.add(second.get(10, TimeUnit.SECONDS));

            assertThat(memberRepository.countByTeamId(teamId)).isEqualTo(MAX_MEMBER_COUNT);
            assertThat(errors).containsExactlyInAnyOrder(null, ErrorCode.MEMBER_LIMIT_EXCEEDED);
        } finally {
            executor.shutdownNow();
        }
    }

    private ErrorCode acceptAndGetError(Long userId, UUID invitationId) {
        try {
            invitationService.acceptInvitation(userId, invitationId.toString());
            return null;
        } catch (CustomException e) {
            return e.getErrorCode();
        }
    }

    private Long newUser() {
        Long id = userRepository.save(
                User.builder().email("cap-" + UUID.randomUUID() + "@test.com").password("enc").nickname("수락자").build())
                .getId();
        userIds.add(id);
        return id;
    }

    private UUID newInvitation(Long targetUserId) {
        UUID id = invitationRepository.saveAndFlush(
                Invitation.create(teamId, ownerId, targetUserId, Duration.ofDays(1))).getInvitationId();
        invitationIds.add(id);
        return id;
    }
}
