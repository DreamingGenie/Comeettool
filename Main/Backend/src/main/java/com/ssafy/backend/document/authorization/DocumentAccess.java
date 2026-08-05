package com.ssafy.backend.document.authorization;

public record DocumentAccess(
        Long teamId,
        CollaborationPermission permission
) {
}
