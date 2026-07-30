package com.ssafy.backend.auth.service.impl;

import com.ssafy.backend.auth.dto.RequestLoginDto;
import com.ssafy.backend.auth.dto.RequestSignupDto;
import com.ssafy.backend.auth.dto.RequestTokenRefreshDto;
import com.ssafy.backend.auth.dto.ResponseLoginDto;
import com.ssafy.backend.auth.dto.ResponseSignupDto;
import com.ssafy.backend.auth.dto.ResponseTokenRefreshDto;
import com.ssafy.backend.auth.mapper.UserMapper;
import com.ssafy.backend.auth.service.AuthService;
import com.ssafy.backend.auth.service.RefreshTokenService;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.jwt.JwtProvider;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * AUTH-01 회원가입, AUTH-02 로그인, AUTH-03 Access Token 재발급, AUTH-04 로그아웃 로직. 비밀번호는 BCrypt(SecurityConfig의 PasswordEncoder
 * 빈)로 해싱해 저장·비교한다.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    // users.nickname 컬럼 제약(CK_USERS_NICKNAME_LENGTH)과 동일한 최대 길이.
    private static final int NICKNAME_MAX_LENGTH = 20;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public ResponseSignupDto signup(RequestSignupDto request) {
        // 이메일은 대소문자 구분 없이 동일 계정으로 취급 — 저장·중복검사 전부 소문자로 정규화한 값을 쓴다.
        String normalizedEmail = normalizeEmail(request.email());
        validateEmailNotDuplicated(normalizedEmail);

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .nickname(extractDefaultNickname(normalizedEmail))
                .build();
        User saved = userRepository.save(user);

        return userMapper.toSignupResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseLoginDto login(RequestLoginDto request) {
        String normalizedEmail = normalizeEmail(request.email());

        // 이메일 없음·탈퇴 계정·비밀번호 불일치 모두 AUTH_LOGIN_FAILED로 통일 (보안상 구분하지 않음)
        User user = userRepository.findByEmailAndIsDeletedFalse(normalizedEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        String userId = String.valueOf(user.getId());
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);

        refreshTokenService.save(userId, refreshToken, jwtProvider.getRefreshExpirationSeconds());

        // JWT sub·Redis 키는 String(userId)을 그대로 쓰고, 응답 DTO만 AUTH-01/05와 통일해 Long으로 내려준다.
        return new ResponseLoginDto("Bearer", accessToken, refreshToken, user.getId(), user.isOnboarded());
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseTokenRefreshDto refreshAccessToken(RequestTokenRefreshDto request) {
        Claims claims = parseRefreshTokenClaimsOrThrow(request.refreshToken());
        String userId = claims.getSubject();

        // Redis에 저장된 refresh:{userId} 값과 다르면(만료·로그아웃·미존재) 재발급 거부. Rotation 없음 — Redis write 안 함.
        if (!refreshTokenService.isValid(userId, request.refreshToken())) {
            throw new CustomException(ErrorCode.AUTH_REFRESH_FAILED);
        }

        String accessToken = jwtProvider.createAccessToken(userId);
        return new ResponseTokenRefreshDto("Bearer", accessToken);
    }

    @Override
    public void logout(String userId) {
        // RefreshTokenService.delete는 키가 없어도 예외 없이 지나가므로 별도 존재 확인이 필요 없다(멱등).
        refreshTokenService.delete(userId);
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    // nickname 컬럼 제약(CK_USERS_NICKNAME_LENGTH, 최대 20자)에 맞춰 이메일 로컬파트를 그대로(별도 필터링 없이) 잘라 기본값으로 쓴다.
    private String extractDefaultNickname(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        return localPart.length() > NICKNAME_MAX_LENGTH
                ? localPart.substring(0, NICKNAME_MAX_LENGTH)
                : localPart;
    }

    private void validateEmailNotDuplicated(String email) {
        // 탈퇴한 계정과 같은 이메일은 중복으로 취급하지 않는다(탈퇴 이메일 재가입 허용 정책).
        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new CustomException(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }
    }

    // 서명 실패·만료·형식 오류를 원인 구분 없이 전부 AUTH_REFRESH_FAILED로 통일(보안상 이유 비노출).
    private Claims parseRefreshTokenClaimsOrThrow(String refreshToken) {
        Claims claims;
        try {
            claims = jwtProvider.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.AUTH_REFRESH_FAILED);
        }

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new CustomException(ErrorCode.AUTH_REFRESH_FAILED);
        }
        return claims;
    }
}