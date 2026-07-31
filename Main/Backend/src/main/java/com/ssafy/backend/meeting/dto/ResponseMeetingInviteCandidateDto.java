package com.ssafy.backend.meeting.dto;

/**
 * MEET-09 회의 초대 후보 검색 응답
 * 
 * meetingRoom이 속한 팀의 Member 중 아직 회의 Participant로 등록되지 않은
 * 멤버 정보를 반환한다.
 * 
 * memberId를 식별자로 사용한다.
 */

public record ResponseMeetingInviteCandidateDto(
        Long memberId,
        Long userId,
        String nickname,
        String email,
        String profileImage
) {
}