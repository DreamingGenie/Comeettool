package com.ssafy.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * ObjectMapper 빈 등록.
 * Boot 4 webmvc 스타터는 Jackson 자동설정 빈을 제공하지 않으므로 직접 정의한다.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        // 날짜/시간은 API 규약(README §0)대로 ISO 8601 UTC 문자열로 직렬화한다.
        // 기본값(WRITE_DATES_AS_TIMESTAMPS=true)이면 epoch 숫자로 나가 규약을 위반한다.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        return mapper;
    }
}
