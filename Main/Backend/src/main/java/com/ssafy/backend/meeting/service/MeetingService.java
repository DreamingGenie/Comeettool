package com.ssafy.backend.meeting.service;

import java.util.List;

import com.ssafy.backend.meeting.dto.RequestCreateMeetingDto;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseJoinMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseLeaveMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInvitationDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInviteCandidateDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingListDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;

public interface MeetingService {

    ResponseCreateMeetingDto addMeeting(
            Long requesterUserId,
            Long spaceId,
            RequestCreateMeetingDto request
    );

    ResponseJoinMeetingDto joinMeeting(
            Long requesterUserId,
            Long meetingId
    );

    List<ResponseMeetingListDto> getParticipatingMeetings(
            Long requesterUserId,
            Long spaceId
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

    List<ResponseMeetingInviteCandidateDto> getInviteCandidates(
            Long requesterUserId,
            Long meetingId,
            String query
    );

    ResponseLeaveMeetingDto leaveMeeting(
            Long requesterUserId,
            Long meetingId
    );

    void endMeeting(
            Long requesterUserId,
            Long meetingId
    );

    ResponseMeetingInvitationDto inviteMember(
            Long requesterUserId,
            Long meetingId,
            Long inviteeUserId
    );
}
