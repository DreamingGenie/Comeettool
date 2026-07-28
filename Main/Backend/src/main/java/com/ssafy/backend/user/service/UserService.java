package com.ssafy.backend.user.service;

import com.ssafy.backend.user.dto.RequestUpdateProfileDto;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;

public interface UserService {

    // AUTH-05: userId로 내 프로필 조회.
    ResponseMyProfileDto findMyProfile(Long userId);

    // AUTH-06: 요청에 담긴 필드만 부분 수정(PATCH) 후 최신 프로필 반환.
    ResponseMyProfileDto modifyMyProfile(Long userId, RequestUpdateProfileDto request);
}
