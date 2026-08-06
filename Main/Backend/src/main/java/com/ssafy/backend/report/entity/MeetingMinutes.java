package com.ssafy.backend.report.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * meeting_minutes 테이블 매핑 엔티티.
 * title/summary/topics/decisions/actionItems/openIssues/createdAt은 AI_BE(Python)가 쓰기 전용으로 관리한다 —
 * Main Backend는 읽기 전용으로만 매핑하며, 세터·save용 도메인 메서드를 두지 않는다(@Immutable로 변경 감지·flush도 막는다).
 * isConfirmed/confirmedAt/mdUrl/pdfUrl은 Main Backend가 소유하는 컬럼이지만 갱신 로직은 REPORTS-05/06/07 범위이므로
 * 이번 작업(REPORTS-04, 목록 조회)에서는 필드만 선언한다.
 */
@Entity
@Immutable
@Table(name = "meeting_minutes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingMinutes {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "title")
    private String title;

    @Column(name = "summary")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topics", columnDefinition = "jsonb")
    private String topics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decisions", columnDefinition = "jsonb")
    private String decisions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_items", columnDefinition = "jsonb")
    private String actionItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "open_issues", columnDefinition = "jsonb")
    private String openIssues;

    @Column(name = "is_confirmed")
    private Boolean isConfirmed;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "md_url")
    private String mdUrl;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
