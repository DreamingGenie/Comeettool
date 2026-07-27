package com.ssafy.backend.auth.controller;

import com.ssafy.backend.auth.dto.RequestLoginDto;
import com.ssafy.backend.auth.dto.RequestSignupDto;
import com.ssafy.backend.auth.dto.RequestTokenRefreshDto;
import com.ssafy.backend.auth.dto.ResponseLoginDto;
import com.ssafy.backend.auth.dto.ResponseSignupDto;
import com.ssafy.backend.auth.dto.ResponseTokenRefreshDto;
import com.ssafy.backend.auth.service.AuthService;
import com.ssafy.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUTH 도메인 REST 컨트롤러 (inventory.md §1).
 * SecurityConfig에서 /signup, /login, /token/refresh는 permitAll이고, 그 외(/logout 포함)는 인증 필요.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<ResponseSignupDto>> signup(@Valid @RequestBody RequestSignupDto request) {
        ResponseSignupDto response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<ResponseLoginDto>> login(@Valid @RequestBody RequestLoginDto request) {
        ResponseLoginDto response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", response));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<ResponseTokenRefreshDto>> refreshAccessToken(
            @Valid @RequestBody RequestTokenRefreshDto request) {
        ResponseTokenRefreshDto response = authService.refreshAccessToken(request);
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal String userId) {
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.<Void>success("로그아웃 성공", null));
    }
}