package com.ssafy.backend.meeting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.ResponseStartTranscriptionDto;

@DisplayName("AI 회의 시작 Client 테스트")
class AiTranscriptionClientTest {

    private static final String BASE_URL = "http://ai-server:8000";
    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final Long MEETING_ID = 15L;
    private static final String STARTED_AT =
            "2026-08-03T15:25:17.64601+09:00";

    private MockRestServiceServer mockServer;
    private AiTranscriptionClient aiTranscriptionClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(
                        "X-Internal-Token",
                        INTERNAL_TOKEN
                );
        mockServer = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();
        aiTranscriptionClient = new AiTranscriptionClient(
                restClientBuilder.build()
        );
    }

    @Test
    @DisplayName("회의 생성 시 AI 시작 API를 호출한다")
    void startTranscription_callsAiInternalApi() {
        mockServer.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/meetings/15/transcription/start"
                ))
                .andExpect(method(POST))
                .andExpect(header(
                        "X-Internal-Token",
                        INTERNAL_TOKEN
                ))
                .andExpect(content().json("""
                        {
                          "startedAt": "2026-08-03T15:25:17.64601+09:00"
                        }
                        """))
                .andRespond(withSuccess(
                        """
                        {
                          "code": "SUCCESS",
                          "message": "회의 시작",
                          "meetingRoomId": 15,
                          "s3Prefix": "conferences/15/"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ResponseStartTranscriptionDto response =
                aiTranscriptionClient.startTranscription(
                        MEETING_ID,
                        STARTED_AT
                );

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.meetingRoomId()).isEqualTo(MEETING_ID);
        assertThat(response.s3Prefix()).isEqualTo("conferences/15/");
        mockServer.verify();
    }

    @Test
    @DisplayName("AI 서버 오류는 도메인 예외로 변환한다")
    void startTranscription_throwsExceptionWhenAiServerFails() {
        mockServer.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/meetings/15/transcription/start"
                ))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() ->
                aiTranscriptionClient.startTranscription(
                        MEETING_ID,
                        STARTED_AT
                ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception).getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.MEETING_AI_TRANSCRIPTION_START_FAILED
                );
        mockServer.verify();
    }

    @Test
    @DisplayName("AI 응답의 S3 Prefix가 다르면 실패 처리한다")
    void startTranscription_rejectsInvalidS3Prefix() {
        mockServer.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/meetings/15/transcription/start"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "code": "SUCCESS",
                          "message": "회의 시작",
                          "meetingRoomId": 15,
                          "s3Prefix": "meetings/15/"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() ->
                aiTranscriptionClient.startTranscription(
                        MEETING_ID,
                        STARTED_AT
                ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception).getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.MEETING_AI_TRANSCRIPTION_START_FAILED
                );
        mockServer.verify();
    }
}
