package com.ssafy.backend.member.repository;

import com.ssafy.backend.member.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    // MEMBER-02: 이미 대기 중인(만료되지 않은) 초대가 있는지 확인.
    boolean existsByTeamIdAndTargetUserIdAndExpiresAtAfter(Long teamId, Long targetUserId, OffsetDateTime now);

    // 수락/거절 시 유효한(만료되지 않은) 초대 조회(다음 작업 범위).
    Optional<Invitation> findByInvitationIdAndExpiresAtAfter(UUID invitationId, OffsetDateTime now);

    // 내 초대함 조회 — 만료되지 않은 초대만(다음 작업 범위).
    List<Invitation> findByTargetUserIdAndExpiresAtAfter(Long targetUserId, OffsetDateTime now);
}