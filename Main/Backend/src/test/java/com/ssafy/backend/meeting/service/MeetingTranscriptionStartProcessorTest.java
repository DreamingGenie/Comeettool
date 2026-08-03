package com.ssafy.backend.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

import com.ssafy.backend.meeting.client.AiTranscriptionClient;
import com.ssafy.backend.meeting.client.AiTranscriptionException;
import com.ssafy.backend.meeting.client.AiTranscriptionFailureType;
import com.ssafy.backend.meeting.dto.ResponseStartTranscriptionDto;

@ExtendWith(MockitoExtension.class)
@DisplayName("회의 STT 폴링 시작 Processor 테스트")
class MeetingTranscriptionStartProcessorTest {

    private static final Long MEETING_ID = 15L;
    private static final String STARTED_AT =
            "2026-08-03T15:25:17.64601+09:00";

    @Mock
    private AiTranscriptionClient aiTranscriptionClient;

    @Mock
    private TaskExecutor transcriptionTaskExecutor;

    @Mock
    private TaskScheduler transcriptionRetryScheduler;

    private final Deque<Runnable> executorTasks = new ArrayDeque<>();
    private final Deque<Runnable> retryTasks = new ArrayDeque<>();

    private MeetingTranscriptionStartProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MeetingTranscriptionStartProcessor(
                aiTranscriptionClient,
                transcriptionTaskExecutor,
                transcriptionRetryScheduler,
                3,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
        );
    }

    @Test
    @DisplayName("최초 AI 호출 작업을 전용 Executor에 제출한다")
    void startTranscription_submitsFirstAttemptToExecutor() {
        captureExecutorTasks();
        given(aiTranscriptionClient.startTranscription(
                MEETING_ID,
                STARTED_AT
        )).willReturn(successResponse());

        processor.startTranscription(MEETING_ID, STARTED_AT);

        verify(transcriptionTaskExecutor).execute(any(Runnable.class));
        verifyNoInteractions(aiTranscriptionClient);

        runNextExecutorTask();

        verify(aiTranscriptionClient).startTranscription(
                MEETING_ID,
                STARTED_AT
        );
        assertThat(retryTasks).isEmpty();
    }

    @Test
    @DisplayName("재시도 가능한 오류는 Scheduler에 다음 시도를 예약한다")
    void startTranscription_schedulesRetryForRetryableFailure() {
        captureExecutorTasks();
        captureRetryTasks();
        given(aiTranscriptionClient.startTranscription(
                MEETING_ID,
                STARTED_AT
        ))
                .willThrow(retryableException())
                .willReturn(successResponse());

        processor.startTranscription(MEETING_ID, STARTED_AT);
        runNextExecutorTask();

        verify(transcriptionRetryScheduler).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
        assertThat(executorTasks).isEmpty();
        assertThat(retryTasks).hasSize(1);

        runNextRetryTask();
        runNextExecutorTask();

        verify(aiTranscriptionClient, times(2)).startTranscription(
                MEETING_ID,
                STARTED_AT
        );
    }

    @Test
    @DisplayName("재시도 불가능한 오류는 추가 시도를 예약하지 않는다")
    void startTranscription_doesNotRetryNonRetryableFailure() {
        captureExecutorTasks();
        given(aiTranscriptionClient.startTranscription(
                MEETING_ID,
                STARTED_AT
        )).willThrow(nonRetryableException());

        processor.startTranscription(MEETING_ID, STARTED_AT);
        runNextExecutorTask();

        verify(
                transcriptionRetryScheduler,
                never()
        ).schedule(any(Runnable.class), any(Instant.class));
        assertThat(retryTasks).isEmpty();
    }

    @Test
    @DisplayName("최대 시도 횟수 이후에는 추가 재시도를 예약하지 않는다")
    void startTranscription_stopsAfterMaximumAttempts() {
        captureExecutorTasks();
        captureRetryTasks();
        given(aiTranscriptionClient.startTranscription(
                MEETING_ID,
                STARTED_AT
        )).willThrow(retryableException());

        processor.startTranscription(MEETING_ID, STARTED_AT);
        runNextExecutorTask();
        runNextRetryTask();
        runNextExecutorTask();
        runNextRetryTask();
        runNextExecutorTask();

        verify(aiTranscriptionClient, times(3)).startTranscription(
                MEETING_ID,
                STARTED_AT
        );
        verify(transcriptionRetryScheduler, times(2)).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
        assertThat(retryTasks).isEmpty();
        assertThat(executorTasks).isEmpty();
    }

    @Test
    @DisplayName("Executor가 작업을 거부해도 요청 스레드에서 AI를 호출하지 않는다")
    void startTranscription_doesNotCallAiWhenExecutorRejectsTask() {
        doThrow(new TaskRejectedException("executor saturated"))
                .when(transcriptionTaskExecutor)
                .execute(any(Runnable.class));

        assertThatCode(() ->
                processor.startTranscription(MEETING_ID, STARTED_AT)
        ).doesNotThrowAnyException();

        verifyNoInteractions(aiTranscriptionClient);
        verify(
                transcriptionRetryScheduler,
                never()
        ).schedule(any(Runnable.class), any(Instant.class));
    }

    private void captureExecutorTasks() {
        doAnswer(invocation -> {
            executorTasks.addLast(invocation.getArgument(0));
            return null;
        }).when(transcriptionTaskExecutor).execute(any(Runnable.class));
    }

    private void captureRetryTasks() {
        given(transcriptionRetryScheduler.schedule(
                any(Runnable.class),
                any(Instant.class)
        )).willAnswer(invocation -> {
            retryTasks.addLast(invocation.getArgument(0));
            return null;
        });
    }

    private void runNextExecutorTask() {
        executorTasks.removeFirst().run();
    }

    private void runNextRetryTask() {
        retryTasks.removeFirst().run();
    }

    private ResponseStartTranscriptionDto successResponse() {
        return new ResponseStartTranscriptionDto(
                "SUCCESS",
                "회의 시작",
                MEETING_ID,
                "conferences/15/"
        );
    }

    private AiTranscriptionException retryableException() {
        return new AiTranscriptionException(
                AiTranscriptionFailureType.RETRYABLE,
                "일시적 오류"
        );
    }

    private AiTranscriptionException nonRetryableException() {
        return new AiTranscriptionException(
                AiTranscriptionFailureType.NON_RETRYABLE,
                "영구적 오류"
        );
    }
}
