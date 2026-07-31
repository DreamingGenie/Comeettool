package com.ssafy.backend.meeting.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.backend.meeting.entity.MeetingRoom;

import jakarta.persistence.LockModeType;

public interface MeetingRoomRepository extends JpaRepository<MeetingRoom, Long> {
    // SPACE-11: 스페이스 삭제 시 하위 회의방 전파 soft delete(정책 SP-2). 팀당 1쿼리 벌크 갱신.
    @Modifying
    @Query("update MeetingRoom m set m.isDeleted = true, m.deletedAt = :deletedAt "
            + "where m.teamId = :teamId and m.isDeleted = false")
    int softDeleteByTeamId(@Param("teamId") Long teamId, @Param("deletedAt") OffsetDateTime deletedAt);


    // MEET-01: 팀 스페이스에서 현재 진행 중인 회의 수를 조회한다.
    long countByTeamIdAndIsDeletedFalse(Long teamId);

    /**
     * MEET-02: Participant에서 얻은 회의 ID 중 같은 스페이스의 진행 중인 회의만 조회한다.
     */
    @Query("""
            select mr
            from MeetingRoom mr
            where mr.id in :meetingRoomIds
              and mr.teamId = :teamId
              and mr.isDeleted = false
            order by mr.createdAt desc, mr.id desc
            """)
    List<MeetingRoom> findAllActiveByIdsAndTeamId(
            @Param("meetingRoomIds") List<Long> meetingRoomIds,
            @Param("teamId") Long teamId
    );

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

    /**
     * MEET-07 참여자 조회용 활성 회의 조회.
     * 읽기 작업이므로 MEET-06과 달리 비관적 쓰기 잠금을 사용하지 않는다.
     */
    @Query("""
            select mr
            from MeetingRoom mr
            where mr.id = :meetingId
              and mr.isDeleted = false
            """)
    Optional<MeetingRoom> findActiveById(@Param("meetingId") Long meetingId);
}
