package com.ssafy.backend.meeting.dto;

import java.time.OffsetDateTime;

public record ResponseVadRecordingDto(
        Long meetingRoomId,
        Long participantId,
        Integer sequence,
        String audioObjectKey,
        String metadataObjectKey,
        Long durationMs,
        OffsetDateTime uploadedAt,
        String username
) {
}
