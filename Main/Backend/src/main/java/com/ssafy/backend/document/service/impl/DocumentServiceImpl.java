package com.ssafy.backend.document.service.impl;

import com.ssafy.backend.document.authorization.DocumentAccess;
import com.ssafy.backend.document.authorization.DocumentPermissionReader;
import com.ssafy.backend.document.dto.ResponseCollaborationTokenDto;
import com.ssafy.backend.document.service.DocumentService;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentPermissionReader documentPermissionReader;
    private final jwtProvider jwtProvider;

    @Override
    @Transactional(readOnly = true)
    ResponseCollaborationTokenDto issueCollaborationToken(UUID documentId, String userId) {
        Long parsedUserId = parseUserId(userId);
        DocumentAccess access = documentPermissionReader
                .findByDocumentIdAndUserId(documentId, parsedUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        String permission = access.permission().name();
        String token = jwtProvider.createCollaborationToken(
                userId,
                documentId,
                access.teamId(),
                permission
        );
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(jwtProvider.getCollaborationExpirationSeconds());

        return new ResponseCollaborationTokenDto(token, expiresAt, permission);
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }
}
