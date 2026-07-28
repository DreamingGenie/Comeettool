package com.ssafy.backend.space.entity;

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
 * teams 테이블 매핑 엔티티 (스페이스).
 * 스페이스 생성(SPACE-01) 시 이름·설명·설정과 소유자(team_owner_id)를 채운다.
 */
@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    // DB가 IDENTITY로 채번(database-schema.md 참조) — 엔티티는 값을 지정하지 않는다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    private Long id;

    @Column(name = "team_name", nullable = false)
    private String name;

    @Column(name = "team_description")
    private String description;

    @Column(name = "team_owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "team_profile_image")
    private String profileImageUrl;

    // NOT NULL — 사용자가 설정에서 지정할 수 있어 insert에 포함한다. 미지정 시 생성자에서 기본색으로 채운다.
    @Column(name = "team_color", nullable = false)
    private String color;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "team_invite_link")
    private String inviteLink;

    private static final String DEFAULT_COLOR = "#000000";

    @Builder
    private Team(String name, String description, Long ownerId, String profileImageUrl, String color) {
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.profileImageUrl = profileImageUrl;
        // 설정 미지정 시 스키마 기본색(#000000)을 적용한다.
        this.color = (color != null && !color.isBlank()) ? color : DEFAULT_COLOR;
        this.isDeleted = false;
    }
}
