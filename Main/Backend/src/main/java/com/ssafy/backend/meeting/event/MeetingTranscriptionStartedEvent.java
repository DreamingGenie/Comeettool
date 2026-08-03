package com.ssafy.backend.meeting.event;

public record MeetingTranscriptionStartedEvent(
        Long meetingId,
        String startedAt
) {
}
