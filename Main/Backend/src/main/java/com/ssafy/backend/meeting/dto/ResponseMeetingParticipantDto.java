package com.ssafy.backend.meeting.dto;

/**
 * MEET-07 회의 참여자 조회 응답.
 *
 * participantRole은 BE, FE처럼 화면에 표시되는 프로필이며 회의 권한과 무관하다.
 * isHost는 별도 저장 값이 아니라 MeetingRoom.hostId와 Member.userId를 비교한 결과다.
 */

public record ResponseMeetingParticipantDto(
        Long participantId,
        Long memberId,
        Long userId,
        String nickname,
        String profileImageUrl,
        String participantRole,
        boolean isHost,
        boolean isInMeeting
) {
}
