package com.ssafy.backend.meeting.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Participant 엔티티의 프로필 역할 및 Host 표시값 변경 동작을 검증하는 순수 단위 테스트.
 *
 * <p>검증 내용:</p>
 * <ul>
 *     <li>회의 ID, 멤버 ID, 프로필 역할과 Host 표시값이 정상적으로 생성되는지 확인한다.</li>
 *     <li>Host 표시값을 변경해도 BE·FE 등의 participantRole이 유지되는지 확인한다.</li>
 *     <li>participantRole을 변경해도 Host 표시값이 영향을 받지 않는지 확인한다.</li>
 * </ul>
 *
 * <p>participantRole은 사용자 프로필 정보이고 Host 권한과 다른 개념임을 테스트로 보장한다.
 * Spring Context와 DB를 실행하지 않고 Participant 객체만 생성하여 도메인 동작을 검증한다.</p>
 */
@DisplayName("Participant 엔티티 단위 테스트")
class ParticipantTest {

    private static final Long MEETING_ROOM_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    @Test
    @DisplayName("참가자의 프로필 역할과 Host 표시값을 생성한다")
    void 참가자의_프로필_역할과_Host_표시값을_생성한다() {
        Participant participant = createParticipant("BE", false);

        assertThat(participant.getMeetingRoomId()).isEqualTo(MEETING_ROOM_ID);
        assertThat(participant.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(participant.getParticipantRole()).isEqualTo("BE");
        assertThat(participant.isHost()).isFalse();
    }

    @Test
    @DisplayName("Host 권한 표시를 변경해도 프로필 역할은 유지한다")
    void Host_권한_표시를_변경해도_프로필_역할은_유지한다() {
        Participant participant = createParticipant("FE", false);

        participant.grantHostAuthority();

        assertThat(participant.isHost()).isTrue();
        assertThat(participant.getParticipantRole()).isEqualTo("FE");

        participant.revokeHostAuthority();

        assertThat(participant.isHost()).isFalse();
        assertThat(participant.getParticipantRole()).isEqualTo("FE");
    }

    @Test
    @DisplayName("프로필 역할을 변경해도 Host 표시값은 유지한다")
    void 프로필_역할을_변경해도_Host_표시값은_유지한다() {
        Participant participant = createParticipant("BE", true);

        participant.updateParticipantRole("서기");

        assertThat(participant.getParticipantRole()).isEqualTo("서기");
        assertThat(participant.isHost()).isTrue();
    }

    private Participant createParticipant(String participantRole, boolean host) {
        return Participant.builder()
                .meetingRoomId(MEETING_ROOM_ID)
                .memberId(MEMBER_ID)
                .participantRole(participantRole)
                .host(host)
                .build();
    }
}
