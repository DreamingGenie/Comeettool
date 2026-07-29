package com.ssafy.backend.user.controller;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.user.dto.RequestChangePasswordDto;
import com.ssafy.backend.user.dto.RequestUpdateProfileDto;
import com.ssafy.backend.user.dto.ResponseChangePasswordDto;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;
import com.ssafy.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * USER 도메인 REST 컨트롤러 (inventory.md §1 /users/*). SecurityConfig 기본 규칙으로 전부 인증 필요.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ResponseMyProfileDto>> getMyProfile(@AuthenticationPrincipal String userId) {
        ResponseMyProfileDto response = userService.findMyProfile(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.success("프로필 조회 성공", response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<ResponseMyProfileDto>> modifyMyProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody RequestUpdateProfileDto request) {
        ResponseMyProfileDto response = userService.modifyMyProfile(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.success("프로필 수정 성공", response));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<ResponseChangePasswordDto>> changePassword(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody RequestChangePasswordDto request) {
        ResponseChangePasswordDto response = userService.changePassword(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호 변경 성공", response));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal String userId) {
        userService.withdraw(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.<Void>success("회원 탈퇴 성공", null));
    }
}