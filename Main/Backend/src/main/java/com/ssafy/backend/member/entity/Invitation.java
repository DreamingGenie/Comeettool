package com.ssafy.backend.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * invitations 테이블 매핑 엔티티 (스페이스 초대).
 * (team_id, target_user_id) 유니크 — 스페이스당 대상 유저에게 동시에 대기 중인 초대는 하나뿐이다(DB 레벨 최종 방어선).
 * team·inviter·target 은 BIGINT FK 이지만 members/teams와 동일한 컨벤션으로 식별자(Long)로만 다룬다.
 * 상태 필드 없음 — expires_at을 지났는지 여부로 "유효/만료"를 판단한다(물리 삭제는 이번 범위 아님).
 */
@Entity
@Table(name = "invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation {

    @Id
    @Column(name = "invitation_id")
    private UUID invitationId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "inviter_id", nullable = false)
    private Long inviterId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Builder
    private Invitation(UUID invitationId, Long teamId, Long inviterId, Long targetUserId, OffsetDateTime expiresAt) {
        this.invitationId = invitationId;
        this.teamId = teamId;
        this.inviterId = inviterId;
        this.targetUserId = targetUserId;
        this.expiresAt = expiresAt;
    }

    // MEMBER-02: 초대 생성. expires_at = 생성 시점 + ttl(정책상 1일).
    public static Invitation create(Long teamId, Long inviterId, Long targetUserId, Duration ttl) {
        return Invitation.builder()
                .invitationId(UUID.randomUUID())
                .teamId(teamId)
                .inviterId(inviterId)
                .targetUserId(targetUserId)
                .expiresAt(OffsetDateTime.now().plus(ttl))
                .build();
    }
}