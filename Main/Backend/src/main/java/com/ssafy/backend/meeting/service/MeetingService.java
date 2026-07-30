package com.ssafy.backend.meeting.service;

import java.util.List;

import com.ssafy.backend.meeting.dto.RequestCreateMeetingDto;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;

public interface MeetingService {

    ResponseCreateMeetingDto addMeeting(
            Long requesterUserId,
            Long spaceId,
            RequestCreateMeetingDto request
    );

    ResponseTransferHostDto transferHost(
            Long requesterUserId,
            Long meetingId,
            RequestTransferHostDto request
    );

    List<ResponseMeetingParticipantDto> getParticipants(
            Long requesterUserId,
            Long meetingId
    );
}
