package com.ssafy.backend.space.repository;

import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemberRepository 실제 쿼리 검증 — 특히 Mock 단위 테스트로는 잡히지 않는 @Query(JPQL) 두 건
 * (countMembersByTeamIds, findActiveTeamsWithMyAuthority)의 조인·프로젝션·필터를 실 DB로 확인한다.
 *
 * <p>UserRepositoryTest와 동일한 관례: 스키마는 database-schema.md DDL로 관리(ddl-auto: validate)하고
 * {@code @AutoConfigureTestDatabase(replace = NONE)}으로 application.yml(local)의 실제 데이터소스
 * (SSH 터널 localhost:5432 공유 개발 DB)를 그대로 쓴다. 각 테스트는 @DataJpaTest 기본 롤백으로 종료돼
 * 개발 DB에 데이터가 남지 않는다. 실행 전 SSH 터널이 연결돼 있어야 한다.
 * members는 users·teams로의 FK 제약이 있어, 멤버를 심기 전에 User·Team을 먼저 영속화한다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("MemberRepository 통합 테스트")
class MemberRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    // 공유 개발 DB의 기존 데이터와 충돌하지 않도록 유니크 토큰을 섞는다.
    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User persistUser() {
        String t = token();
        User user = User.builder().email("u-" + t + "@test.com").password("$2a$10$encoded").build();
        return entityManager.persistAndFlush(user);
    }

    private Team persistTeam(Long ownerId, boolean deleted) {
        Team team = Team.builder().name("팀-" + token()).ownerId(ownerId).color("#123456").build();
        if (deleted) {
            team.softDelete(OffsetDateTime.now());
        }
        return entityManager.persistAndFlush(team);
    }

    private Member persistMember(Long userId, Long teamId, MemberAuthority authority) {
        Member member = Member.builder()
                .userId(userId).teamId(teamId).authority(authority).nickname("nick-" + token()).build();
        return entityManager.persistAndFlush(member);
    }

    @Nested
    @DisplayName("파생 쿼리 (existsBy / findBy)")
    class DerivedQueries {

        @Test
        @DisplayName("existsByTeamIdAndUserId — 멤버면 true, 아니면 false")
        void existsByTeamIdAndUserId() {
            User user = persistUser();
            Team team = persistTeam(user.getId(), false);
            persistMember(user.getId(), team.getId(), MemberAuthority.OWNER);

            assertThat(memberRepository.existsByTeamIdAndUserId(team.getId(), user.getId())).isTrue();
            assertThat(memberRepository.existsByTeamIdAndUserId(team.getId(), -1L)).isFalse();
        }

        @Test
        @DisplayName("findByTeamIdAndUserId — 멤버 행을 반환하고, 없으면 empty")
        void findByTeamIdAndUserId() {
            User user = persistUser();
            Team team = persistTeam(user.getId(), false);
            persistMember(user.getId(), team.getId(), MemberAuthority.MEMBER);

            Optional<Member> found = memberRepository.findByTeamIdAndUserId(team.getId(), user.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getAuthority()).isEqualTo(MemberAuthority.MEMBER);

            assertThat(memberRepository.findByTeamIdAndUserId(team.getId(), -1L)).isEmpty();
        }

        @Test
        @DisplayName("findByTeamId — 해당 스페이스의 멤버 전원을 반환한다")
        void findByTeamId() {
            User owner = persistUser();
            User memberUser = persistUser();
            Team team = persistTeam(owner.getId(), false);
            persistMember(owner.getId(), team.getId(), MemberAuthority.OWNER);
            persistMember(memberUser.getId(), team.getId(), MemberAuthority.MEMBER);

            List<Member> members = memberRepository.findByTeamId(team.getId());

            assertThat(members).extracting(Member::getUserId)
                    .containsExactlyInAnyOrder(owner.getId(), memberUser.getId());
        }
    }

    @Nested
    @DisplayName("countMembersByTeamIds (@Query, GROUP BY 집계)")
    class CountMembersByTeamIds {

        @Test
        @DisplayName("팀별 참여자 수를 [teamId, count]로 집계하며, 멤버 없는 팀은 결과에 없다")
        void aggregatesMemberCountPerTeam() {
            User u1 = persistUser();
            User u2 = persistUser();
            User u3 = persistUser();
            Team teamTwo = persistTeam(u1.getId(), false);
            Team teamOne = persistTeam(u3.getId(), false);
            Team teamEmpty = persistTeam(u3.getId(), false);
            persistMember(u1.getId(), teamTwo.getId(), MemberAuthority.OWNER);
            persistMember(u2.getId(), teamTwo.getId(), MemberAuthority.MEMBER);
            persistMember(u3.getId(), teamOne.getId(), MemberAuthority.OWNER);

            List<Object[]> rows = memberRepository.countMembersByTeamIds(
                    List.of(teamTwo.getId(), teamOne.getId(), teamEmpty.getId()));

            Map<Long, Long> countByTeam = new HashMap<>();
            for (Object[] row : rows) {
                countByTeam.put((Long) row[0], (Long) row[1]);
            }
            assertThat(countByTeam).containsEntry(teamTwo.getId(), 2L);
            assertThat(countByTeam).containsEntry(teamOne.getId(), 1L);
            // 멤버가 없는 팀은 GROUP BY 결과에 포함되지 않는다.
            assertThat(countByTeam).doesNotContainKey(teamEmpty.getId());
        }
    }

    @Nested
    @DisplayName("findActiveTeamsWithMyAuthority (@Query, Team×Member 조인)")
    class FindActiveTeamsWithMyAuthority {

        @Test
        @DisplayName("사용자가 참여한 삭제되지 않은 스페이스를 [Team, 내 권한]으로 반환한다")
        void returnsActiveTeamsWithAuthority() {
            User user = persistUser();
            Team team = persistTeam(user.getId(), false);
            persistMember(user.getId(), team.getId(), MemberAuthority.OWNER);

            List<Object[]> rows = memberRepository.findActiveTeamsWithMyAuthority(user.getId());

            assertThat(rows).hasSize(1);
            assertThat((Team) rows.get(0)[0]).extracting(Team::getId).isEqualTo(team.getId());
            assertThat((MemberAuthority) rows.get(0)[1]).isEqualTo(MemberAuthority.OWNER);
        }

        @Test
        @DisplayName("soft delete된 스페이스는 결과에서 제외된다")
        void excludesSoftDeletedTeam() {
            User user = persistUser();
            Team active = persistTeam(user.getId(), false);
            Team deleted = persistTeam(user.getId(), true);
            persistMember(user.getId(), active.getId(), MemberAuthority.OWNER);
            persistMember(user.getId(), deleted.getId(), MemberAuthority.OWNER);

            List<Object[]> rows = memberRepository.findActiveTeamsWithMyAuthority(user.getId());

            assertThat(rows).extracting(row -> ((Team) row[0]).getId())
                    .containsExactly(active.getId());
        }

        @Test
        @DisplayName("멤버가 아닌 스페이스는 반환하지 않는다")
        void excludesTeamsUserIsNotMemberOf() {
            User me = persistUser();
            User other = persistUser();
            Team myTeam = persistTeam(me.getId(), false);
            Team othersTeam = persistTeam(other.getId(), false);
            persistMember(me.getId(), myTeam.getId(), MemberAuthority.OWNER);
            persistMember(other.getId(), othersTeam.getId(), MemberAuthority.OWNER);

            List<Object[]> rows = memberRepository.findActiveTeamsWithMyAuthority(me.getId());

            assertThat(rows).extracting(row -> ((Team) row[0]).getId())
                    .containsExactly(myTeam.getId());
        }
    }
}
