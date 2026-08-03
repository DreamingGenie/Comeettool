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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

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

    @ParameterizedTest(name = "HTTP {0} 응답은 재시도한다")
    @ValueSource(ints = {408, 429, 500, 503})
    @DisplayName("일시적인 HTTP 오류는 재시도 가능한 오류로 분류한다")
    void startTranscription_classifiesTemporaryStatusAsRetryable(
            int status
    ) {
        mockServer.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/meetings/15/transcription/start"
                ))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() ->
                aiTranscriptionClient.startTranscription(
                        MEETING_ID,
                        STARTED_AT
                ))
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isTrue());
        mockServer.verify();
    }

    @ParameterizedTest(name = "HTTP {0} 응답은 재시도하지 않는다")
    @ValueSource(ints = {400, 401, 404})
    @DisplayName("영구적인 HTTP 오류는 재시도 불가능한 오류로 분류한다")
    void startTranscription_classifiesPermanentStatusAsNonRetryable(
            int status
    ) {
        mockServer.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/meetings/15/transcription/start"
                ))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() ->
                aiTranscriptionClient.startTranscription(
                        MEETING_ID,
                        STARTED_AT
                ))
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isFalse());
        mockServer.verify();
    }

    @Test
    @DisplayName("연결 또는 응답 시간 초과는 재시도 가능한 오류로 분류한다")
    void startTranscription_classifiesTimeoutAsRetryable() {
        mockServer.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/meetings/15/transcription/start"
                ))
                .andRespond(request -> {
                    throw new ResourceAccessException("timeout");
                });

        assertThatThrownBy(() ->
                aiTranscriptionClient.startTranscription(
                        MEETING_ID,
                        STARTED_AT
                ))
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isTrue());
        mockServer.verify();
    }

    @Test
    @DisplayName("AI 응답의 S3 Prefix가 다르면 재시도하지 않는다")
    void startTranscription_rejectsInvalidS3PrefixWithoutRetry() {
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
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isFalse());
        mockServer.verify();
    }
}
