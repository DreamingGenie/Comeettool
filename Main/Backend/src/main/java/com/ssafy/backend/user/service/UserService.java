package com.ssafy.backend.user.service;

import com.ssafy.backend.user.dto.RequestChangePasswordDto;
import com.ssafy.backend.user.dto.RequestUpdateProfileDto;
import com.ssafy.backend.user.dto.ResponseChangePasswordDto;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;

public interface UserService {

    // AUTH-05: userId로 내 프로필 조회.
    ResponseMyProfileDto findMyProfile(Long userId);

    // AUTH-06: 요청에 담긴 필드만 부분 수정(PATCH) 후 최신 프로필 반환.
    ResponseMyProfileDto modifyMyProfile(Long userId, RequestUpdateProfileDto request);

    // AUTH-07: 현재 비밀번호 검증 후 새 비밀번호로 변경, 토큰 재발급.
    ResponseChangePasswordDto changePassword(Long userId, RequestChangePasswordDto request);

    // AUTH-08: soft delete 처리 후 Redis의 refresh token 삭제.
    void withdraw(Long userId);
}
