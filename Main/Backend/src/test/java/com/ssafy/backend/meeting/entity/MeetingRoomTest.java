package com.ssafy.backend.meeting.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MeetingRoom 엔티티 단위 테스트.
 * 기본 회의명 설정, hostId 기반 호스트 판별과 MEET-06 호스트 변경 규칙을 검증한다.
 */
@DisplayName("MeetingRoom 엔티티 테스트")
class MeetingRoomTest {

    @Test
    @DisplayName("회의명이 비어 있으면 기본 회의명을 사용한다")
    void create_usesDefaultNameWhenNameIsBlank() {
        MeetingRoom meetingRoom = MeetingRoom.builder()
                .teamId(10L)
                .hostId(1L)
                .name(" ")
                .build();

        assertThat(meetingRoom.getName()).isEqualTo("새 회의");
        assertThat(meetingRoom.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("hostId와 요청 userId가 같을 때만 호스트로 판별한다")
    void isHost_comparesUserIdWithHostId() {
        MeetingRoom meetingRoom = createMeetingRoom();

        assertThat(meetingRoom.isHost(1L)).isTrue();
        assertThat(meetingRoom.isHost(2L)).isFalse();
    }

    @Test
    @DisplayName("호스트를 양도하면 hostId가 새 호스트 userId로 변경된다")
    void transferHostTo_changesHostId() {
        MeetingRoom meetingRoom = createMeetingRoom();

        meetingRoom.transferHostTo(2L);

        assertThat(meetingRoom.getHostId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("새 호스트 userId가 null이면 변경할 수 없다")
    void transferHostTo_rejectsNullUserId() {
        MeetingRoom meetingRoom = createMeetingRoom();

        assertThatThrownBy(() -> meetingRoom.transferHostTo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("새 호스트 ID는 필수입니다.");
    }

    private MeetingRoom createMeetingRoom() {
        return MeetingRoom.builder()
                .teamId(10L)
                .hostId(1L)
                .name("데일리 미팅")
                .build();
    }
}
