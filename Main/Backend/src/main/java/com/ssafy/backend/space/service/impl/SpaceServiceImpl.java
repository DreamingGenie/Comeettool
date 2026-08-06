package com.ssafy.backend.space.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.storage.profile.ProfileImageOwner;
import com.ssafy.backend.global.storage.profile.ProfileImageStorageService;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.RequestTransferOwnerDto;
import com.ssafy.backend.space.dto.RequestUpdateSpaceDto;
import com.ssafy.backend.space.dto.RequestUpdateSpaceOrderDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.dto.ResponseSpaceProfileImageDto;
import com.ssafy.backend.space.dto.ResponseTransferOwnerDto;
import com.ssafy.backend.space.dto.ResponseUpdateSpaceDto;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.document.repository.DocumentRepository;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.member.repository.InvitationRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * 팀 스페이스(teams/members) 조회·생성 로직 (SPACE-01/02/05). Controller는 요청/응답만, 비즈니스 로직은 이 계층에 둔다(코드 컨벤션).
 */
@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final InvitationRepository invitationRepository;
    private final SpaceMapper spaceMapper;
    private final ProfileImageStorageService profileImageStorageService;

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
    public List<ResponseSpaceListDto> findSpaceList(Long userId, String search) {
        // SPACE-03: 공백뿐이거나 빈 검색어는 필터 없음(null)으로 정규화 — 전체 목록 반환.
        String keyword = (search == null || search.isBlank()) ? null : search.trim();

        // [Team, 내 권한] 을 한 번의 조인 쿼리로 가져온다(검색어가 있으면 이름 부분 일치 필터).
        List<Object[]> rows = memberRepository.findActiveTeamsWithMyAuthority(userId, keyword);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 참여자 수 일괄 집계 — teamId → count (N+1 방지).
        List<Long> teamIds = rows.stream().map(row -> ((Team) row[0]).getId()).toList();
        Map<Long, Long> memberCountByTeam = new HashMap<>();
        for (Object[] row : memberRepository.countMembersByTeamIds(teamIds)) {
            memberCountByTeam.put((Long) row[0], (Long) row[1]);
        }

        // 쿼리 기본 정렬은 최신순(created_at DESC).
        List<ResponseSpaceListDto> items = rows.stream()
                .map(row -> {
                    Team team = (Team) row[0];
                    MemberAuthority authority = (MemberAuthority) row[1];
                    long count = memberCountByTeam.getOrDefault(team.getId(), 0L);
                    return spaceMapper.toListItem(team, authority.name(), count);
                })
                .toList();

        // SPACE-04: 사용자 커스텀 순서를 적용한다(저장된 순서 없으면 최신순 그대로).
        String orderCsv = userRepository.findById(userId)
                .map(User::getSpaceOrder)
                .orElse(null);
        return applyCustomOrder(items, orderCsv);
    }

    @Override
    @Transactional
    public List<ResponseSpaceListDto> modifySpaceOrder(Long userId, RequestUpdateSpaceOrderDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // spaceId 목록을 CSV로 저장(빈 목록이면 엔티티에서 null로 정규화 → 최신순 복귀).
        user.updateSpaceOrder(toCsv(request.spaceOrder()));

        // 저장된 순서로 병합·정렬된 목록을 그대로 반환(FE 재조회 불필요).
        return findSpaceList(userId, null);
    }

    // SPACE-04 병합 규칙: (1) 저장 순서대로 (2) 저장에 없는 신규 스페이스는 최신순으로 맨 앞 (3) 없어진 id는 버림.
    private List<ResponseSpaceListDto> applyCustomOrder(List<ResponseSpaceListDto> items, String orderCsv) {
        if (orderCsv == null || orderCsv.isBlank()) {
            return items;
        }
        List<Long> stored = parseOrder(orderCsv);
        Map<Long, ResponseSpaceListDto> byId = new LinkedHashMap<>();
        for (ResponseSpaceListDto item : items) {
            byId.put(item.spaceId(), item);
        }
        Set<Long> storedSet = new HashSet<>(stored);

        List<ResponseSpaceListDto> result = new ArrayList<>(items.size());
        // (2) 저장 순서에 없는 신규 스페이스 → 최신순(현재 items 순서) 유지하며 맨 앞에.
        for (ResponseSpaceListDto item : items) {
            if (!storedSet.contains(item.spaceId())) {
                result.add(item);
            }
        }
        // (1) 저장된 순서대로, (3) 실제 존재하는 것만(중복 방지).
        Set<Long> added = new HashSet<>();
        for (Long id : stored) {
            ResponseSpaceListDto item = byId.get(id);
            if (item != null && added.add(id)) {
                result.add(item);
            }
        }
        return result;
    }

    // CSV("5,2,9")를 Long 목록으로 파싱(공백·비정상 토큰은 무시).
    private List<Long> parseOrder(String orderCsv) {
        List<Long> ids = new ArrayList<>();
        for (String part : orderCsv.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(token));
            } catch (NumberFormatException ignored) {
                // 손상된 토큰은 건너뛴다(정렬은 부가 기능이라 예외로 실패시키지 않는다).
            }
        }
        return ids;
    }

    // spaceId 목록 → CSV. 빈 목록이면 null(커스텀 순서 해제).
    private String toCsv(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return String.join(",", ids.stream().map(String::valueOf).toList());
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
    public ResponseUpdateSpaceDto modifySpace(Long userId, Long spaceId, RequestUpdateSpaceDto request) {
        // 정보 수정도 teams 행을 변경하므로 삭제·위임과 동일하게 잠금 조회(동시 삭제/수정 경합 방지, 없으면 404).
        Team team = loadActiveTeamForUpdate(spaceId);

        // 정책: 스페이스 정보 수정은 소유자만 가능. 비-Owner는 거부(403).
        if (!isOwner(team, userId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        // NOT NULL 컬럼(name·color)은 "전달됐다면" 공백일 수 없다(빈 값으로 필수 필드를 지울 수 없음).
        if (request.teamName() != null && request.teamName().isBlank()) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        if (request.teamColor() != null && request.teamColor().isBlank()) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }

        team.updateInfo(
                request.teamName(),
                request.teamDescription(),
                request.teamColor(),
                request.teamProfileImage()
        );
        return spaceMapper.toUpdateResponse(team);
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
        // invitations는 is_deleted 컬럼이 없는 임시성 데이터라 soft delete 대신 hard delete로 정리한다.
        invitationRepository.deleteByTeamId(spaceId);
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

    @Override
    @Transactional
    public ResponseSpaceProfileImageDto changeProfileImage(
            Long userId,
            Long spaceId,
            MultipartFile file
    ) {
        Team team = loadActiveTeamForUpdate(spaceId);
        if (!isOwner(team, userId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        String previousUrl = team.getProfileImageUrl();
        String newUrl = profileImageStorageService.upload(
                file,
                ProfileImageOwner.team(spaceId)
        );
        team.updateProfileImage(newUrl);

        if (previousUrl != null && !previousUrl.equals(newUrl)) {
            profileImageStorageService.delete(previousUrl);
        }

        return new ResponseSpaceProfileImageDto(newUrl);
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
