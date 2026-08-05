package com.ssafy.backend.meeting.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Participant 엔티티 단위 테스트.
 * SQL v1.1.7에 맞춰 선택 프로필 역할과 BOOLEAN 접속 상태가 독립적으로 변경됨을 검증한다.
 */
@DisplayName("Participant 엔티티 테스트")
class ParticipantTest {

    @Test
    @DisplayName("회의 참여자 정보를 생성한다")
    void create_setsParticipantProfile() {
        Participant participant = Participant.builder()
                .meetingRoomId(100L)
                .memberId(20L)
                .participantRole("BE")
                .build();

        assertThat(participant.getMeetingRoomId()).isEqualTo(100L);
        assertThat(participant.getMemberId()).isEqualTo(20L);
        assertThat(participant.getParticipantRole()).isEqualTo("BE");
        assertThat(participant.isInMeeting()).isFalse();
    }

    @Test
    @DisplayName("프로필 역할을 변경해도 회의 식별 정보는 유지된다")
    void updateParticipantRole_changesOnlyProfileRole() {
        Participant participant = Participant.builder()
                .meetingRoomId(100L)
                .memberId(20L)
                .participantRole("FE")
                .build();

        participant.updateParticipantRole("서기");

        assertThat(participant.getParticipantRole()).isEqualTo("서기");
        assertThat(participant.getMeetingRoomId()).isEqualTo(100L);
        assertThat(participant.getMemberId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("프로필 역할은 지정하지 않아도 참여자를 생성할 수 있다")
    void create_allowsNullParticipantRole() {
        Participant participant = Participant.builder()
                .meetingRoomId(100L)
                .memberId(20L)
                .participantRole(null)
                .build();

        assertThat(participant.getParticipantRole()).isNull();
    }

    @Test
    @DisplayName("입장과 퇴장 상태를 BOOLEAN 값으로 변경한다")
    void updateMeetingPresence_changesBooleanState() {
        Participant participant = Participant.builder()
                .meetingRoomId(100L)
                .memberId(20L)
                .participantRole(null)
                .build();

        participant.enterMeeting();
        assertThat(participant.isInMeeting()).isTrue();

        participant.leaveMeeting();
        assertThat(participant.isInMeeting()).isFalse();
    }
}
