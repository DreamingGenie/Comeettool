package com.ssafy.backend.report.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.backend.report.dto.TranscriptSummaryDto;
import com.ssafy.backend.report.entity.AudioTranscription;

public interface AudioTranscriptionRepository extends JpaRepository<AudioTranscription, Long> {

    /**
     * REPORTS-01: audio_transcriptions에는 team_id가 없어 meeting_rooms와 조인해
     * 해당 spaceId(=team_id)에 속한 회의의 전사만 최신순으로 페이징 조회한다.
     */
    @Query("""
            select new com.ssafy.backend.report.dto.TranscriptSummaryDto(a.meetingId, m.name, a.createdAt)
            from AudioTranscription a, MeetingRoom m
            where a.meetingId = m.id
              and m.teamId = :teamId
            order by a.createdAt desc
            """)
    Page<TranscriptSummaryDto> findAllByTeamId(@Param("teamId") Long teamId, Pageable pageable);

    /**
     * REPORTS-03: mdUrl/pdfUrl은 Main Backend가 소유하는 캐시 컬럼이다.
     * AudioTranscription 엔티티는 @Immutable이라 변경 감지·save()로 갱신할 수 없으므로,
     * 이 두 컬럼에 한해 @Modifying UPDATE로 갱신 경로를 좁게 열어준다. 호출부는 반드시
     * @Transactional이 걸린 서비스 메서드 안에서 실행해야 한다.
     */
    @Modifying
    @Query("UPDATE AudioTranscription a SET a.mdUrl = :url WHERE a.meetingId = :meetingId")
    void updateMdUrl(@Param("meetingId") Long meetingId, @Param("url") String url);

    @Modifying
    @Query("UPDATE AudioTranscription a SET a.pdfUrl = :url WHERE a.meetingId = :meetingId")
    void updatePdfUrl(@Param("meetingId") Long meetingId, @Param("url") String url);
}
