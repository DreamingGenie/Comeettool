package com.ssafy.backend.meeting.mapper;

import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingListDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import org.springframework.stereotype.Component;

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
}
