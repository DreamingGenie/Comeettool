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
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * acceptInvitation()의 실제 트랜잭션(커밋/롤백) 동작 검증 전용 테스트.
 *
 * <p>Mock 기반 {@code InvitationServiceImplTest}는 {@code invitationRepository.delete(...)}가 "호출"됐는지만
 * 확인하는데, 이는 실제 스프링 트랜잭션 프록시가 없어 "예외로 트랜잭션이 롤백되며 그 delete까지 무효화되는" 문제를
 * 절대 잡아내지 못한다. {@code @DataJpaTest}(테스트별 자동 롤백)로도 확인 불가하다 — 서비스 메서드가 정말로
 * 자체 트랜잭션을 시작·커밋하는지를 봐야 하므로, 테스트 자체는 트랜잭션을 걸지 않는 {@code @SpringBootTest}로
 * 실제 스프링 빈(AOP 프록시 포함)을 통해 호출하고, 별도 조회로 커밋 여부를 확인한다.
 *
 * <p>MemberRepositoryTest와 동일한 이유로 User·Team을 먼저 영속화한다 — members는 users·teams로의 실제 FK
 * 제약이 있다(반면 invitations는 FK가 없어 team_id/inviter_id는 임의 값으로 충분하다).
 * 실행 전 SSH 터널이 연결돼 있어야 하며, 각 테스트가 만든 데이터는 자동 롤백되지 않으므로 직접 정리한다.</p>
 */
@SpringBootTest
@DisplayName("InvitationServiceImpl 트랜잭션 통합 테스트")
class InvitationServiceImplTransactionTest {

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

    private Long memberId;
    private UUID invitationId;
    private Long teamId;
    private Long userId;

    @AfterEach
    void cleanUp() {
        // FK 순서: members가 users·teams를 참조하므로 member부터 지운다.
        if (memberId != null) {
            memberRepository.deleteById(memberId);
        }
        if (invitationId != null) {
            invitationRepository.deleteById(invitationId);
        }
        if (teamId != null) {
            teamRepository.deleteById(teamId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    private Long uniqueId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }

    @Test
    @DisplayName("이미 멤버인 상태로 수락을 시도해 MEMBER_ALREADY_JOINED가 나도, 함께 정리한 초대 삭제는 실제로 커밋된 채 유지된다")
    void acceptInvitation_keepsInvitationDeletedDespiteThrowingException() {
        // given — targetUserId가 teamId의 멤버로 이미 존재 + 같은 팀에 대한 유효한 초대도 남아있는 상태
        // (같은 초대를 중복/동시 수락해 한쪽이 먼저 멤버 등록을 마친 상황을 재현).
        User user = userRepository.save(
                User.builder().email("tx-" + UUID.randomUUID() + "@test.com").password("enc").build());
        userId = user.getId();

        Team team = teamRepository.save(Team.builder().name("팀").ownerId(userId).color("#123456").build());
        teamId = team.getId();

        Member member = memberRepository.save(Member.invited(userId, teamId, "닉네임"));
        memberId = member.getId();

        Invitation invitation = invitationRepository.saveAndFlush(
                Invitation.create(teamId, uniqueId(), userId, Duration.ofDays(1)));
        invitationId = invitation.getInvitationId();

        // when — 수락 시도(이미 멤버라 예외가 난다).
        assertThatThrownBy(() -> invitationService.acceptInvitation(userId, invitationId.toString()))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_ALREADY_JOINED);

        // then — 예외가 났어도 트랜잭션이 롤백되지 않고, 초대 삭제는 실제로(DB에) 반영돼 있어야 한다.
        assertThat(invitationRepository.findById(invitationId)).isEmpty();
        invitationId = null; // 이미 삭제됐으니 cleanUp에서 다시 지우지 않도록.
    }
}
