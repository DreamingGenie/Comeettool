package com.ssafy.backend.meeting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
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

import com.ssafy.backend.meeting.dto.ResponseProcessMeetingDto;

@DisplayName("AI 회의 처리 Client 테스트")
class AiTranscriptionClientTest {

    private static final String BASE_URL = "http://ai-server:8000";
    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final Long MEETING_ID = 15L;
    private static final String PROCESS_URL =
            BASE_URL + "/meetings/15/process";

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
    @DisplayName("회의 종료 후 AI process API를 내부 토큰과 함께 호출한다")
    void processMeeting_callsAiProcessApi() {
        mockServer.expect(requestTo(PROCESS_URL))
                .andExpect(method(POST))
                .andExpect(header(
                        "X-Internal-Token",
                        INTERNAL_TOKEN
                ))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "job_id": "job-15",
                                  "meeting_id": 15,
                                  "status": "pending"
                                }
                                """));

        ResponseProcessMeetingDto response =
                aiTranscriptionClient.processMeeting(MEETING_ID);

        assertThat(response.jobId()).isEqualTo("job-15");
        assertThat(response.meetingId()).isEqualTo(MEETING_ID);
        assertThat(response.status()).isEqualTo("pending");
        mockServer.verify();
    }

    @ParameterizedTest(name = "HTTP {0} 응답은 재시도한다")
    @ValueSource(ints = {408, 429, 500, 503})
    @DisplayName("일시적인 HTTP 오류는 재시도 가능한 오류로 분류한다")
    void processMeeting_classifiesTemporaryStatusAsRetryable(int status) {
        mockServer.expect(requestTo(PROCESS_URL))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() ->
                aiTranscriptionClient.processMeeting(MEETING_ID))
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isTrue());
        mockServer.verify();
    }

    @ParameterizedTest(name = "HTTP {0} 응답은 재시도하지 않는다")
    @ValueSource(ints = {400, 401, 404})
    @DisplayName("영구적인 HTTP 오류는 재시도 불가능한 오류로 분류한다")
    void processMeeting_classifiesPermanentStatusAsNonRetryable(int status) {
        mockServer.expect(requestTo(PROCESS_URL))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() ->
                aiTranscriptionClient.processMeeting(MEETING_ID))
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isFalse());
        mockServer.verify();
    }

    @Test
    @DisplayName("연결 또는 응답 시간 초과는 재시도 가능한 오류로 분류한다")
    void processMeeting_classifiesTimeoutAsRetryable() {
        mockServer.expect(requestTo(PROCESS_URL))
                .andRespond(request -> {
                    throw new ResourceAccessException("timeout");
                });

        assertThatThrownBy(() ->
                aiTranscriptionClient.processMeeting(MEETING_ID))
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isTrue());
        mockServer.verify();
    }

    @Test
    @DisplayName("응답의 회의 ID가 다르면 재시도하지 않는다")
    void processMeeting_rejectsMismatchedMeetingIdWithoutRetry() {
        mockServer.expect(requestTo(PROCESS_URL))
                .andRespond(withSuccess(
                        """
                        {
                          "job_id": "job-99",
                          "meeting_id": 99,
                          "status": "pending"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() ->
                aiTranscriptionClient.processMeeting(MEETING_ID))
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isFalse());
        mockServer.verify();
    }

    @Test
    @DisplayName("job_id가 비어 있으면 재시도하지 않는다")
    void processMeeting_rejectsBlankJobIdWithoutRetry() {
        mockServer.expect(requestTo(PROCESS_URL))
                .andRespond(withSuccess(
                        """
                        {
                          "job_id": "",
                          "meeting_id": 15,
                          "status": "pending"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() ->
                aiTranscriptionClient.processMeeting(MEETING_ID))
                .isInstanceOf(AiTranscriptionException.class)
                .satisfies(exception -> assertThat(
                        ((AiTranscriptionException) exception).isRetryable()
                ).isFalse());
        mockServer.verify();
    }
}
