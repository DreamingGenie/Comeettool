package com.ssafy.backend.document.authorization;

import java.util.Optional;
import java.util.UUID;

/**
 * 문서와 팀 멤버십을 조회해 협업 권한으로 변환하는 계약.
 *
 * <p>구현체는 OWNER/MEMBER를 WRITE로, GUEST를 READ로 변환해야 한다.</p>
 */
public interface DocumentPermissionReader {
    Optional<DocumentAccess> findByDocumentIdAndUserId(UUID documentId, Long userId);
}
