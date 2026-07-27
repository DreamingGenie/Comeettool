package com.ssafy.backend.space.repository;

import com.ssafy.backend.space.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    // SPACE-05: 삭제되지 않은 스페이스만 상세 조회 대상.
    Optional<Team> findByIdAndIsDeletedFalse(Long id);
}
