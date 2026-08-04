package com.ssafy.backend.report.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.backend.report.dto.MinutesSummaryDto;
import com.ssafy.backend.report.entity.MeetingMinutes;

public interface MeetingMinutesRepository extends JpaRepository<MeetingMinutes, Long> {

    /**
     * REPORTS-04: meeting_minutes에는 team_id가 없어 meeting_rooms와 조인해
     * 해당 spaceId(=team_id)에 속한 회의의 회의록만 최신순으로 페이징 조회한다.
     * REPORTS-01(AudioTranscriptionRepository.findAllByTeamId)과 동일한 콤마 조인 스타일.
     */
    @Query("""
            select new com.ssafy.backend.report.dto.MinutesSummaryDto(
                mm.meetingId, m.name, mm.title, mm.isConfirmed, mm.createdAt)
            from MeetingMinutes mm, MeetingRoom m
            where mm.meetingId = m.id
              and m.teamId = :teamId
            order by mm.createdAt desc
            """)
    Page<MinutesSummaryDto> findAllByTeamId(@Param("teamId") Long teamId, Pageable pageable);
}
