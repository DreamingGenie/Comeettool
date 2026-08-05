package com.ssafy.backend.auth.service;

import com.ssafy.backend.auth.dto.RequestLoginDto;
import com.ssafy.backend.auth.dto.RequestSignupDto;
import com.ssafy.backend.auth.dto.RequestTokenRefreshDto;
import com.ssafy.backend.auth.dto.ResponseLoginDto;
import com.ssafy.backend.auth.dto.ResponseSignupDto;
import com.ssafy.backend.auth.dto.ResponseTokenRefreshDto;

public interface AuthService {

    // AUTH-01: email·password로 계정 생성. 나머지 프로필은 온보딩에서 채움.
    ResponseSignupDto signup(RequestSignupDto request);

    // AUTH-02: email·password 검증 후 Access/Refresh Token 발급.
    ResponseLoginDto login(RequestLoginDto request);

    // AUTH-03: refreshToken 검증 후 새 accessToken만 재발급 (rotation 없음, refreshToken 그대로 유지).
    ResponseTokenRefreshDto refreshAccessToken(RequestTokenRefreshDto request);

    // AUTH-04: Redis의 refresh:{userId}를 삭제해 세션을 종료. 이미 없어도 멱등하게 200 처리.
    void logout(String userId);
}