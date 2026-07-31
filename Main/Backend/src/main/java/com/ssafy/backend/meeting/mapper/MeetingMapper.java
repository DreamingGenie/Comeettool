package com.ssafy.backend.meeting.mapper;

import org.springframework.stereotype.Component;

import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInviteCandidateDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingListDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.user.entity.User;

@Component
public class MeetingMapper {

    public ResponseCreateMeetingDto toCreateResponse(
            MeetingRoom meetingRoom,
            Long hostUserId,
            int participantCount
    ) {
        return new ResponseCreateMeetingDto(
                meetingRoom.getId(),
                meetingRoom.getTeamId(),
                new ResponseMeetingHostDto(hostUserId),
                meetingRoom.getCreatedAt(),
                participantCount
        );
    }

    public ResponseMeetingListDto toListItem(
            MeetingRoom meetingRoom,
            long participantCount,
            boolean isInMeeting
    ) {
        return new ResponseMeetingListDto(
                meetingRoom.getId(),
                meetingRoom.getTeamId(),
                meetingRoom.getName(),
                new ResponseMeetingHostDto(meetingRoom.getHostId()),
                meetingRoom.getCreatedAt(),
                participantCount,
                isInMeeting
        );
    }

    public ResponseMeetingInviteCandidateDto toInviteCandidate(
            Member member,
            User user
    ) {
        return new ResponseMeetingInviteCandidateDto(
                member.getId(),
                user.getId(),
                member.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl()
        );
    }
}
