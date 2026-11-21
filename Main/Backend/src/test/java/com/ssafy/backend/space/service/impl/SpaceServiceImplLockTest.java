package com.ssafy.backend.space.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.dto.RequestTransferOwnerDto;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.space.service.SpaceService;
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
 * 스페이스 소유자 상태를 바꾸는 경로(나가기·삭제·위임)가 teams 행 비관적 잠금으로 직렬화되는지 검증한다.
 *
 * <p>Mockito 기반 {@code SpaceServiceImplTest}는 {@code findActiveByIdForUpdate}가 "호출됐는지"만 본다.
 * 잠금이 실제로 다른 트랜잭션을 기다리게 하는지는 DB가 있어야 확인되므로 실제 PostgreSQL에 붙는다.
 * 서비스가 자체 트랜잭션을 커밋하므로 테스트가 만든 행은 직접 지운다.</p>
 */
@Tag("integration")
@SpringBootTest
@DisplayName("SpaceServiceImpl 잠금 통합 테스트")
class SpaceServiceImplLockTest {

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TeamRowLockProbe lockProbe;
    private final List<Long> teamIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    private Long ownerId;
    private Long memberUserId;
    private Long teamId;

    @BeforeEach
    void setUp() {
        lockProbe = new TeamRowLockProbe(transactionManager, teamRepository);
        ownerId = newUser();
        memberUserId = newUser();
        teamId = newTeamWithOwnerAndMember(ownerId, memberUserId);
    }

    @AfterEach
    void cleanUp() {
        // FK 순서: members가 users·teams를 참조하므로 member부터 지운다.
        for (Long id : teamIds) {
            memberRepository.deleteAll(memberRepository.findByTeamId(id));
            teamRepository.deleteById(id);
        }
        userRepository.deleteAllById(userIds);
    }

    @Test
    @DisplayName("SPACE-101 소유권 위임은 teams 행 잠금이 풀릴 때까지 기다린 뒤 수행된다")
    void transferOwnerWaitsForTeamRowLock() throws Exception {
        TeamRowLockProbe.Outcome<Object> outcome = lockProbe.callWhileTeamLocked(teamId,
                () -> spaceService.transferOwner(ownerId, teamId, new RequestTransferOwnerDto(memberUserId)));

        assertThat(outcome.waitedForLock()).isTrue();
        assertThat(outcome.error()).isNull();
        assertThat(teamRepository.findById(teamId).orElseThrow().getOwnerId()).isEqualTo(memberUserId);
    }

    @Test
    @DisplayName("SPACE-07 나가기는 teams 행 잠금이 풀릴 때까지 기다린 뒤 수행된다")
    void removeMyMembershipWaitsForTeamRowLock() throws Exception {
        TeamRowLockProbe.Outcome<Object> outcome = lockProbe.callWhileTeamLocked(teamId, () -> {
            spaceService.removeMyMembership(memberUserId, teamId);
            return null;
        });

        assertThat(outcome.waitedForLock()).isTrue();
        assertThat(outcome.error()).isNull();
        assertThat(memberRepository.existsByTeamIdAndUserId(teamId, memberUserId)).isFalse();
    }

    @Test
    @DisplayName("SPACE-11 삭제는 teams 행 잠금이 풀릴 때까지 기다린 뒤 수행된다")
    void removeSpaceWaitsForTeamRowLock() throws Exception {
        TeamRowLockProbe.Outcome<Object> outcome = lockProbe.callWhileTeamLocked(teamId, () -> {
            spaceService.removeSpace(ownerId, teamId);
            return null;
        });

        assertThat(outcome.waitedForLock()).isTrue();
        assertThat(outcome.error()).isNull();
        assertThat(teamRepository.findByIdAndIsDeletedFalse(teamId)).isEmpty();
    }

    @Test
    @DisplayName("SPACE-04 상세 조회는 잠금을 걸지 않아 teams 행이 잠겨 있어도 바로 응답한다")
    void findSpaceDetailsDoesNotWaitForTeamRowLock() throws Exception {
        TeamRowLockProbe.Outcome<Object> outcome = lockProbe.callWhileTeamLocked(teamId,
                () -> spaceService.findSpaceDetails(memberUserId, teamId));

        assertThat(outcome.waitedForLock()).isFalse();
        assertThat(outcome.error()).isNull();
    }

    @RepeatedTest(5)
    @DisplayName("위임과 위임 대상의 나가기가 동시에 와도 소유자가 멤버가 아닌 상태는 생기지 않는다")
    void concurrentTransferAndLeaveNeverLeavesOwnerWithoutMembership() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ErrorCode> transfer = executor.submit(() -> {
                start.await();
                return errorCodeOf(() -> spaceService.transferOwner(
                        ownerId, teamId, new RequestTransferOwnerDto(memberUserId)));
            });
            Future<ErrorCode> leave = executor.submit(() -> {
                start.await();
                return errorCodeOf(() -> spaceService.removeMyMembership(memberUserId, teamId));
            });
            start.countDown();

            ErrorCode transferError = transfer.get(10, TimeUnit.SECONDS);
            ErrorCode leaveError = leave.get(10, TimeUnit.SECONDS);

            // 둘 중 정확히 하나만 성공한다. 진 쪽은 이긴 쪽이 커밋한 상태를 보고 정책대로 거절된다.
            if (transferError == null) {
                assertThat(leaveError).isEqualTo(ErrorCode.SPACE_OWNER_MUST_TRANSFER);
            } else {
                assertThat(leaveError).isNull();
                assertThat(transferError).isEqualTo(ErrorCode.SPACE_MEMBER_NOT_FOUND);
            }

            Long currentOwnerId = teamRepository.findById(teamId).orElseThrow().getOwnerId();
            Member ownerMember = memberRepository.findByTeamIdAndUserId(teamId, currentOwnerId).orElseThrow();
            assertThat(ownerMember.getAuthority()).isEqualTo(MemberAuthority.OWNER);
        } finally {
            executor.shutdownNow();
        }
    }

    private ErrorCode errorCodeOf(Runnable call) {
        try {
            call.run();
            return null;
        } catch (CustomException e) {
            return e.getErrorCode();
        }
    }

    private Long newUser() {
        Long id = userRepository.save(
                User.builder().email("lock-" + UUID.randomUUID() + "@test.com").password("enc").build()).getId();
        userIds.add(id);
        return id;
    }

    private Long newTeamWithOwnerAndMember(Long ownerUserId, Long otherUserId) {
        Long id = teamRepository.save(
                Team.builder().name("잠금 테스트").ownerId(ownerUserId).color("#123456").build()).getId();
        teamIds.add(id);
        memberRepository.save(Member.owner(ownerUserId, id, "소유자"));
        memberRepository.save(Member.invited(otherUserId, id, "멤버"));
        return id;
    }
}
