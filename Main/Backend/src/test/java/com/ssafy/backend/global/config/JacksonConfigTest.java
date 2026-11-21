package com.ssafy.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 날짜/시간 직렬화가 API 규약(README §0, ISO 8601 UTC)을 따르는지 검증한다.
 * JacksonConfig의 커스터마이저를 Boot가 하듯 JsonMapper 빌더에 적용해 확인한다.
 */
@DisplayName("JacksonConfig 날짜 직렬화")
class JacksonConfigTest {

    private final ObjectMapper objectMapper = customizedMapper();

    private static ObjectMapper customizedMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().utcDateTimeCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("OffsetDateTime은 ISO 8601 UTC 문자열로 직렬화된다")
    void serializesOffsetDateTimeAsIso8601Utc() {
        OffsetDateTime value = OffsetDateTime.of(2026, 7, 27, 8, 30, 0, 0, ZoneOffset.UTC);

        String json = objectMapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"2026-07-27T08:30:00Z\"");
    }

    @Test
    @DisplayName("오프셋이 있는 시각도 UTC(Z)로 정규화해 직렬화된다")
    void normalizesOffsetToUtc() {
        // +09:00 09:30 == UTC 00:30
        OffsetDateTime value = OffsetDateTime.of(2026, 7, 27, 9, 30, 0, 0, ZoneOffset.ofHours(9));

        String json = objectMapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"2026-07-27T00:30:00Z\"");
    }
}
