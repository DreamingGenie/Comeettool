package com.ssafy.backend.meeting.dto;

public record ResponseJoinMeetingDto(
        Long meetingRoomId,
        Long teamId,
        String role,
        boolean isHost,
        String token,
        String url
) {
}
