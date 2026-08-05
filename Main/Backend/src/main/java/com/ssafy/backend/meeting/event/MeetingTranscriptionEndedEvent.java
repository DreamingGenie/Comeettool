package com.ssafy.backend.meeting.event;

public record MeetingTranscriptionEndedEvent(
        Long meetingId,
        String endedAt
) {
}
