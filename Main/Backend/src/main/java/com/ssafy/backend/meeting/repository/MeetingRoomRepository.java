package com.ssafy.backend.meeting.repository;

import com.ssafy.backend.meeting.entity.MeetingRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface MeetingRoomRepository extends JpaRepository<MeetingRoom, Long> {

    /**
     * MEET-06 호스트 양도용 활성 회의 조회.
     * 동시에 여러 양도 요청이 들어와도 하나씩 처리하도록 비관적 쓰기 잠금을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mr
            from MeetingRoom mr
            where mr.id = :meetingId
              and mr.isDeleted = false
            """)
    Optional<MeetingRoom> findActiveByIdForUpdate(@Param("meetingId") Long meetingId);

    // SPACE-11: 스페이스 삭제 시 하위 회의방 전파 soft delete(정책 SP-2). 팀당 1쿼리 벌크 갱신.
    @Modifying
    @Query("update MeetingRoom m set m.isDeleted = true, m.deletedAt = :deletedAt "
            + "where m.teamId = :teamId and m.isDeleted = false")
    int softDeleteByTeamId(@Param("teamId") Long teamId, @Param("deletedAt") OffsetDateTime deletedAt);
}
