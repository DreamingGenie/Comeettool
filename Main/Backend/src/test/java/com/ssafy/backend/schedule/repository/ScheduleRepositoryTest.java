package com.ssafy.backend.schedule.repository;

import com.ssafy.backend.schedule.entity.Schedule;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScheduleRepository 통합 테스트.
 * SCHEDULE-01의 PG 배열 containment(@>) 쿼리와 soft delete 필터를 실제 PostgreSQL로 검증한다.
 * (H2는 bigint[]·@>를 지원하지 않으므로 반드시 실 DB가 필요하다)
 *
 * <p>관례는 TeamRepositoryTest와 동일 — @AutoConfigureTestDatabase(replace = NONE)로 실 개발 DB
 * (SSH 터널)에 붙고 @DataJpaTest 롤백으로 종료된다. 실행 전 SSH 터널이 연결돼 있어야 한다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ScheduleRepository 통합 테스트")
class ScheduleRepositoryTest {

    private static final Long OUTSIDER_ID = 999_000_001L;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ScheduleRepository scheduleRepository;

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Long persistUser() {
        User user = User.builder()
                .email("sched-" + token() + "@test.com")
                .password("hash")
                .nickname("n-" + token())
                .build();
        return entityManager.persistAndFlush(user).getId();
    }

    private Long persistTeam(Long ownerId) {
        Team team = Team.builder().name("팀-" + token()).ownerId(ownerId).color("#123456").build();
        return entityManager.persistAndFlush(team).getId();
    }

    private Schedule persistSchedule(Long teamId, Long creatorId, List<Long> userIdArr,
                                     OffsetDateTime start, boolean deleted) {
        Schedule schedule = Schedule.builder()
                .teamId(teamId)
                .creatorId(creatorId)
                .title("일정-" + token())
                .startTime(start)
                .userIdArr(userIdArr)
                .build();
        if (deleted) {
            schedule.softDelete(OffsetDateTime.now());
        }
        return entityManager.persistAndFlush(schedule);
    }

    @Test
    @DisplayName("findMySchedules는 user_id_arr에 내가 포함된, 삭제되지 않은 일정만 반환한다")
    void findMySchedulesReturnsOnlyMyActiveOnes() {
        Long me = persistUser();
        Long teamId = persistTeam(me);
        OffsetDateTime base = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");

        Schedule mine = persistSchedule(teamId, me, List.of(me, OUTSIDER_ID), base, false);
        persistSchedule(teamId, me, List.of(OUTSIDER_ID), base, false);      // 내가 참여자 아님 → 제외
        persistSchedule(teamId, me, List.of(me), base, true);                // 삭제됨 → 제외
        entityManager.clear();

        List<Schedule> result = scheduleRepository.findMySchedules(me);

        assertThat(result).extracting(Schedule::getId).containsExactly(mine.getId());
    }

    @Test
    @DisplayName("findByTeamIdAndIsDeletedFalseOrderByStartTimeAsc는 삭제 제외 + 시작시각 오름차순으로 반환한다")
    void findTeamSchedulesOrderedExcludingDeleted() {
        Long me = persistUser();
        Long teamId = persistTeam(me);
        OffsetDateTime later = OffsetDateTime.parse("2026-08-11T10:00:00+09:00");
        OffsetDateTime earlier = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");

        Schedule s2 = persistSchedule(teamId, me, List.of(me), later, false);
        Schedule s1 = persistSchedule(teamId, me, List.of(me), earlier, false);
        persistSchedule(teamId, me, List.of(me), earlier, true);             // 삭제됨 → 제외
        entityManager.clear();

        List<Schedule> result = scheduleRepository.findByTeamIdAndIsDeletedFalseOrderByStartTimeAsc(teamId);

        assertThat(result).extracting(Schedule::getId).containsExactly(s1.getId(), s2.getId());
    }
}
