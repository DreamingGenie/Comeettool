package com.ssafy.backend.meeting.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LiveKit 이름 생성기 테스트")
class LiveKitNameGeneratorTest {

    private final LiveKitNameGenerator liveKitNameGenerator = new LiveKitNameGenerator();

    @Test
    @DisplayName("회의방 ID로 고정된 LiveKit 방 이름을 생성한다")
    void generateMeetingRoomName_returnsStableName() {
        assertThat(liveKitNameGenerator.generateMeetingRoomName(100L))
                .isEqualTo("meeting-100");
    }

    @Test
    @DisplayName("Participant ID로 고정된 LiveKit identity를 생성한다")
    void generateParticipantIdentity_returnsStableIdentity() {
        assertThat(liveKitNameGenerator.generateParticipantIdentity(30L))
                .isEqualTo("participant-30");
    }

    @Test
    @DisplayName("ID가 없으면 LiveKit 이름 생성을 거부한다")
    void generateName_rejectsNullIdentifier() {
        assertThatNullPointerException()
                .isThrownBy(() -> liveKitNameGenerator.generateMeetingRoomName(null));
        assertThatNullPointerException()
                .isThrownBy(() -> liveKitNameGenerator.generateParticipantIdentity(null));
    }
}
