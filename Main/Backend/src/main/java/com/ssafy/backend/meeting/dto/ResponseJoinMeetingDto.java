package com.ssafy.backend.meeting.dto;

public record ResponseJoinMeetingDto(
        Long meetingRoomId,
        String role,
        boolean isHost,
        String token,
        String url
) {
}
