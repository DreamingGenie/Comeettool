package com.ssafy.backend.meeting.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.RequestCreateMeetingDto;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseJoinMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseLeaveMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInviteCandidateDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingListDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.livekit.LiveKitConnectionInfo;
import com.ssafy.backend.meeting.livekit.LiveKitParticipantManager;
import com.ssafy.backend.meeting.livekit.LiveKitTokenProvider;
import com.ssafy.backend.meeting.mapper.MeetingMapper;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.repository.ParticipantRepository;
import com.ssafy.backend.meeting.service.MeetingService;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회의 도메인 서비스.
 * MEET-01 생성, MEET-02 목록, MEET-03 RTC 입장,
 * MEET-06 호스트 양도, MEET-07 현재 참여자 조회,
 * MEET-09 초대 후보 검색을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingServiceImpl implements MeetingService {

    private static final long MAX_ACTIVE_MEETING_ROOM_COUNT = 3L;
    private static final int INITIAL_PARTICIPANT_COUNT = 1;

    private final MeetingRoomRepository meetingRoomRepository;
    private final ParticipantRepository participantRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final MeetingMapper meetingMapper;
    private final LiveKitTokenProvider liveKitTokenProvider;
    private final LiveKitParticipantManager liveKitParticipantManager;

    /**
     * MEET-01: 회의 생성.
     */
    @Override
    @Transactional
    public ResponseCreateMeetingDto addMeeting(
            Long requesterUserId,
            Long spaceId,
            RequestCreateMeetingDto request
    ) {
        teamRepository.findActiveByIdForUpdate(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        Member hostMember = memberRepository.findByTeamIdAndUserId(spaceId, requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_ACCESS_DENIED));
        if (hostMember.getAuthority() == MemberAuthority.GUEST) {
            throw new CustomException(ErrorCode.MEETING_CREATE_FORBIDDEN);
        }

        long activeMeetingRoomCount =
                meetingRoomRepository.countByTeamIdAndIsDeletedFalse(spaceId);
        if (activeMeetingRoomCount >= MAX_ACTIVE_MEETING_ROOM_COUNT) {
            throw new CustomException(ErrorCode.MEETING_ROOM_LIMIT_EXCEEDED);
        }

        MeetingRoom meetingRoom = MeetingRoom.builder()
                .teamId(spaceId)
                .hostId(requesterUserId)
                .name(request.meetingRoomName())
                .build();
        MeetingRoom savedMeetingRoom = meetingRoomRepository.save(meetingRoom);

        String participantRole = memberRepository
                .findTeamRoleNameByMemberId(hostMember.getId())
                .orElse(null);
        Participant hostParticipant = Participant.builder()
                .meetingRoomId(savedMeetingRoom.getId())
                .memberId(hostMember.getId())
                .participantRole(participantRole)
                .isInMeeting(false)
                .build();
        participantRepository.save(hostParticipant);

        return meetingMapper.toCreateResponse(
                savedMeetingRoom,
                requesterUserId,
                INITIAL_PARTICIPANT_COUNT
        );
    }

    /**
     * MEET-02: 로그인 사용자가 참가 권한을 가진 진행 중인 회의 목록 조회.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ResponseMeetingListDto> getParticipatingMeetings(
            Long requesterUserId,
            Long spaceId
    ) {
        // 1. 삭제되지 않은 스페이스인지 확인한다.
        teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        // 2. 로그인 사용자의 스페이스 Member를 조회하며 접근 권한을 확인한다.
        Member requesterMember = memberRepository
                .findByTeamIdAndUserId(spaceId, requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_ACCESS_DENIED));

        // 3. Member가 참가 권한을 가진 Participant를 조회한다.
        // 일반 퇴장 후 재입장을 허용하므로 isInMeeting 값으로 여기서 제외하지 않는다.
        List<Participant> myParticipants = participantRepository
                .findAllByMemberIdOrderByIdAsc(requesterMember.getId());
        if (myParticipants.isEmpty()) {
            return List.of();
        }

        Map<Long, Boolean> myConnectionStateByMeetingRoomId = myParticipants.stream()
                .collect(Collectors.toMap(
                        Participant::getMeetingRoomId,
                        Participant::isInMeeting,
                        (first, second) -> first || second
                ));

        // 4. Participant가 참조하는 회의 중 같은 스페이스의 삭제되지 않은 회의만 조회한다.
        List<MeetingRoom> meetingRooms = meetingRoomRepository.findAllActiveByIdsAndTeamId(
                List.copyOf(myConnectionStateByMeetingRoomId.keySet()),
                spaceId
        );
        if (meetingRooms.isEmpty()) {
            return List.of();
        }

        // 5. 각 회의의 현재 접속자 수를 일괄 집계해 N+1 쿼리를 방지한다.
        List<Long> activeMeetingRoomIds = meetingRooms.stream()
                .map(MeetingRoom::getId)
                .toList();

        Map<Long, Long> participantCountByMeetingRoomId = new HashMap<>();
        for (Object[] row : participantRepository
                .countInMeetingParticipantsByMeetingRoomIds(activeMeetingRoomIds)) {
            participantCountByMeetingRoomId.put((Long) row[0], (Long) row[1]);
        }

        // 6. 회의 기본 정보, 현재 접속자 수, 내 접속 상태를 목록 응답으로 조립한다.
        return meetingRooms.stream()
                .map(meetingRoom -> meetingMapper.toListItem(
                        meetingRoom,
                        participantCountByMeetingRoomId.getOrDefault(meetingRoom.getId(), 0L),
                        myConnectionStateByMeetingRoomId.getOrDefault(
                                meetingRoom.getId(),
                                false
                        )
                ))
                .toList();
    }

    /**
     * MEET-03: 초대된 참여자의 LiveKit 회의 입장 정보를 발급한다.
     */
    @Override
    @Transactional
    public ResponseJoinMeetingDto joinMeeting(
            Long requesterUserId,
            Long meetingId
    ) {
        MeetingRoom meetingRoom = meetingRoomRepository.findActiveById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        Member member = memberRepository
                .findByTeamIdAndUserId(meetingRoom.getTeamId(), requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        Participant participant = participantRepository
                .findByMeetingRoomIdAndMemberId(meetingId, member.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        LiveKitConnectionInfo connectionInfo = liveKitTokenProvider.generateJoinToken(
                meetingRoom.getId(),
                participant.getId(),
                member.getNickname()
        );
        participant.enterMeeting();

        return new ResponseJoinMeetingDto(
                meetingRoom.getId(),
                participant.getParticipantRole(),
                meetingRoom.isHost(requesterUserId),
                connectionInfo.token(),
                connectionInfo.url()
        );
    }

    /**
     * MEET-04: 현재 사용자를 회의에서 퇴장시키고 LiveKit 연결을 종료한다.
     */
    @Override
    @Transactional
    public ResponseLeaveMeetingDto leaveMeeting(
            Long requesterUserId,
            Long meetingId
    ) {
        MeetingRoom meetingRoom = meetingRoomRepository.findActiveById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        Member member = memberRepository
                .findByTeamIdAndUserId(meetingRoom.getTeamId(), requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        Participant participant = participantRepository
                .findByMeetingRoomIdAndMemberId(meetingId, member.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        if (meetingRoom.isHost(requesterUserId)) {
            throw new CustomException(ErrorCode.MEETING_HOST_CANNOT_LEAVE);
        }

        liveKitParticipantManager.disconnectParticipant(
                meetingRoom.getId(),
                participant.getId()
        );

        participant.leaveMeeting();

        return new ResponseLeaveMeetingDto(false);
    }

    /**
     * MEET-06: 현재 호스트가 회의 참여자에게 호스트 권한을 양도한다.
     */
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

        Participant nextHostParticipant = participantRepository
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

    /**
     * MEET-09: 호스트가 회의에 초대할 수 있는 팀 멤버를 조회한다.
     *
     * 검색어가 없으면 초대 가능한 전체 팀 멤버를 반환한다.
     * 이미 Participant로 등록된 멤버는 조회 대상에서 제외한다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ResponseMeetingInviteCandidateDto> getInviteCandidates(
            Long requesterUserId,
            Long meetingId,
            String query
    ) {
        // 1. 삭제되지 않은 회의인지 확인한다.
        MeetingRoom meetingRoom = meetingRoomRepository.findActiveById(meetingId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.MEETING_NOT_FOUND));

        // 2. 요청자가 해당 회의의 호스트인지 확인한다.
        if (!meetingRoom.isHost(requesterUserId)) {
            throw new CustomException(ErrorCode.MEETING_HOST_REQUIRED);
        }

        // 3. 검색어가 없으면 전체 후보가 조회되도록 빈 문자열로 정규화한다.
        String normalizedQuery =
                query == null || query.isBlank()
                        ? ""
                        : query.trim();

        // 4. 팀 멤버와 사용자 정보를 조회하여 응답 DTO로 변환한다.
        return memberRepository.findMeetingInviteCandidates(
                        meetingRoom.getTeamId(),
                        meetingId,
                        normalizedQuery
                )
                .stream()
                .map(row -> meetingMapper.toInviteCandidate(
                        (Member) row[0],
                        (User) row[1]
                ))
                .toList();
    }
}
