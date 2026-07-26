package com.ssafy.backend.user.repository;

import com.ssafy.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 회원가입 시 이메일 중복 검사(AUTH_EMAIL_DUPLICATED)에 사용.
    boolean existsByEmail(String email);

    // 로그인 시 이메일로 유저 조회(AUTH-02)에 사용.
    Optional<User> findByEmail(String email);
}