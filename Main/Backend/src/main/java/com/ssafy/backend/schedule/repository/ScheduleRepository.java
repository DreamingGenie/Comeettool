package com.ssafy.backend.schedule.repository;

import com.ssafy.backend.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // SCHEDULE-04/05: 삭제되지 않은 일정만 조회 대상.
    Optional<Schedule> findByIdAndIsDeletedFalse(Long id);

    // SCHEDULE-02: 팀(스페이스) 일정 목록. 시작 시각 오름차순.
    List<Schedule> findByTeamIdAndIsDeletedFalseOrderByStartTimeAsc(Long teamId);

    /**
     * SCHEDULE-01: 내가 참여자로 포함된 일정. PG 배열 containment(@>)로 조회해
     * schedules_user_id_arr_gin(GIN) 인덱스를 활용한다. (= ANY는 인덱스 미사용)
     */
    @Query(value = "SELECT * FROM schedules "
            + "WHERE user_id_arr @> ARRAY[:userId]::bigint[] AND is_deleted = false "
            + "ORDER BY start_time ASC", nativeQuery = true)
    List<Schedule> findMySchedules(@Param("userId") Long userId);
}
