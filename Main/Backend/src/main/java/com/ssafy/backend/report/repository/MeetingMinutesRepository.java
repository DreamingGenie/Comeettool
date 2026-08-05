package com.ssafy.backend.report.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * REPORTS-06: title/summary/topics/decisions/actionItems/openIssues는 AI_BE가 최초 생성하지만
     * 이후 갱신 권한은 Main Backend가 가진다. MeetingMinutes는 @Immutable이라 변경 감지·save()로 갱신할 수 없으므로,
     * REPORTS-03(AudioTranscriptionRepository.updateMdUrl/updatePdfUrl)과 동일하게 필드별 @Modifying UPDATE로
     * 갱신 경로를 좁게 열어준다. 서비스 계층은 요청에 포함된(null이 아닌) 필드에 대해서만 선택적으로 호출하며,
     * 반드시 @Transactional이 걸린 메서드 안에서 실행해야 한다.
     */
    @Modifying
    @Query("UPDATE MeetingMinutes m SET m.title = :title WHERE m.meetingId = :meetingId")
    void updateTitle(@Param("meetingId") Long meetingId, @Param("title") String title);

    @Modifying
    @Query("UPDATE MeetingMinutes m SET m.summary = :summary WHERE m.meetingId = :meetingId")
    void updateSummary(@Param("meetingId") Long meetingId, @Param("summary") String summary);

    // topics/decisions/actionItems/openIssues는 항목 단위 부분 수정을 지원하지 않는 배열 전체 교체다.
    @Modifying
    @Query("UPDATE MeetingMinutes m SET m.topics = :topics WHERE m.meetingId = :meetingId")
    void updateTopics(@Param("meetingId") Long meetingId, @Param("topics") String topics);

    @Modifying
    @Query("UPDATE MeetingMinutes m SET m.decisions = :decisions WHERE m.meetingId = :meetingId")
    void updateDecisions(@Param("meetingId") Long meetingId, @Param("decisions") String decisions);

    @Modifying
    @Query("UPDATE MeetingMinutes m SET m.actionItems = :actionItems WHERE m.meetingId = :meetingId")
    void updateActionItems(@Param("meetingId") Long meetingId, @Param("actionItems") String actionItems);

    @Modifying
    @Query("UPDATE MeetingMinutes m SET m.openIssues = :openIssues WHERE m.meetingId = :meetingId")
    void updateOpenIssues(@Param("meetingId") Long meetingId, @Param("openIssues") String openIssues);
}
