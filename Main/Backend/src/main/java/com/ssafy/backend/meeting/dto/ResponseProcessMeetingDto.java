package com.ssafy.backend.meeting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI POST /meetings/{meetingId}/process 응답 (202 Accepted).
 */
public record ResponseProcessMeetingDto(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("meeting_id") Long meetingId,
        String status
) {
}
