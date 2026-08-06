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
 * facilitator_reports 테이블 매핑 엔티티.
 * title/meetingType/overallReview/participationComment/participationStats/qualityEvaluation/
 * strengths/improvements/decisionProcessChecks/unresolvedIssuesEvaluation/nextMeetingSuggestions/createdAt은
 * AI_BE(Python)가 쓰기 전용으로 관리한다 — Main Backend는 읽기 전용으로만 매핑하며,
 * 세터·save용 도메인 메서드를 두지 않는다(@Immutable로 변경 감지·flush도 막는다).
 * mdUrl/pdfUrl은 Main Backend가 소유하는 캐시 컬럼이지만 갱신 로직은 REPORTS-11 범위이므로
 * 이번 작업(REPORTS-09, 목록 조회)에서는 필드만 선언한다(MeetingMinutes의 mdUrl/pdfUrl과 동일한 설계).
 */
@Entity
@Immutable
@Table(name = "facilitator_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilitatorReport {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "title")
    private String title;

    @Column(name = "meeting_type")
    private String meetingType;

    @Column(name = "overall_review")
    private String overallReview;

    @Column(name = "participation_comment")
    private String participationComment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participation_stats", columnDefinition = "jsonb")
    private String participationStats;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_evaluation", columnDefinition = "jsonb")
    private String qualityEvaluation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", columnDefinition = "jsonb")
    private String strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvements", columnDefinition = "jsonb")
    private String improvements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_process_checks", columnDefinition = "jsonb")
    private String decisionProcessChecks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unresolved_issues_evaluation", columnDefinition = "jsonb")
    private String unresolvedIssuesEvaluation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "next_meeting_suggestions", columnDefinition = "jsonb")
    private String nextMeetingSuggestions;

    @Column(name = "md_url")
    private String mdUrl;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
