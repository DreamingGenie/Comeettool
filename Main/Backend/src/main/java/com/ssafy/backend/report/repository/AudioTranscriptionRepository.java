package com.ssafy.backend.report.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
