package com.ssafy.backend.meeting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * participants 테이블 매핑 엔티티.
 * participantRole은 BE·FE·서기처럼 화면에 표시하는 선택 프로필이며, 회의 권한과 무관하다.
 * Host 권한은 Participant에 중복 저장하지 않고 MeetingRoom.hostId에서만 관리한다.
 */
@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant {

    // DB가 IDENTITY로 채번하므로 엔티티에서 ID를 직접 지정하지 않는다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long id;

    @Column(name = "meeting_room_id", nullable = false)
    private Long meetingRoomId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "participants_role", length = 50)
    private String participantRole;

    @Column(name = "is_in_meeting", nullable = false)
    private boolean isInMeeting;

    @Builder
    private Participant(
            Long meetingRoomId,
            Long memberId,
            String participantRole,
            boolean isInMeeting
    ) {
        this.meetingRoomId = meetingRoomId;
        this.memberId = memberId;
        this.participantRole = participantRole;
        this.isInMeeting = isInMeeting;
    }

    public void updateParticipantRole(String participantRole) {
        this.participantRole = participantRole;
    }

    public void enterMeeting() {
        this.isInMeeting = true;
    }

    public void leaveMeeting() {
        this.isInMeeting = false;
    }
}
