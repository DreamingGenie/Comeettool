package com.ssafy.backend.meeting.dto;

import java.time.OffsetDateTime;

public record ResponseCreateMeetingDto(

        Long meetingRoomId,
        Long teamId,
        ResponseMeetingHostDto host,
        OffsetDateTime createdAt,
        int participantCount

) {
}
