package com.ssafy.backend.user.service.impl;

import com.ssafy.backend.auth.service.RefreshTokenService;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.jwt.JwtProvider;
import com.ssafy.backend.user.dto.RequestChangePasswordDto;
import com.ssafy.backend.user.dto.RequestUpdateProfileDto;
import com.ssafy.backend.user.dto.ResponseChangePasswordDto;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.mapper.UserProfileMapper;
import com.ssafy.backend.user.repository.UserRepository;
import com.ssafy.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-05 내 프로필 조회, AUTH-06 내 프로필 수정, AUTH-07 비밀번호 변경 로직. 이후 AUTH-08, 10, 11도 여기에 추가될 예정.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public ResponseMyProfileDto findMyProfile(Long userId) {
        // JWT principal이 가리키는 userId는 로그인 시 실제 DB에서 조회해 발급된 값이라 이론상 항상 존재하지만,
        // 탈퇴 등으로 이후 유효하지 않게 될 가능성을 방어적으로 처리한다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return userProfileMapper.toMyProfileResponse(user);
    }

    @Override
    @Transactional
    public ResponseMyProfileDto modifyMyProfile(Long userId, RequestUpdateProfileDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // user는 영속 상태 엔티티라 필드 변경만으로 트랜잭션 커밋 시 UPDATE가 나간다(더티 체킹) — save() 호출 불필요.
        user.updateProfile(
                request.nickname(), request.phone(), request.sex(), request.age(),
                request.jobFamily(), request.jobRole(), request.userDescription(), request.userColor()
        );

        return userProfileMapper.toMyProfileResponse(user);
    }

    @Override
    @Transactional
    public ResponseChangePasswordDto changePassword(Long userId, RequestChangePasswordDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 현재 비밀번호 BCrypt 검증
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 새 비밀번호로 갱신 (더티 체킹 — save() 불필요)
        user.updatePassword(passwordEncoder.encode(request.newPassword()));

        // 새 토큰 발급 및 Redis Refresh Token 교체
        String userIdStr = String.valueOf(userId);
        String newAccessToken = jwtProvider.createAccessToken(userIdStr);
        String newRefreshToken = jwtProvider.createRefreshToken(userIdStr);
        refreshTokenService.save(userIdStr, newRefreshToken, jwtProvider.getRefreshExpirationSeconds());

        return new ResponseChangePasswordDto("Bearer", newAccessToken, newRefreshToken);
    }
}