package com.ssafy.backend.schedule.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * schedules 테이블 매핑 엔티티 (일정).
 * 권한 판단 기준은 creator_id(생성자) 단일, 참여자 목록은 user_id_arr(BIGINT[])로 관리한다.
 * 삭제는 soft delete(is_deleted + deleted_at)로 처리한다.
 */
@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    // DB가 IDENTITY로 채번(database-schema.md) — 엔티티는 값을 지정하지 않는다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    // 생성자. SCHEDULE-04/05 수정·삭제 권한 판단 기준(생성자 or 스페이스 Owner).
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "schedule_category", length = 1000)
    private String category;

    @Column(name = "schedule_title", length = 1000)
    private String title;

    @Column(name = "schedule_description")
    private String description;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    // PostgreSQL BIGINT[] ↔ List<Long>. SCHEDULE-01은 이 배열의 containment(@>)로 조회한다.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "user_id_arr", columnDefinition = "bigint[]")
    private List<Long> userIdArr;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Builder
    private Schedule(Long teamId, Long creatorId, String category, String title,
                     String description, OffsetDateTime startTime, OffsetDateTime endTime,
                     List<Long> userIdArr) {
        this.teamId = teamId;
        this.creatorId = creatorId;
        this.category = category;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.userIdArr = userIdArr;
        this.isDeleted = false;
    }

    public boolean isCreator(Long userId) {
        return creatorId.equals(userId);
    }

    // SCHEDULE-04: 부분 수정 — null이 아닌 필드만 갱신한다(전달되지 않은 필드는 유지).
    public void updateInfo(String category, String title, String description,
                           OffsetDateTime startTime, OffsetDateTime endTime, List<Long> userIdArr) {
        if (category != null) {
            this.category = category;
        }
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (startTime != null) {
            this.startTime = startTime;
        }
        if (endTime != null) {
            this.endTime = endTime;
        }
        if (userIdArr != null) {
            this.userIdArr = userIdArr;
        }
    }

    // SCHEDULE-05: soft delete. is_deleted=true + 삭제 시각 기록.
    public void softDelete(OffsetDateTime deletedAt) {
        this.isDeleted = true;
        this.deletedAt = deletedAt;
    }
}
