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
 * participantRole은 BE·FE·서기처럼 화면에 표시하는 프로필 역할이며, 회의 권한과 무관하다.
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

    @Column(name = "participants_role", nullable = false, length = 50)
    private String participantRole;

    @Column(name = "is_host", nullable = false)
    private boolean host;

    @Builder
    private Participant(Long meetingRoomId, Long memberId, String participantRole, boolean host) {
        this.meetingRoomId = meetingRoomId;
        this.memberId = memberId;
        this.participantRole = participantRole;
        this.host = host;
    }

    public void grantHostAuthority() {
        this.host = true;
    }

    public void revokeHostAuthority() {
        this.host = false;
    }

    public void updateParticipantRole(String participantRole) {
        this.participantRole = participantRole;
    }
}
