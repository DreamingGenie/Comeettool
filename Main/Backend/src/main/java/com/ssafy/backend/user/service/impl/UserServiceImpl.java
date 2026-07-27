package com.ssafy.backend.user.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.mapper.UserMapper;
import com.ssafy.backend.user.repository.UserRepository;
import com.ssafy.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-05 내 프로필 조회 로직. 이후 AUTH-06~08, 10도 여기에 추가될 예정.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public ResponseMyProfileDto getMyProfile(Long userId) {
        // JWT principal이 가리키는 userId는 로그인 시 실제 DB에서 조회해 발급된 값이라 이론상 항상 존재하지만,
        // 탈퇴 등으로 이후 유효하지 않게 될 가능성을 방어적으로 처리한다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toMyProfileResponse(user);
    }
}