package com.ssafy.backend.user.repository;

import com.ssafy.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // 회원가입 시 이메일 중복 검사(AUTH_EMAIL_DUPLICATED)에 사용.
    boolean existsByEmail(String email);
}