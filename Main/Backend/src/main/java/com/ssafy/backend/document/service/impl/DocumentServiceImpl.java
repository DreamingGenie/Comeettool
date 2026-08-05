package com.ssafy.backend.document.service.impl;

import com.ssafy.backend.document.authorization.CollaborationPermission;
import com.ssafy.backend.document.authorization.DocumentAccess;
import com.ssafy.backend.document.authorization.DocumentAuthorizationService;
import com.ssafy.backend.document.authorization.DocumentPermissionReader;
import com.ssafy.backend.document.client.DocumentClient;
import com.ssafy.backend.document.dto.RequestCreateDocumentDto;
import com.ssafy.backend.document.dto.ResponseCollaborationTokenDto;
import com.ssafy.backend.document.dto.ResponseDocumentDetailDto;
import com.ssafy.backend.document.dto.ResponseDocumentSummaryDto;
import com.ssafy.backend.document.entity.Document;
import com.ssafy.backend.document.mapper.DocumentMapper;
import com.ssafy.backend.document.repository.DocumentRepository;
import com.ssafy.backend.document.service.DocumentService;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.jwt.JwtProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentClient documentClient;
    private final DocumentRepository documentRepository;
    private final DocumentAuthorizationService documentAuthorizationService;
    private final DocumentMapper documentMapper;
    private final DocumentPermissionReader documentPermissionReader;
    private final JwtProvider jwtProvider;

    @Override
    public List<ResponseDocumentSummaryDto> findDocumentList(Long teamId, String userId) {
        Long parsedUserId = parseUserId(userId);
        documentAuthorizationService.requireTeamMember(teamId, parsedUserId);

        return documentRepository
                .findAllByTeamIdAndIsDeletedFalseOrderByUpdatedAtDesc(teamId)
                .stream()
                .map(documentMapper::toSummary)
                .toList();
    }

    @Override
    public ResponseDocumentDetailDto findDocumentDetails(UUID documentId, String userId) {
        Long parsedUserId = parseUserId(userId);
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        documentAuthorizationService.requireTeamMember(document.getTeamId(), parsedUserId);
        return documentMapper.toDetail(document);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseCollaborationTokenDto issueCollaborationToken(UUID documentId, String userId) {
        Long parsedUserId = parseUserId(userId);
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
        CollaborationPermission collaborationPermission =
                documentAuthorizationService.requireCollaborationPermission(
                        document.getTeamId(),
                        parsedUserId
                );

        String permission = collaborationPermission.name();
        String token = jwtProvider.createCollaborationToken(
                userId,
                documentId,
                document.getTeamId(),
                permission
        );
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(jwtProvider.getCollaborationExpirationSeconds());

        return new ResponseCollaborationTokenDto(token, expiresAt, permission);
    }

    @Override
    public ResponseDocumentDetailDto addDocument(RequestCreateDocumentDto request, String userId) {
        Long parsedUserId = parseUserId(userId);
        documentAuthorizationService.requireCreatePermission(request.teamId(), parsedUserId);
        return documentClient.addDocument(request.teamId());
    }

    @Override
    public void deleteDocument(UUID documentId, String userId) {
        Long parsedUserId = parseUserId(userId);
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        documentAuthorizationService.requireDeletePermission(
                document.getTeamId(),
                parsedUserId
        );
        documentClient.deleteDocument(documentId);
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }
}
