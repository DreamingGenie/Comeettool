package com.ssafy.backend.user.service;

import com.ssafy.backend.user.dto.ResponseMyProfileDto;

public interface UserService {

    // AUTH-05: userId로 내 프로필 조회.
    ResponseMyProfileDto findMyProfile(Long userId);
}
