package com.ssafy.backend.space.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * members 테이블 매핑 엔티티 (스페이스 참여자).
 * (team_id, user_id) 유니크 — 한 사용자는 한 스페이스에 하나의 멤버로만 존재한다.
 * team·user 는 BIGINT FK 이지만 본 작업 범위에서는 식별자(Long)로만 다룬다.
 */
@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "authority", nullable = false)
    private MemberRole authority;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Builder
    private Member(Long userId, Long teamId, MemberRole role, MemberRole authority, String nickname) {
        this.userId = userId;
        this.teamId = teamId;
        this.role = role;
        this.authority = authority;
        this.nickname = nickname;
    }

    // SPACE-01: 생성자를 Owner 멤버로 등록. role·authority 모두 OWNER.
    public static Member owner(Long userId, Long teamId, String nickname) {
        return Member.builder()
                .userId(userId)
                .teamId(teamId)
                .role(MemberRole.OWNER)
                .authority(MemberRole.OWNER)
                .nickname(nickname)
                .build();
    }
}
