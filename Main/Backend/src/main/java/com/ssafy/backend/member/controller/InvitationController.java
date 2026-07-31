package com.ssafy.backend.member.controller;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;
import com.ssafy.backend.member.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * INVITATION 리소스 REST 컨트롤러 (스페이스 하위, MEMBER 도메인).
 * 인증 필요 — principal = userId(String). 요청/응답만 담당하고 로직은 InvitationService에 위임한다.
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    // MEMBER-02: 스페이스 소유자가 사용자를 초대한다.
    @PostMapping
    public ResponseEntity<ApiResponse<ResponseInviteMemberDto>> inviteMember(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @Valid @RequestBody RequestInviteMemberDto request) {
        ResponseInviteMemberDto response =
                invitationService.inviteMember(Long.parseLong(userId), spaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("멤버 초대 성공", response));
    }
}