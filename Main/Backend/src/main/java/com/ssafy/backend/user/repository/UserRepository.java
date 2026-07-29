package com.ssafy.backend.user.repository;

import com.ssafy.backend.user.entity.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 회원가입 시 이메일 중복 검사(AUTH_EMAIL_DUPLICATED)에 사용. 탈퇴(is_deleted=true)한 이메일은 재사용 가능하므로 검사 대상에서 제외.
    boolean existsByEmailAndIsDeletedFalse(String email);

    // 로그인 시 이메일로 유저 조회(AUTH-02)에 사용. 탈퇴 계정은 존재하지 않는 것으로 취급(로그인 불가).
    Optional<User> findByEmailAndIsDeletedFalse(String email);

    // AUTH-10: 닉네임 또는 이메일에 검색어가 포함된 활성 계정 검색. 대소문자 무시(LOWER), 탈퇴 계정·본인 제외, userId 오름차순.
    // excludeUserId를 LIMIT 적용 전(쿼리 단계)에서 걸러야 "본인 제외 후 7건"이 정확히 보장된다.
    @Query("SELECT u FROM User u WHERE u.isDeleted = false "
            + "AND u.id <> :excludeUserId "
            + "AND (LOWER(u.nickname) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "ORDER BY u.id ASC")
    List<User> searchByNicknameOrEmail(@Param("query") String query, @Param("excludeUserId") Long excludeUserId, Limit limit);
}