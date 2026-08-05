package com.ssafy.backend.document.repository;

import com.ssafy.backend.document.entity.Document;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndIsDeletedFalse(UUID id);

    List<Document> findAllByTeamIdAndIsDeletedFalseOrderByUpdatedAtDesc(Long teamId);

    // SPACE-11: 스페이스 삭제 시 하위 문서 전파 soft delete(정책 SP-2). 팀당 1쿼리 벌크 갱신.
    @Modifying
    @Query("update Document d set d.isDeleted = true, d.deletedAt = :deletedAt "
            + "where d.teamId = :teamId and d.isDeleted = false")
    int softDeleteByTeamId(@Param("teamId") Long teamId, @Param("deletedAt") OffsetDateTime deletedAt);
}
