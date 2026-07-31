package com.ssafy.backend.member.entity;

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
 * team·user·team_role 은 BIGINT FK 이지만 본 작업 범위에서는 식별자(Long)로만 다룬다.
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
    @Column(name = "authority", nullable = false)
    private MemberAuthority authority;

    // 배정된 역할(team_roles FK). 1 멤버 = 최대 1 역할, 미배정 시 null. 역할 부여는 MEMBER-06 범위.
    @Column(name = "team_role_id")
    private Long teamRoleId;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Builder
    private Member(Long userId, Long teamId, MemberAuthority authority, Long teamRoleId, String nickname) {
        this.userId = userId;
        this.teamId = teamId;
        this.authority = authority;
        this.teamRoleId = teamRoleId;
        this.nickname = nickname;
    }

    // SPACE-101: 소유권 위임 시 권한 승격(→OWNER)·강등(→MEMBER)에 사용.
    public void changeAuthority(MemberAuthority authority) {
        this.authority = authority;
    }

    // SPACE-01: 생성자를 Owner 권한 멤버로 등록. 역할은 미배정(null).
    public static Member owner(Long userId, Long teamId, String nickname) {
        return Member.builder()
                .userId(userId)
                .teamId(teamId)
                .authority(MemberAuthority.OWNER)
                .teamRoleId(null)
                .nickname(nickname)
                .build();
    }

    // MEMBER-03: 초대 수락으로 합류한 사용자를 일반 멤버(MEMBER)로 등록. 역할은 미배정(null).
    public static Member invited(Long userId, Long teamId, String nickname) {
        return Member.builder()
                .userId(userId)
                .teamId(teamId)
                .authority(MemberAuthority.MEMBER)
                .teamRoleId(null)
                .nickname(nickname)
                .build();
    }
}