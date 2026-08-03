package com.ssafy.backend.member.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.dto.RequestAssignTeamRoleDto;
import com.ssafy.backend.member.dto.RequestChangeAuthorityDto;
import com.ssafy.backend.member.dto.ResponseAssignTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseChangeAuthorityDto;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.entity.TeamRole;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.member.repository.TeamRoleRepository;
import com.ssafy.backend.member.service.MemberService;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final TeamRoleRepository teamRoleRepository;

    @Override
    @Transactional
    public void kickMember(Long requesterId, Long spaceId, Long memberId) {
        Team team = teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        if (!team.getOwnerId().equals(requesterId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        Member member = memberRepository.findById(memberId)
                .filter(m -> m.getTeamId().equals(spaceId))
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND));

        if (member.getUserId().equals(requesterId)) {
            throw new CustomException(ErrorCode.CANNOT_KICK_SELF);
        }

        memberRepository.delete(member);
    }

    @Override
    @Transactional
    public ResponseChangeAuthorityDto changeMemberAuthority(
            Long requesterId, Long spaceId, Long memberId, RequestChangeAuthorityDto request
    ) {
        // 권한 변경도 소유자 상태 변경(위임·삭제)과 같은 급의 쓰기 작업이라 잠금 조회로 동시 요청을 직렬화한다.
        Team team = teamRepository.findActiveByIdForUpdate(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        if (!team.getOwnerId().equals(requesterId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        MemberAuthority requestedAuthority = parseRequestedAuthority(request.authority());

        Member member = memberRepository.findById(memberId)
                .filter(m -> m.getTeamId().equals(spaceId))
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND));

        if (member.getAuthority() == MemberAuthority.OWNER) {
            throw new CustomException(ErrorCode.CANNOT_CHANGE_OWNER_AUTHORITY);
        }

        // 멱등 처리: 이미 같은 권한이면 쓰기 없이 현재 상태 그대로 응답한다.
        if (member.getAuthority() != requestedAuthority) {
            member.changeAuthority(requestedAuthority);
            memberRepository.save(member);
        }

        return new ResponseChangeAuthorityDto(member.getId(), member.getAuthority().name());
    }

    // 요청 문자열을 MemberAuthority로 변환한다. OWNER는 위임 API(SPACE-101) 안내와 함께 명시적으로 거부하고,
    // 그 외 유효하지 않은 값(예: 오타)은 VALIDATION_FAILED 기본 메시지로 응답한다.
    private MemberAuthority parseRequestedAuthority(String authority) {
        if (MemberAuthority.OWNER.name().equals(authority)) {
            throw new CustomException(ErrorCode.CANNOT_SET_OWNER_AUTHORITY);
        }
        try {
            return MemberAuthority.valueOf(authority);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Override
    @Transactional
    public ResponseAssignTeamRoleDto assignTeamRole(
            Long requesterId, Long spaceId, Long memberId, RequestAssignTeamRoleDto request
    ) {
        // 역할 배정도 권한 변경과 같은 급의 멤버 상태 쓰기 작업이라 잠금 조회로 동시 요청을 직렬화한다.
        Team team = teamRepository.findActiveByIdForUpdate(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        if (!team.getOwnerId().equals(requesterId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        Member member = memberRepository.findById(memberId)
                .filter(m -> m.getTeamId().equals(spaceId))
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND));

        Long requestedTeamRoleId = request.teamRoleId();
        TeamRole teamRole = null;
        if (requestedTeamRoleId != null) {
            teamRole = teamRoleRepository.findByIdAndTeamId(requestedTeamRoleId, spaceId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TEAM_ROLE_NOT_FOUND));
        }

        // 멱등 처리: 이미 같은 역할(둘 다 미배정인 경우 포함)이면 쓰기 없이 현재 상태 그대로 응답한다.
        if (!Objects.equals(member.getTeamRoleId(), requestedTeamRoleId)) {
            member.assignTeamRole(requestedTeamRoleId);
            memberRepository.save(member);
        }

        String roleName = teamRole != null ? teamRole.getRoleName() : null;
        return new ResponseAssignTeamRoleDto(member.getId(), member.getTeamRoleId(), roleName);
    }
}