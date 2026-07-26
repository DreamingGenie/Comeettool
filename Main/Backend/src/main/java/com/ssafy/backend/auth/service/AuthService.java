package com.ssafy.backend.auth.service;

import com.ssafy.backend.auth.dto.RequestLoginDto;
import com.ssafy.backend.auth.dto.RequestSignupDto;
import com.ssafy.backend.auth.dto.ResponseLoginDto;
import com.ssafy.backend.auth.dto.ResponseSignupDto;

public interface AuthService {

    // AUTH-01: email·password로 계정 생성. 나머지 프로필은 온보딩에서 채움.
    ResponseSignupDto signup(RequestSignupDto request);

    // AUTH-02: email·password 검증 후 Access/Refresh Token 발급.
    ResponseLoginDto login(RequestLoginDto request);
}