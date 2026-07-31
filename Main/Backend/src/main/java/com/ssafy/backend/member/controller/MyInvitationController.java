package com.ssafy.backend.member.controller;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.member.dto.ResponseMyInvitationDto;
import com.ssafy.backend.member.service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내가 받은 초대 리소스 REST 컨트롤러 (MEMBER 도메인). INVITATION 리소스가 스페이스 하위(/spaces/{spaceId}/invitations)인 것과 달리,
 * 이 리소스는 요청자 본인 기준 조회·처리라 스페이스에 종속되지 않는 최상위 경로(/invitations)를 쓴다.
 * 인증 필요 — principal = userId(String).
 */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class MyInvitationController {

    private final InvitationService invitationService;

    // MEMBER-03: 내가 받은(만료되지 않은) 초대 목록을 최신순으로 조회한다.
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ResponseMyInvitationDto>>> findMyInvitations(
            @AuthenticationPrincipal String userId) {
        List<ResponseMyInvitationDto> response = invitationService.findMyInvitations(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.success("받은 초대 목록 조회 성공", response));
    }

    // MEMBER-03: 받은 초대를 수락해 스페이스 멤버로 등록한다.
    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @AuthenticationPrincipal String userId,
            @PathVariable String invitationId) {
        invitationService.acceptInvitation(Long.parseLong(userId), invitationId);
        return ResponseEntity.ok(ApiResponse.success("초대 수락 성공", null));
    }
}