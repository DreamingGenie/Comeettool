package com.ssafy.backend.meeting.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MeetingRoom 엔티티의 생성 및 Host 변경 동작을 검증하는 순수 단위 테스트.
 *
 * <p>검증 내용:</p>
 * <ul>
 *     <li>회의 이름을 입력하지 않았을 때 기본 이름인 "새 회의"가 설정되는지 확인한다.</li>
 *     <li>meeting_rooms.host_id를 기준으로 현재 Host를 정확히 판별하는지 확인한다.</li>
 *     <li>MEET-06 호스트 양도 시 hostId가 다음 Host로 정상 변경되는지 확인한다.</li>
 *     <li>다음 Host ID가 null이면 변경을 거부하고 기존 Host를 유지하는지 확인한다.</li>
 * </ul>
 *
 * <p>Spring Context와 DB를 실행하지 않고 MeetingRoom 객체만 생성하여 도메인 동작을 검증한다.</p>
 */
@DisplayName("MeetingRoom 엔티티 단위 테스트")
class MeetingRoomTest {

    private static final Long TEAM_ID = 1L;
    private static final Long HOST_ID = 10L;

    @Test
    @DisplayName("회의 이름이 없으면 기본 이름을 사용한다")
    void 회의_이름이_없으면_기본_이름을_사용한다() {
        MeetingRoom meetingRoom = MeetingRoom.builder()
                .teamId(TEAM_ID)
                .hostId(HOST_ID)
                .build();

        assertThat(meetingRoom.getName()).isEqualTo("새 회의");
        assertThat(meetingRoom.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("hostId를 기준으로 현재 호스트를 판별한다")
    void hostId를_기준으로_현재_호스트를_판별한다() {
        MeetingRoom meetingRoom = createMeetingRoom();

        assertThat(meetingRoom.isHost(HOST_ID)).isTrue();
        assertThat(meetingRoom.isHost(20L)).isFalse();
    }

    @Test
    @DisplayName("다음 호스트로 정상적으로 변경한다")
    void 다음_호스트로_정상적으로_변경한다() {
        MeetingRoom meetingRoom = createMeetingRoom();
        Long nextHostId = 20L;

        meetingRoom.changeHost(nextHostId);

        assertThat(meetingRoom.getHostId()).isEqualTo(nextHostId);
        assertThat(meetingRoom.isHost(nextHostId)).isTrue();
        assertThat(meetingRoom.isHost(HOST_ID)).isFalse();
    }

    @Test
    @DisplayName("다음 호스트 ID가 null이면 변경을 거부한다")
    void 다음_호스트_ID가_null이면_변경을_거부한다() {
        MeetingRoom meetingRoom = createMeetingRoom();

        assertThatThrownBy(() -> meetingRoom.changeHost(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("다음 호스트 ID는 필수입니다.");
        assertThat(meetingRoom.getHostId()).isEqualTo(HOST_ID);
    }

    private MeetingRoom createMeetingRoom() {
        return MeetingRoom.builder()
                .teamId(TEAM_ID)
                .hostId(HOST_ID)
                .name("주간 회의")
                .build();
    }
}
