package com.ssafy.backend.global.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Boot가 자동 구성하는 Jackson 3 {@code JsonMapper}(HTTP 응답 변환기와 직접 주입 양쪽이 쓰는 단일 매퍼)에
 * API 규약(README §0)을 적용한다.
 *
 * <p>Jackson 3는 날짜/시간을 기본으로 ISO 8601 문자열로 쓰지만 오프셋은 값 그대로 둔다
 * (예: {@code 2026-07-27T09:30:00+09:00}). 매퍼의 시간대를 UTC로 지정해 규약대로
 * {@code 2026-07-27T00:30:00Z}로 정규화한다.</p>
 *
 * <p>예전에는 여기서 Jackson 2 {@code ObjectMapper} 빈을 따로 만들었다. 그 빈은 Jackson 2 타입을 직접
 * 주입받는 코드만 썼고, HTTP 응답은 Jackson 3 매퍼가 직렬화해 이 설정이 닿지 않았다. 매퍼를 하나로 합쳤다.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer utcDateTimeCustomizer() {
        return builder -> builder.defaultTimeZone(TimeZone.getTimeZone("UTC"));
    }
}
