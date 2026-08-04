package com.ssafy.backend.meeting.dto;

import java.time.OffsetDateTime;

public record VadSegmentMetadataDto(
        Long meetingRoomId,
        Long participantId,
        String username,
        Integer sequence,
        Long startedAt,
        Long endedAt,
        Long durationMs,
        String audioSha256,
        String audioObjectKey,
        OffsetDateTime uploadedAt
) {
}
