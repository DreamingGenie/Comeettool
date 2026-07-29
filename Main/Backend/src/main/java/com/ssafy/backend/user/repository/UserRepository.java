package com.ssafy.backend.user.repository;

import com.ssafy.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 회원가입 시 이메일 중복 검사(AUTH_EMAIL_DUPLICATED)에 사용. 탈퇴(is_deleted=true)한 이메일은 재사용 가능하므로 검사 대상에서 제외.
    boolean existsByEmailAndIsDeletedFalse(String email);

    // 로그인 시 이메일로 유저 조회(AUTH-02)에 사용. 탈퇴 계정은 존재하지 않는 것으로 취급(로그인 불가).
    Optional<User> findByEmailAndIsDeletedFalse(String email);
}