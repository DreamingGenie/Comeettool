package com.ssafy.backend.meeting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.backend.meeting.entity.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // MEET-02: 로그인 사용자의 Member가 참가 권한을 가진 모든 회의를 찾는다.
    List<Participant> findAllByMemberIdOrderByIdAsc(Long memberId);

    // MEET-02: 조회 대상 회의별 현재 접속자 수를 한 번에 집계한다.
    @Query("""
            select p.meetingRoomId, count(p)
            from Participant p
            where p.meetingRoomId in :meetingRoomIds
              and p.isInMeeting = true
            group by p.meetingRoomId
            """)
    List<Object[]> countInMeetingParticipantsByMeetingRoomIds(
            @Param("meetingRoomIds") List<Long> meetingRoomIds
    );

    // MEET-06: 양도 대상이 지정한 회의에 실제로 등록된 Participant인지 확인한다.
    Optional<Participant> findByIdAndMeetingRoomId(Long participantId, Long meetingRoomId);

    // MEET-07: 로그인한 팀원이 해당 회의에 초대된 Participant인지 확인한다.
    Optional<Participant> findByMeetingRoomIdAndMemberId(Long meetingRoomId, Long memberId);

    // MEET-07: 현재 회의에 입장 중인 Participant만 ID 순서대로 조회한다.
    List<Participant> findAllByMeetingRoomIdAndIsInMeetingTrueOrderByIdAsc(Long meetingRoomId);
}
