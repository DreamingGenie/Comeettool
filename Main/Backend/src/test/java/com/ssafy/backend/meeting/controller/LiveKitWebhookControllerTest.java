package com.ssafy.backend.meeting.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.meeting.livekit.LiveKitWebhookService;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiveKit 웹훅 API 테스트")
class LiveKitWebhookControllerTest {

    private static final String WEBHOOK_ENDPOINT = "/api/v1/webhooks/livekit";
    private static final String WEBHOOK_MEDIA_TYPE = "application/webhook+json";
    private static final String RAW_BODY = "{\"event\":\"participant_left\"}";
    private static final String AUTHORIZATION_HEADER = "Bearer livekit-signature";

    @Mock
    private LiveKitWebhookService liveKitWebhookService;

    @InjectMocks
    private LiveKitWebhookController liveKitWebhookController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(liveKitWebhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("서명이 포함된 웹훅을 수신하면 200을 반환한다")
    void receiveLiveKitWebhook_returns200() throws Exception {
        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .contentType(WEBHOOK_MEDIA_TYPE)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_HEADER)
                        .content(RAW_BODY))
                .andExpect(status().isOk());

        verify(liveKitWebhookService).handle(
                RAW_BODY,
                AUTHORIZATION_HEADER
        );
    }

    @Test
    @DisplayName("LiveKit 서명 검증에 실패하면 401을 반환한다")
    void receiveLiveKitWebhook_returns401ForInvalidSignature() throws Exception {
        org.mockito.BDDMockito.willThrow(new CustomException(
                ErrorCode.MEETING_LIVEKIT_WEBHOOK_UNAUTHORIZED
        )).given(liveKitWebhookService).handle(
                RAW_BODY,
                AUTHORIZATION_HEADER
        );

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .contentType(WEBHOOK_MEDIA_TYPE)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_HEADER)
                        .content(RAW_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("MEETING_LIVEKIT_WEBHOOK_UNAUTHORIZED"));
    }
}
