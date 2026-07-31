package com.ssafy.backend.member.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.entity.Invitation;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseMyInvitationDto;
import com.ssafy.backend.member.repository.InvitationRepository;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.member.service.InvitationService;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MEMBER-02 멤버 초대. 초대는 invitations 테이블(RDB)에 저장하며, 상태는 expires_at 경과 여부로만 판단한다(별도 상태 필드 없음).
 * (team_id, target_user_id) 유니크 제약이 "이미 대기 중인 초대" 중복 생성을 DB 레벨에서 최종 방어한다.
 * 수락/거절 시 처리, 만료 초대 물리 삭제(배치/스케줄러)는 다음 작업 범위.
 */
@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final Duration TTL = Duration.ofDays(1);
    private static final String UNKNOWN_INVITER_NICKNAME = "(알 수 없음)";

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final InvitationRepository invitationRepository;

    @Override
    @Transactional
    public ResponseInviteMemberDto inviteMember(Long inviterId, Long spaceId, RequestInviteMemberDto request) {
        Team team = teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        // 초대는 스페이스 소유자만 가능(SPACE-11/101과 동일한 Owner 전용 정책).
        if (!team.getOwnerId().equals(inviterId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        Long targetUserId = request.targetUserId();
        if (!userRepository.existsById(targetUserId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (memberRepository.existsByTeamIdAndUserId(spaceId, targetUserId)) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_JOINED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (invitationRepository.existsByTeamIdAndTargetUserIdAndExpiresAtAfter(spaceId, targetUserId, now)) {
            throw new CustomException(ErrorCode.INVITATION_ALREADY_PENDING);
        }

        // 위 체크를 통과했다면 이 (team_id, target_user_id) 쌍의 행이 남아있어도 반드시 만료된 것이다.
        // invitations는 만료돼도 물리 삭제되지 않으므로(배치 정리는 범위 밖) 그대로 두면 유니크 제약에 걸려
        // 정상적인 재초대까지 막힌다 — 새 초대를 만들기 전에 먼저 지운다.
        invitationRepository.deleteByTeamIdAndTargetUserId(spaceId, targetUserId);

        Invitation invitation = Invitation.create(spaceId, inviterId, targetUserId, TTL);
        try {
            // saveAndFlush로 즉시 INSERT를 실행해, 유니크 제약 위반을 이 트랜잭션 안에서 바로 잡아낸다.
            invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException e) {
            // (team_id, target_user_id) 유니크 제약 위반 — 위 existsBy 체크 이후 동시에 들어온 요청이 먼저 커밋된 경우의 race condition 방어.
            throw new CustomException(ErrorCode.INVITATION_ALREADY_PENDING);
        }

        return new ResponseInviteMemberDto(invitation.getInvitationId().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResponseMyInvitationDto> findMyInvitations(Long userId) {
        List<Invitation> invitations =
                invitationRepository.findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(userId, OffsetDateTime.now());

        // 스페이스 삭제 시 관련 초대는 함께 정리되므로 이론상 team이 없는 초대는 없지만, 방어적으로 없으면 결과에서 제외한다(목록 조회는 부분 실패로 전체가 깨지면 안 됨).
        return invitations.stream()
                .flatMap(invitation -> teamRepository.findById(invitation.getTeamId())
                        .map(team -> toResponseMyInvitationDto(invitation, team))
                        .stream())
                .toList();
    }

    private ResponseMyInvitationDto toResponseMyInvitationDto(Invitation invitation, Team team) {
        return new ResponseMyInvitationDto(
                invitation.getInvitationId().toString(),
                team.getId(),
                team.getName(),
                resolveInviterNickname(invitation.getInviterId()),
                invitation.getCreatedAt()
        );
    }

    // 탈퇴(is_deleted=true)했거나 존재하지 않는 초대자는 닉네임 대신 fallback 값을 사용한다.
    private String resolveInviterNickname(Long inviterId) {
        return userRepository.findById(inviterId)
                .filter(user -> !user.isDeleted())
                .map(User::getNickname)
                .orElse(UNKNOWN_INVITER_NICKNAME);
    }

    @Override
    // noRollbackFor: "이미 멤버" 분기(중복 수락 등으로 도달)는 정리(invitation 삭제) 후 예외를 던지는데,
    // CustomException은 RuntimeException이라 기본 규칙대로면 트랜잭션 전체가 롤백돼 방금 한 삭제까지 무효화된다.
    // 이 메서드의 모든 CustomException 분기는 그 시점까지 반영해도 되는(오히려 반영돼야 하는) 상태이므로 안전하다.
    @Transactional(noRollbackFor = CustomException.class)
    public void acceptInvitation(Long userId, String invitationId) {
        UUID id;
        try {
            id = UUID.fromString(invitationId);
        } catch (IllegalArgumentException e) {
            // UUID 형식이 아닌 값도 "존재하지 않는 초대"와 동일하게 처리 — 형식 오류라고 500을 낼 이유가 없다.
            throw new CustomException(ErrorCode.INVITATION_NOT_FOUND);
        }

        Invitation invitation = invitationRepository
                .findByInvitationIdAndExpiresAtAfter(id, OffsetDateTime.now())
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));

        // 만료/미존재와 동일한 코드로 응답 — 타인의 초대 존재 여부·소유자를 노출하지 않는다.
        if (!invitation.getTargetUserId().equals(userId)) {
            throw new CustomException(ErrorCode.INVITATION_NOT_FOUND);
        }

        Long teamId = invitation.getTeamId();
        if (memberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            // 이미 멤버가 됐다는 것은 이 초대가 더 이상 의미 없다는 뜻 — 초대도 함께 정리한다.
            // (주로 같은 초대를 중복/동시 수락했을 때 도달 — 먼저 처리된 요청이 이미 멤버로 등록을 마친 경우.)
            invitationRepository.delete(invitation);
            throw new CustomException(ErrorCode.MEMBER_ALREADY_JOINED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_UNAUTHORIZED));

        Member member = Member.invited(userId, teamId, user.getNickname());
        memberRepository.save(member);
        invitationRepository.delete(invitation);
    }
}