package com.ssafy.backend.document.repository;

import com.ssafy.backend.document.entity.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndDeletedFalse(UUID id);

    List<Document> findAllByTeamIdAndDeletedFalseOrderByUpdatedAtDesc(Long teamId);
}
