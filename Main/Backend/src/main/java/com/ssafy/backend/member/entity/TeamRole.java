package com.ssafy.backend.member.entity;

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
 * team_roles 테이블 매핑 엔티티 (스페이스별 커스텀 역할).
 * (team_id, role_name) 유니크 — 한 스페이스 안에서 역할명은 중복될 수 없다.
 * members.team_role_id가 이 테이블을 참조한다(FK, ON DELETE SET NULL).
 * 역할 삭제 API는 이번 작업 범위 아님.
 */
@Entity
@Table(name = "team_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_role_id")
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "color")
    private String color;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private TeamRole(Long teamId, String roleName, String color) {
        this.teamId = teamId;
        this.roleName = roleName;
        this.color = color;
    }

    // MEMBER-16: 부분 수정 — 인자가 null이면 해당 필드는 건드리지 않는다(요청에 안 보낸 필드로 간주).
    public void updateRole(String roleName, String color) {
        if (roleName != null) {
            this.roleName = roleName;
        }
        if (color != null) {
            this.color = color;
        }
    }
}