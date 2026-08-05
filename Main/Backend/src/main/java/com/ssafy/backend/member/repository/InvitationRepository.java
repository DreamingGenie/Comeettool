package com.ssafy.backend.member.repository;

import com.ssafy.backend.member.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    // MEMBER-02: 이미 대기 중인(만료되지 않은) 초대가 있는지 확인.
    boolean existsByTeamIdAndTargetUserIdAndExpiresAtAfter(Long teamId, Long targetUserId, OffsetDateTime now);

    /**
     * MEMBER-02: 재초대 전, 만료된 채 남아있는 이전 초대를 정리한다. (team_id, target_user_id) 유니크 제약은 만료 여부를
     * 모르므로, 만료된 행을 지우지 않으면 정상적인 재초대까지 제약 위반으로 막힌다. 벌크 DELETE로 즉시 실행되어야
     * (Hibernate flush 큐에서 INSERT가 DELETE보다 먼저 나가는 순서 문제를 피해) 뒤이은 saveAndFlush()보다 먼저 반영된다.
     */
    @Modifying
    @Query("delete from Invitation i where i.teamId = :teamId and i.targetUserId = :targetUserId")
    void deleteByTeamIdAndTargetUserId(@Param("teamId") Long teamId, @Param("targetUserId") Long targetUserId);

    // 수락/거절 시 유효한(만료되지 않은) 초대 조회(다음 작업 범위).
    Optional<Invitation> findByInvitationIdAndExpiresAtAfter(UUID invitationId, OffsetDateTime now);

    // MEMBER-03: 내가 받은 초대 목록 — 만료되지 않은 초대만, 최신순.
    List<Invitation> findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(Long targetUserId, OffsetDateTime now);

    // SPACE-11: 스페이스 삭제 시 관련 초대 정리. invitations는 is_deleted 컬럼이 없는 임시성 데이터라 soft delete 대신 hard delete.
    void deleteByTeamId(Long teamId);
}