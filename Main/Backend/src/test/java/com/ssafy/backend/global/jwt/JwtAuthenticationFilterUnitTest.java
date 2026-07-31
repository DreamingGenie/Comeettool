package com.ssafy.backend.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("JWT 인증 필터 경로 제외 테스트")
class JwtAuthenticationFilterUnitTest {

    private final JwtAuthenticationFilter jwtAuthenticationFilter =
            new JwtAuthenticationFilter(mock(JwtProvider.class));

    @Test
    @DisplayName("LiveKit 웹훅 요청은 사용자 JWT 검증에서 제외한다")
    void shouldNotFilter_skipsLiveKitWebhook() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/webhooks/livekit"
        );
        request.setServletPath("/api/v1/webhooks/livekit");

        assertThat(jwtAuthenticationFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("일반 API 요청은 사용자 JWT 검증 대상이다")
    void shouldNotFilter_keepsRegularApiProtected() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/meetings/1/leave"
        );
        request.setServletPath("/api/v1/meetings/1/leave");

        assertThat(jwtAuthenticationFilter.shouldNotFilter(request)).isFalse();
    }
}
