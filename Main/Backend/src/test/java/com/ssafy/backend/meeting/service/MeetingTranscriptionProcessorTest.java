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
import com.ssafy.backend.meeting.dto.ResponseEndTranscriptionDto;
import com.ssafy.backend.meeting.dto.ResponseStartTranscriptionDto;

@ExtendWith(MockitoExtension.class)
@DisplayName("회의 STT 시작·종료 Processor 테스트")
class MeetingTranscriptionProcessorTest {

    private static final Long MEETING_ID = 15L;
    private static final String STARTED_AT =
            "2026-08-03T15:25:17.64601+09:00";
    private static final String ENDED_AT =
            "2026-08-03T16:25:17.64601+09:00";

    @Mock
    private AiTranscriptionClient aiTranscriptionClient;

    @Mock
    private TaskExecutor transcriptionTaskExecutor;

    @Mock
    private TaskScheduler transcriptionRetryScheduler;

    private final Deque<Runnable> executorTasks = new ArrayDeque<>();
    private final Deque<Runnable> retryTasks = new ArrayDeque<>();

    private MeetingTranscriptionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MeetingTranscriptionProcessor(
                aiTranscriptionClient,
                transcriptionTaskExecutor,
                transcriptionRetryScheduler,
                3,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
        );
    }

    @Test
    @DisplayName("회의 시작 작업을 전용 Executor에 제출한다")
    void startTranscription_submitsFirstAttemptToExecutor() {
        captureExecutorTasks();
        given(aiTranscriptionClient.startTranscription(
                MEETING_ID,
                STARTED_AT
        )).willReturn(startSuccessResponse());

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
    @DisplayName("회의 종료 작업을 전용 Executor에 제출한다")
    void endTranscription_submitsFirstAttemptToExecutor() {
        captureExecutorTasks();
        given(aiTranscriptionClient.endTranscription(
                MEETING_ID,
                ENDED_AT
        )).willReturn(endSuccessResponse());

        processor.endTranscription(MEETING_ID, ENDED_AT);

        verify(transcriptionTaskExecutor).execute(any(Runnable.class));
        verifyNoInteractions(aiTranscriptionClient);

        runNextExecutorTask();

        verify(aiTranscriptionClient).endTranscription(
                MEETING_ID,
                ENDED_AT
        );
        assertThat(retryTasks).isEmpty();
    }

    @Test
    @DisplayName("종료 요청의 재시도 가능한 오류는 Scheduler에 다음 시도를 예약한다")
    void endTranscription_schedulesRetryForRetryableFailure() {
        captureExecutorTasks();
        captureRetryTasks();
        given(aiTranscriptionClient.endTranscription(
                MEETING_ID,
                ENDED_AT
        ))
                .willThrow(retryableException())
                .willReturn(endSuccessResponse());

        processor.endTranscription(MEETING_ID, ENDED_AT);
        runNextExecutorTask();

        verify(transcriptionRetryScheduler).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
        assertThat(executorTasks).isEmpty();
        assertThat(retryTasks).hasSize(1);

        runNextRetryTask();
        runNextExecutorTask();

        verify(aiTranscriptionClient, times(2)).endTranscription(
                MEETING_ID,
                ENDED_AT
        );
    }

    @Test
    @DisplayName("종료 요청의 재시도 불가능한 오류는 추가 시도를 예약하지 않는다")
    void endTranscription_doesNotRetryNonRetryableFailure() {
        captureExecutorTasks();
        given(aiTranscriptionClient.endTranscription(
                MEETING_ID,
                ENDED_AT
        )).willThrow(nonRetryableException());

        processor.endTranscription(MEETING_ID, ENDED_AT);
        runNextExecutorTask();

        verify(
                transcriptionRetryScheduler,
                never()
        ).schedule(any(Runnable.class), any(Instant.class));
        assertThat(retryTasks).isEmpty();
    }

    @Test
    @DisplayName("종료 요청은 최대 시도 횟수 이후 추가 재시도를 예약하지 않는다")
    void endTranscription_stopsAfterMaximumAttempts() {
        captureExecutorTasks();
        captureRetryTasks();
        given(aiTranscriptionClient.endTranscription(
                MEETING_ID,
                ENDED_AT
        )).willThrow(retryableException());

        processor.endTranscription(MEETING_ID, ENDED_AT);
        runNextExecutorTask();
        runNextRetryTask();
        runNextExecutorTask();
        runNextRetryTask();
        runNextExecutorTask();

        verify(aiTranscriptionClient, times(3)).endTranscription(
                MEETING_ID,
                ENDED_AT
        );
        verify(transcriptionRetryScheduler, times(2)).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
        assertThat(retryTasks).isEmpty();
        assertThat(executorTasks).isEmpty();
    }

    @Test
    @DisplayName("Executor가 종료 작업을 거부해도 요청 스레드에서 AI를 호출하지 않는다")
    void endTranscription_doesNotCallAiWhenExecutorRejectsTask() {
        doThrow(new TaskRejectedException("executor saturated"))
                .when(transcriptionTaskExecutor)
                .execute(any(Runnable.class));

        assertThatCode(() ->
                processor.endTranscription(MEETING_ID, ENDED_AT)
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

    private ResponseStartTranscriptionDto startSuccessResponse() {
        return new ResponseStartTranscriptionDto(
                "SUCCESS",
                "회의 시작",
                MEETING_ID,
                "conferences/15/"
        );
    }

    private ResponseEndTranscriptionDto endSuccessResponse() {
        return new ResponseEndTranscriptionDto(
                "SUCCESS",
                "회의 종료",
                MEETING_ID
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
