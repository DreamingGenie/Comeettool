package com.ssafy.backend.report.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.backend.report.dto.FacilitatorReportSummaryDto;
import com.ssafy.backend.report.entity.FacilitatorReport;

public interface FacilitatorReportRepository extends JpaRepository<FacilitatorReport, Long> {

    /**
     * REPORTS-09: facilitator_reports에는 team_id가 없어 meeting_rooms와 조인해
     * 해당 spaceId(=team_id)에 속한 회의의 퍼실리테이터 리포트만 최신순으로 페이징 조회한다.
     * REPORTS-01(AudioTranscriptionRepository.findAllByTeamId)/REPORTS-04(MeetingMinutesRepository.findAllByTeamId)와
     * 동일한 콤마 조인 스타일.
     */
    @Query("""
            select new com.ssafy.backend.report.dto.FacilitatorReportSummaryDto(
                fr.meetingId, m.name, fr.title, fr.meetingType, fr.createdAt)
            from FacilitatorReport fr, MeetingRoom m
            where fr.meetingId = m.id
              and m.teamId = :teamId
            order by fr.createdAt desc
            """)
    Page<FacilitatorReportSummaryDto> findAllByTeamId(@Param("teamId") Long teamId, Pageable pageable);

    /**
     * REPORTS-11: mdUrl/pdfUrl은 Main Backend가 소유하는 캐시 컬럼이다.
     * AudioTranscriptionRepository.updateMdUrl/updatePdfUrl(REPORTS-03)·MeetingMinutesRepository의
     * 것(REPORTS-08)과 동일한 패턴.
     */
    @Modifying
    @Query("UPDATE FacilitatorReport f SET f.mdUrl = :url WHERE f.meetingId = :meetingId")
    void updateMdUrl(@Param("meetingId") Long meetingId, @Param("url") String url);

    @Modifying
    @Query("UPDATE FacilitatorReport f SET f.pdfUrl = :url WHERE f.meetingId = :meetingId")
    void updatePdfUrl(@Param("meetingId") Long meetingId, @Param("url") String url);
}
