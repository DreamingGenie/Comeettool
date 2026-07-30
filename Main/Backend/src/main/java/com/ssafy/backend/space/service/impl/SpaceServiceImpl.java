package com.ssafy.backend.space.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.RequestTransferOwnerDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.dto.ResponseTransferOwnerDto;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.document.repository.DocumentRepository;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.space.mapper.SpaceMapper;
import com.ssafy.backend.space.service.SpaceService;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 팀 스페이스(teams/members) 조회·생성 로직 (SPACE-01/02/05).
 * Controller는 요청/응답만, 비즈니스 로직은 이 계층에 둔다(코드 컨벤션).
 */
@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final SpaceMapper spaceMapper;

    @Override
    @Transactional
    public ResponseCreateSpaceDto addSpace(Long userId, RequestCreateSpaceDto request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_UNAUTHORIZED));

        Team team = Team.builder()
                .name(request.teamName())
                .description(request.teamDescription())
                .ownerId(userId)
                .profileImageUrl(request.teamProfileImage())
                .color(request.teamColor())
                .build();
        Team savedTeam = teamRepository.save(team);

        // 생성자를 Owner 멤버로 등록. members.nickname 은 NOT NULL 이라 표시용 닉네임을 채운다.
        Member ownerMember = Member.owner(userId, savedTeam.getId(), resolveNickname(owner));
        memberRepository.save(ownerMember);

        return spaceMapper.toCreateResponse(savedTeam);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResponseSpaceListDto> findSpaceList(Long userId) {
        // [Team, 내 권한] 을 한 번의 조인 쿼리로 가져온다.
        List<Object[]> rows = memberRepository.findActiveTeamsWithMyAuthority(userId);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 참여자 수 일괄 집계 — teamId → count (N+1 방지).
        List<Long> teamIds = rows.stream().map(row -> ((Team) row[0]).getId()).toList();
        Map<Long, Long> memberCountByTeam = new HashMap<>();
        for (Object[] row : memberRepository.countMembersByTeamIds(teamIds)) {
            memberCountByTeam.put((Long) row[0], (Long) row[1]);
        }

        return rows.stream()
                .map(row -> {
                    Team team = (Team) row[0];
                    MemberAuthority authority = (MemberAuthority) row[1];
                    long count = memberCountByTeam.getOrDefault(team.getId(), 0L);
                    return spaceMapper.toListItem(team, authority.name(), count);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseSpaceDetailDto findSpaceDetails(Long userId, Long spaceId) {
        Team team = loadActiveTeam(spaceId);

        // 요청자가 해당 스페이스 멤버인지 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(spaceId, userId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        List<Member> members = memberRepository.findByTeamId(spaceId);
        return spaceMapper.toDetailResponse(team, members);
    }

    @Override
    @Transactional
    public void removeMyMembership(Long userId, Long spaceId) {
        Team team = loadActiveTeamForUpdate(spaceId);

        // 요청자의 멤버 행을 조회하며 멤버 여부를 검사한다.
        Member member = memberRepository.findByTeamIdAndUserId(spaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_ACCESS_DENIED));

        // 정책 SP-1: 소유자는 바로 나갈 수 없다. 남은 멤버 수로 다음 행동을 분기해 안내한다.
        if (isOwner(team, userId)) {
            if (memberRepository.countByTeamId(spaceId) <= 1) {
                // 혼자뿐인 소유자 → 나가기 대신 스페이스 삭제(SPACE-11)로 유도.
                throw new CustomException(ErrorCode.SPACE_OWNER_LAST_MEMBER);
            }
            // 다른 멤버가 있는 소유자 → 소유권 위임(SPACE-101) 후 나가기로 유도.
            throw new CustomException(ErrorCode.SPACE_OWNER_MUST_TRANSFER);
        }

        memberRepository.delete(member);
    }

    @Override
    @Transactional
    public void removeSpace(Long userId, Long spaceId) {
        Team team = loadActiveTeamForUpdate(spaceId);

        // 정책: 스페이스 삭제는 소유자만 가능. 비-Owner는 거부.
        if (!isOwner(team, userId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        // 정책 SP-2: 전파 soft delete. teams + is_deleted를 가진 하위 테이블(documents·meeting_rooms)을
        // 함께 갱신하고, is_deleted 컬럼이 없는 나머지 종속 테이블은 상위 teams.is_deleted 판정으로 방어한다.
        OffsetDateTime deletedAt = OffsetDateTime.now();
        team.softDelete(deletedAt);
        documentRepository.softDeleteByTeamId(spaceId, deletedAt);
        meetingRoomRepository.softDeleteByTeamId(spaceId, deletedAt);
    }

    @Override
    @Transactional
    public ResponseTransferOwnerDto transferOwner(Long requesterUserId, Long spaceId, RequestTransferOwnerDto request) {
        Team team = loadActiveTeamForUpdate(spaceId);

        // 요청자가 현재 Owner인지 검증. (SPACE-11 삭제와 동일하게 Owner 전용 작업)
        if (!isOwner(team, requesterUserId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        Long newOwnerUserId = request.newOwnerUserId();

        // 대상이 같은 스페이스의 멤버인지 검증.
        Member targetMember = memberRepository.findByTeamIdAndUserId(spaceId, newOwnerUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND));

        // 이미 소유자(자기 자신에게 위임하는 경우 포함)면 거부.
        if (targetMember.getAuthority() == MemberAuthority.OWNER) {
            throw new CustomException(ErrorCode.SPACE_ALREADY_OWNER);
        }
        // 정책: GUEST에게는 위임 불가(MEMBER에게만 허용).
        if (targetMember.getAuthority() == MemberAuthority.GUEST) {
            throw new CustomException(ErrorCode.SPACE_TRANSFER_TARGET_NOT_ELIGIBLE);
        }

        // 기존 Owner 강등 + 대상 승격 + team_owner_id 갱신을 단일 트랜잭션으로(중간 실패 시 Owner 공백 방지).
        Member currentOwnerMember = memberRepository.findByTeamIdAndUserId(spaceId, requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND));
        currentOwnerMember.changeAuthority(MemberAuthority.MEMBER);
        targetMember.changeAuthority(MemberAuthority.OWNER);
        team.changeOwner(newOwnerUserId);

        return new ResponseTransferOwnerDto(spaceId, requesterUserId, newOwnerUserId);
    }

    // SPACE-05 등 읽기 전용: 삭제되지 않은 스페이스 조회(잠금 없음, 없으면 404).
    private Team loadActiveTeam(Long spaceId) {
        return teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));
    }

    // SPACE-07/11/101 공통: 소유자 상태 변경 작업용 — 비관적 쓰기 잠금으로 조회해 동시 요청을 직렬화(없으면 404).
    private Team loadActiveTeamForUpdate(Long spaceId) {
        return teamRepository.findActiveByIdForUpdate(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));
    }

    // Owner 판별 공통화 — team_owner_id 기준.
    private boolean isOwner(Team team, Long userId) {
        return team.getOwnerId().equals(userId);
    }

    // members.nickname(NOT NULL) 용 표시 이름 — 유저 닉네임 우선, 없으면 이메일 로컬파트.
    private String resolveNickname(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        String email = user.getEmail();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
