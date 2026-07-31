package com.ssafy.backend.meeting.dto;

import java.time.OffsetDateTime;

/**
 * MEET-02 참가 중인 회의 목록의 개별 항목.
 *
 * isInMeeting은 현재 접속 상태이며, false여도 Participant로 등록되어 있으면
 * 일반 퇴장 후 다시 입장할 수 있으므로 목록에 포함한다.
 */
public record ResponseMeetingListDto(
        Long meetingRoomId,
        Long teamId,
        String meetingRoomName,
        ResponseMeetingHostDto host,
        OffsetDateTime createdAt,
        long participantCount,
        boolean isInMeeting
) {
}
