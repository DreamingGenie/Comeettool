package com.ssafy.backend.meeting.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.repository.ParticipantRepository;
import com.ssafy.backend.meeting.service.MeetingService;
import com.ssafy.backend.space.entity.Member;
import com.ssafy.backend.space.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * MEET-06 회의 호스트 양도.
 * 호스트 권한은 MeetingRoom.hostId만을 기준으로 판단하고 변경한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingServiceImpl implements MeetingService {

    private final MeetingRoomRepository meetingRoomRepository;
    private final ParticipantRepository participantRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public ResponseTransferHostDto transferHost(
            Long requesterUserId,
            Long meetingId,
            RequestTransferHostDto request
    ) {
        MeetingRoom meetingRoom = meetingRoomRepository.findActiveByIdForUpdate(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        if (!meetingRoom.isHost(requesterUserId)) {
            throw new CustomException(ErrorCode.MEETING_HOST_REQUIRED);
        }

        var nextHostParticipant = participantRepository
                .findByIdAndMeetingRoomId(request.nextHostParticipantId(), meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_PARTICIPANT_NOT_FOUND));

        Member nextHostMember = memberRepository.findById(nextHostParticipant.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_PARTICIPANT_NOT_FOUND));

        if (!meetingRoom.getTeamId().equals(nextHostMember.getTeamId())) {
            throw new CustomException(ErrorCode.MEETING_PARTICIPANT_NOT_FOUND);
        }

        Long previousHostId = meetingRoom.getHostId();
        Long nextHostId = nextHostMember.getUserId();
        if (previousHostId.equals(nextHostId)) {
            throw new CustomException(ErrorCode.MEETING_HOST_ALREADY_ASSIGNED);
        }

        meetingRoom.transferHostTo(nextHostId);

        return new ResponseTransferHostDto(meetingId, previousHostId, nextHostId);
    }
}
