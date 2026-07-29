package com.ssafy.backend.meeting.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Participant 엔티티의 회의 참여 정보와 프로필 역할 변경 동작을 검증하는 순수 단위 테스트.
 *
 * <p>검증 내용:</p>
 * <ul>
 *     <li>회의 ID, 멤버 ID와 BE·FE 등의 프로필 역할이 정상적으로 생성되는지 확인한다.</li>
 *     <li>participantRole을 변경하면 회의·멤버 식별자는 유지되고 프로필 역할만 변경되는지 확인한다.</li>
 * </ul>
 *
 * <p>Host 권한은 Participant가 아니라 MeetingRoom.hostId에서만 관리한다.
 * Spring Context와 DB를 실행하지 않고 Participant 객체만 생성하여 도메인 동작을 검증한다.</p>
 */
@DisplayName("Participant 엔티티 단위 테스트")
class ParticipantTest {

    private static final Long MEETING_ROOM_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    @Test
    @DisplayName("참가자의 회의·멤버 식별자와 프로필 역할을 생성한다")
    void 참가자의_회의_멤버_식별자와_프로필_역할을_생성한다() {
        Participant participant = createParticipant("BE");

        assertThat(participant.getMeetingRoomId()).isEqualTo(MEETING_ROOM_ID);
        assertThat(participant.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(participant.getParticipantRole()).isEqualTo("BE");
    }

    @Test
    @DisplayName("프로필 역할을 변경해도 회의와 멤버 식별자는 유지한다")
    void 프로필_역할을_변경해도_회의와_멤버_식별자는_유지한다() {
        Participant participant = createParticipant("BE");

        participant.updateParticipantRole("서기");

        assertThat(participant.getParticipantRole()).isEqualTo("서기");
        assertThat(participant.getMeetingRoomId()).isEqualTo(MEETING_ROOM_ID);
        assertThat(participant.getMemberId()).isEqualTo(MEMBER_ID);
    }

    private Participant createParticipant(String participantRole) {
        return Participant.builder()
                .meetingRoomId(MEETING_ROOM_ID)
                .memberId(MEMBER_ID)
                .participantRole(participantRole)
                .build();
    }
}
