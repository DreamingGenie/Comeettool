package com.ssafy.backend.meeting.service;

import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;

public interface MeetingService {

    ResponseTransferHostDto transferHost(
            Long requesterUserId,
            Long meetingId,
            RequestTransferHostDto request
    );
}