package com.ssafy.backend.member.controller;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.member.dto.RequestAssignTeamRoleDto;
import com.ssafy.backend.member.dto.RequestChangeAuthorityDto;
import com.ssafy.backend.member.dto.RequestCreateTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseAssignTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseChangeAuthorityDto;
import com.ssafy.backend.member.dto.ResponseTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseTeamRoleSummaryDto;
import com.ssafy.backend.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // MEMBER-05: 스페이스 소유자가 특정 멤버를 강퇴한다.
    @DeleteMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @PathVariable Long memberId) {
        memberService.kickMember(Long.parseLong(userId), spaceId, memberId);
        return ResponseEntity.ok(ApiResponse.success("멤버 강퇴 성공", null));
    }

    // MEMBER-07: 스페이스 소유자가 멤버의 권한(MEMBER/GUEST)을 변경한다.
    @PatchMapping("/{memberId}/authority")
    public ResponseEntity<ApiResponse<ResponseChangeAuthorityDto>> changeMemberAuthority(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @PathVariable Long memberId,
            @Valid @RequestBody RequestChangeAuthorityDto request) {
        ResponseChangeAuthorityDto response =
                memberService.changeMemberAuthority(Long.parseLong(userId), spaceId, memberId, request);
        return ResponseEntity.ok(ApiResponse.success("멤버 권한이 변경되었습니다.", response));
    }

    // MEMBER-06: 스페이스 소유자가 멤버에게 커스텀 역할을 배정하거나(teamRoleId) 해제(null)한다.
    @PatchMapping("/{memberId}/team-role")
    public ResponseEntity<ApiResponse<ResponseAssignTeamRoleDto>> assignTeamRole(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @PathVariable Long memberId,
            @RequestBody RequestAssignTeamRoleDto request) {
        ResponseAssignTeamRoleDto response =
                memberService.assignTeamRole(Long.parseLong(userId), spaceId, memberId, request);
        return ResponseEntity.ok(ApiResponse.success("멤버 역할이 배정되었습니다.", response));
    }

    // MEMBER-14: 스페이스 소유자가 커스텀 팀 역할을 생성한다.
    @PostMapping("/team-roles")
    public ResponseEntity<ApiResponse<ResponseTeamRoleDto>> createTeamRole(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @Valid @RequestBody RequestCreateTeamRoleDto request) {
        ResponseTeamRoleDto response = memberService.createTeamRole(Long.parseLong(userId), spaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("역할이 생성되었습니다.", response));
    }

    // MEMBER-15: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 팀 역할 목록을 조회한다.
    @GetMapping("/team-roles")
    public ResponseEntity<ApiResponse<List<ResponseTeamRoleSummaryDto>>> getTeamRoles(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId) {
        List<ResponseTeamRoleSummaryDto> response = memberService.getTeamRoles(Long.parseLong(userId), spaceId);
        return ResponseEntity.ok(ApiResponse.success("역할 목록 조회 성공", response));
    }
}