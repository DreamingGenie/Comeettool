package com.ssafy.backend.member.repository;

import com.ssafy.backend.member.entity.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRoleRepository extends JpaRepository<TeamRole, Long> {

    // MEMBER-06: 역할이 요청한 스페이스에 소속돼 있는지까지 한 번에 확인.
    Optional<TeamRole> findByIdAndTeamId(Long teamRoleId, Long teamId);

    // MEMBER-14: 같은 스페이스 내 역할명 중복 여부 사전 체크.
    boolean existsByTeamIdAndRoleName(Long teamId, String roleName);

    // MEMBER-15: 스페이스의 역할 목록 — 생성순.
    List<TeamRole> findAllByTeamIdOrderByCreatedAtAsc(Long teamId);
}