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
 * audio_transcriptions 테이블 매핑 엔티티.
 * 이 테이블은 AI_BE(Python)가 쓰기 전용으로 관리한다 — Main Backend는 읽기 전용으로만 매핑하며,
 * 세터·save용 도메인 메서드를 두지 않는다(@Immutable로 변경 감지·flush도 막는다).
 * mdUrl/pdfUrl 갱신(내보내기 캐싱)은 REPORTS-03 범위.
 */
@Entity
@Immutable
@Table(name = "audio_transcriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AudioTranscription {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", nullable = false, columnDefinition = "jsonb")
    private String transcript;

    @Column(name = "md_url")
    private String mdUrl;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
