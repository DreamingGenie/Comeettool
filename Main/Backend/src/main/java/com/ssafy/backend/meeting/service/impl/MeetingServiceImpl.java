package com.ssafy.backend.meeting.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.repository.ParticipantRepository;
import com.ssafy.backend.meeting.service.MeetingService;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회의 도메인 서비스.
 * MEET-06 호스트 양도와 MEET-07 현재 참여자 조회를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingServiceImpl implements MeetingService {

    private final MeetingRoomRepository meetingRoomRepository;
    private final ParticipantRepository participantRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

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

    /**
     * MEET-07: 현재 회의에 입장 중인 참여자의 기본 정보를 조회한다.
     *
     * 카메라·마이크 상태는 DB에 저장하지 않고 추후 LiveKit에서 실시간으로 결합한다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ResponseMeetingParticipantDto> getParticipants(
            Long requesterUserId,
            Long meetingId
    ) {
        // 1. 삭제되지 않은 회의인지 확인한다.
        MeetingRoom meetingRoom = meetingRoomRepository.findActiveById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        // 2. 요청자가 해당 회의의 상위 팀에 소속된 Member인지 확인한다.
        Member requesterMember = memberRepository
                .findByTeamIdAndUserId(meetingRoom.getTeamId(), requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        // 3. 팀 멤버이더라도 회의에 초대된 Participant가 아니면 조회를 거부한다.
        participantRepository
                .findByMeetingRoomIdAndMemberId(meetingId, requesterMember.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        // 4. 현재 회의에 입장 중인(isInMeeting=true) Participant만 조회한다.
        List<Participant> participants = participantRepository
                .findAllByMeetingRoomIdAndIsInMeetingTrueOrderByIdAsc(meetingId);

        // 5. Participant가 참조하는 Member를 일괄 조회해 N+1 쿼리를 방지한다.
        List<Long> memberIds = participants.stream()
                .map(Participant::getMemberId)
                .toList();

        Map<Long, Member> memberMap = memberRepository.findAllById(memberIds)
                .stream()
                .collect(Collectors.toMap(Member::getId, member -> member));

        // 6. 프로필 이미지 조회에 필요한 User도 한 번에 조회한다.
        List<Long> userIds = memberMap.values()
                .stream()
                .map(Member::getUserId)
                .toList();

        Map<Long, User> userMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 7. Participant·Member·User 데이터를 화면 응답 DTO로 조립한다.
        return participants.stream()
                .map(participant -> {
                    Member member = memberMap.get(participant.getMemberId());
                    if (member == null) {
                        throw new CustomException(ErrorCode.MEETING_PARTICIPANT_NOT_FOUND);
                    }

                    User user = userMap.get(member.getUserId());
                    if (user == null) {
                        throw new CustomException(ErrorCode.USER_NOT_FOUND);
                    }

                    return new ResponseMeetingParticipantDto(
                            participant.getId(),
                            member.getId(),
                            user.getId(),
                            member.getNickname(),
                            user.getProfileImageUrl(),
                            participant.getParticipantRole(),
                            meetingRoom.isHost(user.getId()),
                            participant.isInMeeting()
                    );
                })
                .toList();
    }
}
