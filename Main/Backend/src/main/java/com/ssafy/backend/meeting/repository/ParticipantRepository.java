package com.ssafy.backend.meeting.repository;

import com.ssafy.backend.meeting.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // MEET-06: 양도 대상이 지정한 회의에 실제로 등록된 Participant인지 확인한다.
    Optional<Participant> findByIdAndMeetingRoomId(Long participantId, Long meetingRoomId);
}
