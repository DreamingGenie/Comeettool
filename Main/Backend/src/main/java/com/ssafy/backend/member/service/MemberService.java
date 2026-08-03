package com.ssafy.backend.member.service;

import com.ssafy.backend.member.dto.RequestAssignTeamRoleDto;
import com.ssafy.backend.member.dto.RequestChangeAuthorityDto;
import com.ssafy.backend.member.dto.RequestCreateTeamRoleDto;
import com.ssafy.backend.member.dto.RequestUpdateTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseAssignTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseChangeAuthorityDto;
import com.ssafy.backend.member.dto.ResponseTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseTeamRoleSummaryDto;

import java.util.List;

public interface MemberService {

    // MEMBER-04: 스페이스 소유자가 특정 멤버를 강퇴한다.
    void kickMember(Long requesterId, Long spaceId, Long memberId);

    // MEMBER-07: 스페이스 소유자가 멤버의 권한(MEMBER/GUEST)을 변경한다.
    ResponseChangeAuthorityDto changeMemberAuthority(
            Long requesterId, Long spaceId, Long memberId, RequestChangeAuthorityDto request);

    // MEMBER-06: 스페이스 소유자가 멤버에게 커스텀 역할을 배정하거나(teamRoleId) 해제(null)한다.
    ResponseAssignTeamRoleDto assignTeamRole(
            Long requesterId, Long spaceId, Long memberId, RequestAssignTeamRoleDto request);

    // MEMBER-14: 스페이스 소유자가 커스텀 팀 역할을 생성한다.
    ResponseTeamRoleDto createTeamRole(Long requesterId, Long spaceId, RequestCreateTeamRoleDto request);

    // MEMBER-15: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 팀 역할 목록을 조회한다.
    List<ResponseTeamRoleSummaryDto> getTeamRoles(Long requesterId, Long spaceId);

    // MEMBER-16: 스페이스 소유자가 커스텀 팀 역할을 부분 수정한다(roleName/color 각각 null이면 유지).
    ResponseTeamRoleDto updateTeamRole(
            Long requesterId, Long spaceId, Long teamRoleId, RequestUpdateTeamRoleDto request);
}