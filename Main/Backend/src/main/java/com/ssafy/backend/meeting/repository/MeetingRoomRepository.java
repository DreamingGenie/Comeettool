package com.ssafy.backend.meeting.repository;

import com.ssafy.backend.meeting.entity.MeetingRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
