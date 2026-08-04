package com.ssafy.backend.meeting.dto;

public record ResponseEndTranscriptionDto(
        String code,
        String message,
        Long meetingRoomId
) {
}
