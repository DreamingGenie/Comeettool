package com.ssafy.backend.meeting.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.client.AiTranscriptionClient;
import com.ssafy.backend.meeting.dto.ResponseStartTranscriptionDto;

@ExtendWith(MockitoExtension.class)
@DisplayName("회의 STT 폴링 시작 Processor 테스트")
class MeetingTranscriptionStartProcessorTest {

    private static final Long MEETING_ID = 15L;
    private static final String STARTED_AT =
            "2026-08-03T15:25:17.64601+09:00";

    @Mock
    private AiTranscriptionClient aiTranscriptionClient;

    private MeetingTranscriptionStartProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MeetingTranscriptionStartProcessor(
                aiTranscriptionClient,
                3,
                Duration.ZERO,
                Duration.ZERO
        );
    }

    @Test
    @DisplayName("첫 요청이 성공하면 추가 호출하지 않는다")
    void startTranscription_doesNotRetryWhenFirstAttemptSucceeds() {
        given(aiTranscriptionClient.startTranscription(
                MEETING_ID,
                STARTED_AT
        )).willReturn(successResponse());

        processor.startTranscription(MEETING_ID, STARTED_AT);

        verify(aiTranscriptionClient).startTranscription(
                MEETING_ID,
                STARTED_AT
        );
    }

    @Test
    @DisplayName("AI 요청이 실패하면 최대 횟수 안에서 재시도한다")
    void startTranscription_retriesUntilRequestSucceeds() {
        given(aiTranscriptionClient.startTranscription(
                MEETING_ID,
                STARTED_AT
        ))
                .willThrow(startFailedException())
                .willThrow(startFailedException())
                .willReturn(successResponse());

        processor.startTranscription(MEETING_ID, STARTED_AT);

        verify(aiTranscriptionClient, times(3)).startTranscription(
                MEETING_ID,
                STARTED_AT
        );
    }

    @Test
    @DisplayName("최대 재시도 후 실패해도 회의 생성 요청으로 예외를 전파하지 않는다")
    void startTranscription_doesNotPropagateAfterRetriesExhausted() {
        given(aiTranscriptionClient.startTranscription(
                MEETING_ID,
                STARTED_AT
        )).willThrow(startFailedException());

        assertThatCode(() ->
                processor.startTranscription(MEETING_ID, STARTED_AT)
        ).doesNotThrowAnyException();

        verify(aiTranscriptionClient, times(3)).startTranscription(
                MEETING_ID,
                STARTED_AT
        );
    }

    private ResponseStartTranscriptionDto successResponse() {
        return new ResponseStartTranscriptionDto(
                "SUCCESS",
                "회의 시작",
                MEETING_ID,
                "conferences/15/"
        );
    }

    private CustomException startFailedException() {
        return new CustomException(
                ErrorCode.MEETING_AI_TRANSCRIPTION_START_FAILED
        );
    }
}
