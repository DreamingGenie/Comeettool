package com.ssafy.backend.meeting.dto;

public record ResponseStartTranscriptionDto(
        String code,
        String message,
        Long meetingRoomId,
        String s3Prefix
) {
}
