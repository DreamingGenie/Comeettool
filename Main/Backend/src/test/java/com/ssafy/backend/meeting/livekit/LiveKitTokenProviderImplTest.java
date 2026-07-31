package com.ssafy.backend.meeting.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.meeting.livekit.config.LiveKitConfiguration;
import com.ssafy.backend.meeting.livekit.config.LiveKitProperties;

@DisplayName("LiveKit 입장 토큰 발급 테스트")
class LiveKitTokenProviderImplTest {

    private static final String LIVEKIT_URL = "wss://test.livekit.cloud";
    private static final String API_KEY = "test-api-key";
    private static final String API_SECRET = "test-api-secret-value";

    private final LiveKitProperties liveKitProperties = new LiveKitProperties(
            LIVEKIT_URL,
            API_KEY,
            API_SECRET,
            Duration.ofHours(1)
    );
    private final LiveKitTokenProvider liveKitTokenProvider =
            new LiveKitTokenProviderImpl(
                    liveKitProperties,
                    new LiveKitNameGenerator()
            );

    @Test
    @DisplayName("회의방과 Participant 식별자를 포함한 입장 토큰을 발급한다")
    void generateJoinToken_returnsConnectionInfoWithExpectedClaims() throws Exception {
        LiveKitConnectionInfo connectionInfo =
                liveKitTokenProvider.generateJoinToken(100L, 30L, "참여자");

        String encodedPayload = connectionInfo.token().split("\\.")[1];
        String payload = new String(
                Base64.getUrlDecoder().decode(encodedPayload),
                StandardCharsets.UTF_8
        );
        Map<String, Object> claims = new ObjectMapper().readValue(
                payload,
                new TypeReference<>() {
                }
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> videoGrant = (Map<String, Object>) claims.get("video");

        assertThat(connectionInfo.url()).isEqualTo(LIVEKIT_URL);
        assertThat(connectionInfo.roomName()).isEqualTo("meeting-100");
        assertThat(connectionInfo.participantIdentity()).isEqualTo("participant-30");
        assertThat(claims.get("iss")).isEqualTo(API_KEY);
        assertThat(claims.get("sub")).isEqualTo("participant-30");
        assertThat(claims.get("name")).isEqualTo("참여자");
        assertThat(videoGrant)
                .containsEntry("room", "meeting-100")
                .containsEntry("roomJoin", true)
                .containsEntry("canPublish", true)
                .containsEntry("canSubscribe", true)
                .containsEntry("canPublishData", true);
    }

    @Test
    @DisplayName("WebSocket URL을 RoomService용 HTTP URL로 변환한다")
    void apiUrl_convertsWebSocketSchemeToHttpScheme() {
        assertThat(liveKitProperties.apiUrl())
                .isEqualTo("https://test.livekit.cloud");
    }

    @Test
    @DisplayName("LiveKit RoomServiceClient를 공통 Bean으로 생성할 수 있다")
    void liveKitRoomServiceClient_createsClient() {
        assertThat(new LiveKitConfiguration()
                .liveKitRoomServiceClient(liveKitProperties))
                .isNotNull();
    }
}
