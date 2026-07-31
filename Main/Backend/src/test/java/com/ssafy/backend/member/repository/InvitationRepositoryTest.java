package com.ssafy.backend.member.repository;

import com.ssafy.backend.member.entity.Invitation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * InvitationRepository 실제 쿼리·제약 검증 — 특히 Mock 단위 테스트로는 잡히지 않는
 * (team_id, target_user_id) 유니크 제약의 예외 변환(DataIntegrityViolationException)과, expires_at 기반
 * 만료 필터링(existsBy.../findBy...ExpiresAtAfter)의 실제 쿼리 정확성을 실 DB로 확인한다.
 *
 * <p>MemberRepositoryTest와 동일한 관례: 스키마는 database-schema.md DDL로 관리(ddl-auto: validate)하고
 * {@code @AutoConfigureTestDatabase(replace = NONE)}으로 application.yml(local)의 실제 데이터소스
 * (SSH 터널 localhost:5432 공유 개발 DB)를 그대로 쓴다. 각 테스트는 @DataJpaTest 기본 롤백으로 종료돼
 * 개발 DB에 데이터가 남지 않는다. 실행 전 SSH 터널이 연결돼 있어야 한다.
 * invitations는 FK 제약이 없어(members/teams와 동일 컨벤션) User·Team을 미리 영속화할 필요가 없다 —
 * team_id/target_user_id는 유니크 토큰 대신 매 테스트 임의의 Long 값으로 충돌을 피한다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("InvitationRepository 통합 테스트")
class InvitationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InvitationRepository invitationRepository;

    // 공유 개발 DB의 기존 데이터(및 병렬 실행 중인 다른 테스트)와 충돌하지 않도록 매번 임의의 ID를 쓴다.
    private Long uniqueId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }

    private Invitation invitation(Long teamId, Long inviterId, Long targetUserId, OffsetDateTime expiresAt) {
        return Invitation.builder()
                .invitationId(UUID.randomUUID())
                .teamId(teamId)
                .inviterId(inviterId)
                .targetUserId(targetUserId)
                .expiresAt(expiresAt)
                .build();
    }

    @Nested
    @DisplayName("(team_id, target_user_id) 유니크 제약")
    class UniqueConstraint {

        @Test
        @DisplayName("같은 team_id+target_user_id로 두 번째 초대를 저장하면 DataIntegrityViolationException이 발생한다")
        void savingDuplicateTeamAndTargetThrowsConstraintViolation() {
            // given
            Long teamId = uniqueId();
            Long targetUserId = uniqueId();
            invitationRepository.saveAndFlush(
                    invitation(teamId, uniqueId(), targetUserId, OffsetDateTime.now().plusDays(1)));

            // when & then — inviter/invitationId가 달라도 (team_id, target_user_id) 조합이 같으면 제약에 걸린다.
            assertThatThrownBy(() -> invitationRepository.saveAndFlush(
                    invitation(teamId, uniqueId(), targetUserId, OffsetDateTime.now().plusDays(1))))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("team_id 또는 target_user_id가 다르면 제약에 걸리지 않는다")
        void savingWithDifferentTeamOrTargetDoesNotViolateConstraint() {
            Long teamId = uniqueId();
            Long targetUserId = uniqueId();
            invitationRepository.saveAndFlush(
                    invitation(teamId, uniqueId(), targetUserId, OffsetDateTime.now().plusDays(1)));

            // target_user_id만 다름 — 정상 저장.
            invitationRepository.saveAndFlush(
                    invitation(teamId, uniqueId(), uniqueId(), OffsetDateTime.now().plusDays(1)));
            // team_id만 다름 — 정상 저장.
            invitationRepository.saveAndFlush(
                    invitation(uniqueId(), uniqueId(), targetUserId, OffsetDateTime.now().plusDays(1)));
        }
    }

    @Nested
    @DisplayName("existsByTeamIdAndTargetUserIdAndExpiresAtAfter")
    class ExistsByTeamIdAndTargetUserIdAndExpiresAtAfter {

        @Test
        @DisplayName("만료되지 않은 초대가 있으면 true를 반환한다")
        void returnsTrueWhenPendingInvitationExists() {
            Long teamId = uniqueId();
            Long targetUserId = uniqueId();
            entityManager.persistAndFlush(
                    invitation(teamId, uniqueId(), targetUserId, OffsetDateTime.now().plusDays(1)));

            boolean exists = invitationRepository.existsByTeamIdAndTargetUserIdAndExpiresAtAfter(
                    teamId, targetUserId, OffsetDateTime.now());

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("만료된 초대만 있으면 false를 반환한다(만료 필터링)")
        void returnsFalseWhenOnlyExpiredInvitationExists() {
            Long teamId = uniqueId();
            Long targetUserId = uniqueId();
            entityManager.persistAndFlush(
                    invitation(teamId, uniqueId(), targetUserId, OffsetDateTime.now().minusMinutes(1)));

            boolean exists = invitationRepository.existsByTeamIdAndTargetUserIdAndExpiresAtAfter(
                    teamId, targetUserId, OffsetDateTime.now());

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("해당 team_id+target_user_id 초대가 없으면 false를 반환한다")
        void returnsFalseWhenNoInvitationExists() {
            boolean exists = invitationRepository.existsByTeamIdAndTargetUserIdAndExpiresAtAfter(
                    uniqueId(), uniqueId(), OffsetDateTime.now());

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByInvitationIdAndExpiresAtAfter")
    class FindByInvitationIdAndExpiresAtAfter {

        @Test
        @DisplayName("만료되지 않은 초대는 반환한다")
        void returnsInvitationWhenNotExpired() {
            Invitation saved = entityManager.persistAndFlush(
                    invitation(uniqueId(), uniqueId(), uniqueId(), OffsetDateTime.now().plusDays(1)));

            Optional<Invitation> found = invitationRepository.findByInvitationIdAndExpiresAtAfter(
                    saved.getInvitationId(), OffsetDateTime.now());

            assertThat(found).isPresent();
            assertThat(found.get().getInvitationId()).isEqualTo(saved.getInvitationId());
        }

        @Test
        @DisplayName("만료된 초대는 empty를 반환한다(만료 필터링)")
        void returnsEmptyWhenExpired() {
            Invitation saved = entityManager.persistAndFlush(
                    invitation(uniqueId(), uniqueId(), uniqueId(), OffsetDateTime.now().minusMinutes(1)));

            Optional<Invitation> found = invitationRepository.findByInvitationIdAndExpiresAtAfter(
                    saved.getInvitationId(), OffsetDateTime.now());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 invitationId면 empty를 반환한다")
        void returnsEmptyWhenInvitationIdNotFound() {
            Optional<Invitation> found = invitationRepository.findByInvitationIdAndExpiresAtAfter(
                    UUID.randomUUID(), OffsetDateTime.now());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc")
    class FindByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc {

        @Test
        @DisplayName("만료되지 않은 초대만 최신순(createdAt desc)으로 반환한다")
        void returnsOnlyPendingInvitationsOrderedByCreatedAtDesc() {
            Long targetUserId = uniqueId();
            OffsetDateTime now = OffsetDateTime.now();

            // 저장 순서와 무관하게 createdAt 역순으로 나오는지 확인하기 위해 오래된 것부터 저장한 뒤 createdAt을 강제로 덮어쓴다.
            Invitation older = entityManager.persistAndFlush(
                    invitation(uniqueId(), uniqueId(), targetUserId, now.plusDays(1)));
            setCreatedAt(older, now.minusHours(2));

            Invitation newer = entityManager.persistAndFlush(
                    invitation(uniqueId(), uniqueId(), targetUserId, now.plusDays(1)));
            setCreatedAt(newer, now.minusMinutes(1));

            entityManager.persistAndFlush(
                    invitation(uniqueId(), uniqueId(), targetUserId, now.minusMinutes(1)));

            List<Invitation> found = invitationRepository
                    .findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(targetUserId, now);

            // 만료된 초대(마지막에 저장한 것)는 제외되고, 나머지는 createdAt 역순(newer가 먼저)으로 반환된다.
            assertThat(found).extracting(Invitation::getInvitationId)
                    .containsExactly(newer.getInvitationId(), older.getInvitationId());
        }

        @Test
        @DisplayName("받은 초대가 없으면 빈 목록을 반환한다")
        void returnsEmptyListWhenNoInvitations() {
            List<Invitation> found = invitationRepository
                    .findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(uniqueId(), OffsetDateTime.now());

            assertThat(found).isEmpty();
        }

        // createdAt은 @CreationTimestamp(updatable=false)라 빌더·엔티티 setter로는 지정할 수 없어,
        // 정렬 검증을 위해 저장 후 JPQL 벌크 업데이트로 직접 덮어쓴다(정상 흐름에서는 쓰이지 않는 테스트 전용 우회).
        private void setCreatedAt(Invitation invitation, OffsetDateTime createdAt) {
            entityManager.getEntityManager()
                    .createQuery("update Invitation i set i.createdAt = :createdAt where i.invitationId = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", invitation.getInvitationId())
                    .executeUpdate();
            entityManager.getEntityManager().clear();
        }
    }

    @Nested
    @DisplayName("deleteByTeamId")
    class DeleteByTeamId {

        @Test
        @DisplayName("해당 team_id의 초대를 모두 삭제하고 다른 team_id의 초대는 남긴다")
        void deletesOnlyInvitationsForGivenTeam() {
            Long teamId = uniqueId();
            Long otherTeamId = uniqueId();
            Invitation toDelete1 = entityManager.persistAndFlush(
                    invitation(teamId, uniqueId(), uniqueId(), OffsetDateTime.now().plusDays(1)));
            Invitation toDelete2 = entityManager.persistAndFlush(
                    invitation(teamId, uniqueId(), uniqueId(), OffsetDateTime.now().plusDays(1)));
            Invitation toKeep = entityManager.persistAndFlush(
                    invitation(otherTeamId, uniqueId(), uniqueId(), OffsetDateTime.now().plusDays(1)));

            invitationRepository.deleteByTeamId(teamId);
            entityManager.flush();
            entityManager.clear();

            assertThat(invitationRepository.findById(toDelete1.getInvitationId())).isEmpty();
            assertThat(invitationRepository.findById(toDelete2.getInvitationId())).isEmpty();
            assertThat(invitationRepository.findById(toKeep.getInvitationId())).isPresent();
        }

        @Test
        @DisplayName("해당 team_id의 초대가 없어도 예외 없이 아무 일도 일어나지 않는다")
        void doesNothingWhenNoInvitationsForTeam() {
            invitationRepository.deleteByTeamId(uniqueId());
        }
    }
}