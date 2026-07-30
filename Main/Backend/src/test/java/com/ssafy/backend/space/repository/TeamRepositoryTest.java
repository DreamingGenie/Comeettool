package com.ssafy.backend.space.repository;

import com.ssafy.backend.space.entity.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TeamRepository.findByIdAndIsDeletedFalse의 soft delete 필터 동작 검증.
 * SPACE-11 삭제가 teams.is_deleted를 세팅하므로, 삭제된 스페이스가 조회에서 제외되는지 실 DB로 확인한다.
 *
 * <p>관례는 UserRepositoryTest와 동일 — @AutoConfigureTestDatabase(replace = NONE)로 실제 개발 DB
 * (SSH 터널)에 붙고 @DataJpaTest 롤백으로 종료된다. 실행 전 SSH 터널이 연결돼 있어야 한다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TeamRepository 통합 테스트")
class TeamRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TeamRepository teamRepository;

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Team persistTeam(boolean deleted) {
        // team_owner_id는 users FK가 걸려 있지 않으므로(스키마상 members만 FK) 임의 값으로 둔다.
        Team team = Team.builder().name("팀-" + token()).ownerId(1L).color("#123456").build();
        if (deleted) {
            team.softDelete(OffsetDateTime.now());
        }
        return entityManager.persistAndFlush(team);
    }

    @Test
    @DisplayName("삭제되지 않은 스페이스는 반환한다")
    void returnsActiveTeam() {
        Team team = persistTeam(false);

        Optional<Team> found = teamRepository.findByIdAndIsDeletedFalse(team.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(team.getId());
    }

    @Test
    @DisplayName("soft delete된 스페이스는 empty를 반환한다")
    void returnsEmptyForSoftDeletedTeam() {
        Team team = persistTeam(true);

        Optional<Team> found = teamRepository.findByIdAndIsDeletedFalse(team.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 ID면 empty를 반환한다")
    void returnsEmptyForAbsentId() {
        assertThat(teamRepository.findByIdAndIsDeletedFalse(-1L)).isEmpty();
    }
}
