package com.ssafy.backend.meeting.service;

import java.util.List;

import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;

public interface MeetingService {

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
