package com.ssafy.backend.user.repository;

import com.ssafy.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserRepository.searchByNicknameOrEmail 실제 쿼리(LIKE, is_deleted 필터, 본인 제외, LIMIT, 정렬) 검증. 이 프로젝트는 스키마를
 * database-schema.md DDL로 직접 관리하고(ddl-auto: validate) Postgres 전용 문법
 *
 * @AutoConfigureTestDatabase(replace = NONE)으로 application.yml(local 프로필)의 실제 데이터소스, 즉 SSH 터널(localhost:5432)로 연결된 공유
 * 개발 서버 DB를 그대로 사용한다. 각 테스트는 @DataJpaTest 기본 동작인 트랜잭션 롤백으로 종료되어 개발 DB에 데이터가 남지 않는다. 실행 전 SSH 터널이 연결되어 있어야 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRepository 통합 테스트")
class UserRepositoryTest {

    // 검색 쿼리가 excludeUserId로 아무도 걸러내지 않게 하기 위한 값 — 실제 user_id는 항상 양수다.
    private static final long NO_EXCLUSION = -1L;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    // 공유 개발 DB에 이미 있는 다른 팀원 데이터와 검색어가 우연히 겹치지 않도록,
    // 테스트마다 무작위 토큰을 닉네임/이메일에 섞어 유일성을 보장한다.
    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User persistUser(String email, String nickname) {
        User user = User.builder().email(email).password("$2a$10$encoded").build();
        user.updateProfile(nickname, null, null, null, null, null, null, null);
        return entityManager.persistAndFlush(user);
    }

    @Nested
    @DisplayName("searchByNicknameOrEmail")
    class SearchByNicknameOrEmail {

        @Test
        @DisplayName("닉네임에_검색어가_포함되면_대소문자_무관하게_찾는다")
        void 닉네임에_검색어가_포함되면_대소문자_무관하게_찾는다() {
            // given
            String t = token();
            User target = persistUser(t + "@test.com", "Hello" + t + "World");
            persistUser("other-" + t + "@test.com", "nothing-" + t);

            // when
            List<User> result = userRepository.searchByNicknameOrEmail(
                    "LLO" + t.toUpperCase() + "wor", NO_EXCLUSION, Limit.of(7));

            // then
            assertThat(result).extracting(User::getId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("이메일에_검색어가_포함되면_찾는다")
        void 이메일에_검색어가_포함되면_찾는다() {
            // given
            String t = token();
            User target = persistUser("findme-" + t + "@test.com", "nick-" + t + "-1");
            persistUser("nomatch-" + t + "@test.com", "nick-" + t + "-2");

            // when
            List<User> result = userRepository.searchByNicknameOrEmail("findme-" + t, NO_EXCLUSION, Limit.of(7));

            // then
            assertThat(result).extracting(User::getId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("닉네임과_이메일_모두에_검색어가_포함돼도_중복없이_1건만_반환한다")
        void 닉네임과_이메일_모두에_검색어가_포함돼도_중복없이_1건만_반환한다() {
            // given: OR 조건 양쪽을 동시에 만족하는 유저 — 조인이 없는 단일 테이블 쿼리라 원래 중복될 수 없지만,
            // 쿼리가 바뀌어도 이 전제가 깨지지 않는지 회귀 방지 차원에서 명시적으로 검증한다.
            String t = token();
            User target = persistUser("match-" + t + "@test.com", "match-" + t);

            // when
            List<User> result = userRepository.searchByNicknameOrEmail("match-" + t, NO_EXCLUSION, Limit.of(7));

            // then
            assertThat(result).extracting(User::getId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("탈퇴한_계정은_결과에서_제외된다")
        void 탈퇴한_계정은_결과에서_제외된다() {
            // given
            String t = token();
            User withdrawn = persistUser("bye-" + t + "@test.com", "bye-" + t);
            // user_color는 DB DEFAULT로 채워지는 컬럼(insertable=false)이라, 방금 persist한 엔티티는
            // 그 값을 모른 채로 메모리에 남아있다 — 그대로 withdraw() 후 flush하면 더티 체킹 UPDATE가
            // user_color를 null로 덮어써 NOT NULL 제약을 위반한다. 실제 서비스 코드(UserServiceImpl)는
            // 항상 findById()로 다시 조회한 엔티티를 다루므로 이 문제가 없다 — 테스트도 그 흐름을 맞춘다.
            entityManager.clear();
            User reloaded = userRepository.findById(withdrawn.getId()).orElseThrow();
            reloaded.withdraw();
            entityManager.flush();

            // when
            List<User> result = userRepository.searchByNicknameOrEmail("bye-" + t, NO_EXCLUSION, Limit.of(7));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("본인은_결과에서_제외된다")
        void 본인은_결과에서_제외된다() {
            // given
            String t = token();
            User self = persistUser("self-" + t + "@test.com", "self-" + t + "-a");
            User other = persistUser("other-" + t + "@test.com", "self-" + t + "-b");

            // when
            List<User> result = userRepository.searchByNicknameOrEmail("self-" + t, self.getId(), Limit.of(7));

            // then
            assertThat(result).extracting(User::getId).containsExactly(other.getId());
        }

        @Test
        @DisplayName("결과가_LIMIT을_초과하면_최대_건수만큼만_반환한다")
        void 결과가_LIMIT을_초과하면_최대_건수만큼만_반환한다() {
            // given
            String t = token();
            for (int i = 0; i < 5; i++) {
                persistUser("limit" + i + "-" + t + "@test.com", "limit-" + t + "-" + i);
            }

            // when
            List<User> result = userRepository.searchByNicknameOrEmail("limit-" + t, NO_EXCLUSION, Limit.of(3));

            // then
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("userId_오름차순으로_정렬된다")
        void userId_오름차순으로_정렬된다() {
            // given
            String t = token();
            User first = persistUser("order1-" + t + "@test.com", "order-" + t + "-1");
            User second = persistUser("order2-" + t + "@test.com", "order-" + t + "-2");

            // when
            List<User> result = userRepository.searchByNicknameOrEmail("order-" + t, NO_EXCLUSION, Limit.of(7));

            // then
            assertThat(result).extracting(User::getId).containsExactly(first.getId(), second.getId());
        }
    }
}