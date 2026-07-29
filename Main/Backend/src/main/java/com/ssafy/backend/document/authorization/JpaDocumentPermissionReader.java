package com.ssafy.backend.document.authorization;

import com.ssafy.backend.document.repository.DocumentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDocumentPermissionReader implements DocumentPermissionReader {

    private final DocumentRepository documentRepository;
    private final DocumentAuthorizationService documentAuthorizationService;

    @Override
    public Optional<DocumentAccess> findByDocumentIdAndUserId(UUID documentId, Long userId) {
        return documentRepository.findByIdAndIsDeletedFalse(documentId)
                .flatMap(document -> documentAuthorizationService
                        .findCollaborationPermission(document.getTeamId(), userId)
                        .map(permission -> new DocumentAccess(document.getTeamId(), permission)));
    }
}
