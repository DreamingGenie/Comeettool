package com.ssafy.backend.document.controller;

import com.ssafy.backend.document.dto.ResponseCollaborationTokenDto;
import com.ssafy.backend.document.service.DocumentService;
import com.ssafy.backend.global.response.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/{documentId}/collaboration-token")
    public ResponseEntity<ApiResponse<ResponseCollaborationTokenDto>> issueCollaborationToken(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal String userId
    ) {
        ResponseCollaborationTokenDto response =
                documentService.issueCollaborationToken(documentId, userId);
        return ResponseEntity.ok(ApiResponse.success("협업 토큰 발급 성공", response));
    }
}
