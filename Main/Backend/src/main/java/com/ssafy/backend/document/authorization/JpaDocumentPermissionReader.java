package com.ssafy.backend.document.authorization;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDocumentPermissionReader implements DocumentPermissionReader {

    private final EntityManager entityManager;

    @Override
    public Optional<DocumentAccess> findByDocumentIdAndUserId(UUID documentId, Long userId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT d.team_id, m.role
                        FROM documents d
                        JOIN members m ON m.team_id = d.team_id
                        WHERE d.document_id = :documentId
                          AND m.user_id = :userId
                          AND d.is_deleted = false
                        """)
                .setParameter("documentId", documentId)
                .setParameter("userId", userId)
                .getResultList();

        for (Object row : rows) {
            Optional<DocumentAccess> access = toDocumentAccess(row);
            if (access.isPresent()) {
                return access;
            }
        }
        return Optional.empty();
    }

    private Optional<DocumentAccess> toDocumentAccess(Object row) {
        Object[] columns = (Object[]) row;
        Long teamId = ((Number) columns[0]).longValue();
        String role = String.valueOf(columns[1]).toUpperCase(Locale.ROOT);

        return switch (role) {
            case "OWNER", "MEMBER" -> Optional.of(new DocumentAccess(teamId, CollaborationPermission.WRITE));
            case "GUEST" -> Optional.of(new DocumentAccess(teamId, CollaborationPermission.READ));
            default -> Optional.empty();
        };
    }
}
