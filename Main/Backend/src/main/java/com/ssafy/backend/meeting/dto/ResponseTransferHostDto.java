package com.ssafy.backend.meeting.dto;

/**
 * MEET-06 호스트 양도 성공 응답.
 * previousHostId와 nextHostId는 meeting_rooms.host_id에 저장하는 users.user_id다.
 */
public record ResponseTransferHostDto(
        Long meetingId,
        Long previousHostId,
        Long nextHostId
) {
}
