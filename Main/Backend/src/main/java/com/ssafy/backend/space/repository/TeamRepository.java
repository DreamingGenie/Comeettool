package com.ssafy.backend.space.repository;

import com.ssafy.backend.space.entity.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    // SPACE-05: 삭제되지 않은 스페이스만 상세 조회 대상(잠금 없음, 읽기 전용).
    Optional<Team> findByIdAndIsDeletedFalse(Long id);

    /**
     * SPACE-07/11/101: 소유자 상태를 바꾸는 작업(나가기·삭제·위임)용 조회.
     * 동시 요청(위임 중복, 위임·삭제 경합 등)을 직렬화하기 위해 비관적 쓰기 잠금을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :spaceId and t.isDeleted = false")
    Optional<Team> findActiveByIdForUpdate(@Param("spaceId") Long spaceId);
}
