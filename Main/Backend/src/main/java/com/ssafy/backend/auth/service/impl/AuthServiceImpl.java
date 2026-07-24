package com.ssafy.backend.auth.service.impl;

import com.ssafy.backend.auth.dto.RequestSignupDto;
import com.ssafy.backend.auth.dto.ResponseSignupDto;
import com.ssafy.backend.auth.mapper.UserMapper;
import com.ssafy.backend.auth.service.AuthService;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * AUTH-01 회원가입 로직. 비밀번호는 BCrypt(SecurityConfig의 PasswordEncoder 빈)로 해싱해 저장한다.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public ResponseSignupDto signup(RequestSignupDto request) {
        // 이메일은 대소문자 구분 없이 동일 계정으로 취급 — 저장·중복검사 전부 소문자로 정규화한 값을 쓴다.
        String normalizedEmail = normalizeEmail(request.email());
        validateEmailNotDuplicated(normalizedEmail);

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .build();
        User saved = userRepository.save(user);

        return userMapper.toSignupResponse(saved);
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private void validateEmailNotDuplicated(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }
    }
}