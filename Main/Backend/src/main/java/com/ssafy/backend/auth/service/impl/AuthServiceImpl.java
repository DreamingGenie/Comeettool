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
        validateEmailNotDuplicated(request.email());

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();
        User saved = userRepository.save(user);

        return userMapper.toSignupResponse(saved);
    }

    private void validateEmailNotDuplicated(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }
    }
}