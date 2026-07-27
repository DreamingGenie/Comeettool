package com.ssafy.backend.space.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.entity.Member;
import com.ssafy.backend.space.entity.MemberRole;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.mapper.SpaceMapper;
import com.ssafy.backend.space.service.SpaceService;
import com.ssafy.backend.space.repository.MemberRepository;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SpaceMapper spaceMapper;

    @Override
    @Transactional
    public ResponseCreateSpaceDto createSpace(Long userId, RequestCreateSpaceDto request) {
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
    public List<ResponseSpaceListDto> findMySpaces(Long userId) {
        List<Team> teams = memberRepository.findActiveTeamsByUserId(userId);
        if (teams.isEmpty()) {
            return List.of();
        }

        // 내 역할(스페이스별) 조회 — teamId → role.
        Map<Long, MemberRole> myRoleByTeam = new HashMap<>();
        for (Member membership : memberRepository.findByUserId(userId)) {
            myRoleByTeam.put(membership.getTeamId(), membership.getRole());
        }

        // 참여자 수 일괄 집계 — teamId → count.
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, Long> memberCountByTeam = new HashMap<>();
        for (Object[] row : memberRepository.countMembersByTeamIds(teamIds)) {
            memberCountByTeam.put((Long) row[0], (Long) row[1]);
        }

        return teams.stream()
                .map(team -> {
                    MemberRole role = myRoleByTeam.get(team.getId());
                    long count = memberCountByTeam.getOrDefault(team.getId(), 0L);
                    return spaceMapper.toListItem(team, role != null ? role.name() : null, count);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseSpaceDetailDto findSpaceDetail(Long userId, Long spaceId) {
        Team team = teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        // 요청자가 해당 스페이스 멤버인지 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(spaceId, userId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        List<Member> members = memberRepository.findByTeamId(spaceId);
        return spaceMapper.toDetailResponse(team, members);
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
