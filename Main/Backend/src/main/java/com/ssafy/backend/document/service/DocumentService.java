package com.ssafy.backend.document.service;

import com.ssafy.backend.document.dto.RequestCreateDocumentDto;
import com.ssafy.backend.document.dto.ResponseCollaborationTokenDto;

import com.ssafy.backend.document.dto.ResponseDocumentDetailDto;
import com.ssafy.backend.document.dto.ResponseDocumentSummaryDto;
import java.util.List;
import java.util.UUID;

public interface DocumentService {

    List<ResponseDocumentSummaryDto> findDocumentList(Long teamId, String userId);

    ResponseDocumentDetailDto findDocumentDetails(UUID documentId, String userId);

    ResponseCollaborationTokenDto issueCollaborationToken(UUID documentId, String userId);

    ResponseDocumentDetailDto addDocument(RequestCreateDocumentDto request, String userId);

    void deleteDocument(UUID documentId, String userId);
}
