package com.ssafy.backend.member.controller;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}