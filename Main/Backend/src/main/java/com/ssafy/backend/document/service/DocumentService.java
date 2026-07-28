package com.ssafy.backend.document.service;

import com.ssafy.backend.document.dto.ResponseCollaborationTokenDto;

import java.util.UUID;

public interface DocumentService {

    ResponseCollaborationTokenDto issueCollaborationToken(UUID documentId, String userId);
}
