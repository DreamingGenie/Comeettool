package com.ssafy.backend.meeting.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

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

/**
 * meeting_rooms 테이블 매핑 엔티티.
 * 회의 Host 권한은 users.user_id를 저장하는 host_id를 단일 기준으로 판단한다.
 */
@Entity
@Table(name = "meeting_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingRoom {

    private static final String DEFAULT_NAME = "새 회의";

    // DB가 IDENTITY로 채번하므로 엔티티에서 ID를 직접 지정하지 않는다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_room_id")
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "meeting_room_name", nullable = false, length = 250)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Builder
    private MeetingRoom(Long teamId, Long hostId, String name) {
        this.teamId = teamId;
        this.hostId = hostId;
        this.name = name == null || name.isBlank() ? DEFAULT_NAME : name;
        this.isDeleted = false;
    }

    public boolean isHost(Long requesterUserId) {
        return hostId.equals(requesterUserId);
    }

    // MEET-06: 참여자·팀 검증은 Service에서 완료하고, 엔티티는 Host userId만 변경한다.
    public void transferHostTo(Long nextHostUserId) {
        if (nextHostUserId == null || nextHostUserId <= 0) {
            throw new IllegalArgumentException("새 호스트 ID는 양수여야 합니다.");
        }
        this.hostId = nextHostUserId;
    }
}
