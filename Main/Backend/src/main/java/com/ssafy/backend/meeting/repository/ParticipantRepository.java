package com.ssafy.backend.meeting.repository;

import com.ssafy.backend.meeting.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // MEET-06: 양도 대상이 지정한 회의에 실제로 등록된 Participant인지 확인한다.
    Optional<Participant> findByIdAndMeetingRoomId(Long participantId, Long meetingRoomId);

    // MEET-07: 로그인한 팀원이 해당 회의에 초대된 Participant인지 확인한다.
    Optional<Participant> findByMeetingRoomIdAndMemberId(Long meetingRoomId, Long memberId);

    // MEET-07: 현재 회의에 입장 중인 Participant만 ID 순서대로 조회한다.
    List<Participant> findAllByMeetingRoomIdAndIsInMeetingTrueOrderByIdAsc(Long meetingRoomId);
}
