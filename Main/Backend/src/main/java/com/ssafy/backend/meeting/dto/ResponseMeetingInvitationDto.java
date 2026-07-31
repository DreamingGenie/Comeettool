package com.ssafy.backend.meeting.dto;

/**
 * MEET-10 회의 초대 응답.
 *
 * Participant 생성 결과를 반환하며, 초대 직후에는 아직 LiveKit에 접속하지 않았으므로
 * isInMeeting은 false이다.
 */
public record ResponseMeetingInvitationDto(
        Long participantId,
        Long meetingRoomId,
        Long memberId,
        Long userId,
        String participantRole,
        boolean isInMeeting
) {
}
