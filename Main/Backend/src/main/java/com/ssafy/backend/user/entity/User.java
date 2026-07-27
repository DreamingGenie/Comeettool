package com.ssafy.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * users 테이블 매핑 엔티티.
 * 회원가입(AUTH-01)은 email·password만 채우고 나머지 프로필 항목은 null로 둔다 — 온보딩 단계에서 채워진다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    // DB가 IDENTITY로 채번(database-schema.md 참조) — 엔티티는 값을 지정하지 않는다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "password_hash", nullable = false)
    private String password;

    private String nickname;

    @Column(name = "user_profile_image")
    private String profileImageUrl;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "job_family")
    private String jobFamily;

    @Column(name = "job_role")
    private String jobRole;

    @Column(name = "user_description")
    private String description;

    private String sex;

    private Integer age;

    @Column(name = "user_color", insertable = false)
    private String displayColor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    // 가입 시점엔 email·password만 필요 — 나머지 필드는 온보딩(AUTH-06 등)에서 채운다.
    @Builder
    private User(String email, String password) {
        this.email = email;
        this.password = password;
        this.isDeleted = false;
    }

    // 별도 컬럼 없이 sex·age 존재 여부로 판단 (온보딩 3단계에서 두 값을 함께 입력받는 화면 기준).
    public boolean isOnboarded() {
        return sex != null && age != null;
    }
}