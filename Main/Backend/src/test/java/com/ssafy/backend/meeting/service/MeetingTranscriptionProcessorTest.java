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
import com.ssafy.backend.meeting.dto.ResponseProcessMeetingDto;
import com.ssafy.backend.meeting.vad.VadUploadFlightTracker;

@ExtendWith(MockitoExtension.class)
@DisplayName("회의 AI 처리 Processor 테스트")
class MeetingTranscriptionProcessorTest {

    private static final Long MEETING_ID = 15L;

    @Mock
    private AiTranscriptionClient aiTranscriptionClient;

    @Mock
    private VadUploadFlightTracker vadUploadFlightTracker;

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
                vadUploadFlightTracker,
                transcriptionTaskExecutor,
                transcriptionRetryScheduler,
                3,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
        );
    }

    @Test
    @DisplayName("회의 처리 작업을 전용 Executor에 제출한다")
    void processMeeting_submitsFirstAttemptToExecutor() {
        captureExecutorTasks();
        given(vadUploadFlightTracker.awaitIdle(MEETING_ID)).willReturn(true);
        given(aiTranscriptionClient.processMeeting(MEETING_ID))
                .willReturn(processSuccessResponse());

        processor.processMeeting(MEETING_ID);

        verify(transcriptionTaskExecutor).execute(any(Runnable.class));
        verifyNoInteractions(aiTranscriptionClient);

        runNextExecutorTask();

        verify(vadUploadFlightTracker).awaitIdle(MEETING_ID);
        verify(aiTranscriptionClient).processMeeting(MEETING_ID);
        verify(vadUploadFlightTracker).clear(MEETING_ID);
        assertThat(retryTasks).isEmpty();
    }

    @Test
    @DisplayName("VAD drain timeout이어도 AI process 호출을 진행한다")
    void processMeeting_proceedsAfterDrainTimeout() {
        captureExecutorTasks();
        given(vadUploadFlightTracker.awaitIdle(MEETING_ID)).willReturn(false);
        given(aiTranscriptionClient.processMeeting(MEETING_ID))
                .willReturn(processSuccessResponse());

        processor.processMeeting(MEETING_ID);
        runNextExecutorTask();

        verify(vadUploadFlightTracker).awaitIdle(MEETING_ID);
        verify(aiTranscriptionClient).processMeeting(MEETING_ID);
        verify(vadUploadFlightTracker).clear(MEETING_ID);
    }

    @Test
    @DisplayName("처리 요청의 재시도 가능한 오류는 Scheduler에 다음 시도를 예약한다")
    void processMeeting_schedulesRetryForRetryableFailure() {
        captureExecutorTasks();
        captureRetryTasks();
        given(vadUploadFlightTracker.awaitIdle(MEETING_ID)).willReturn(true);
        given(aiTranscriptionClient.processMeeting(MEETING_ID))
                .willThrow(retryableException())
                .willReturn(processSuccessResponse());

        processor.processMeeting(MEETING_ID);
        runNextExecutorTask();

        verify(transcriptionRetryScheduler).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
        assertThat(executorTasks).isEmpty();
        assertThat(retryTasks).hasSize(1);

        runNextRetryTask();
        runNextExecutorTask();

        verify(aiTranscriptionClient, times(2)).processMeeting(MEETING_ID);
        verify(vadUploadFlightTracker, times(2)).awaitIdle(MEETING_ID);
        verify(vadUploadFlightTracker).clear(MEETING_ID);
    }

    @Test
    @DisplayName("처리 요청의 재시도 불가능한 오류는 추가 시도를 예약하지 않는다")
    void processMeeting_doesNotRetryNonRetryableFailure() {
        captureExecutorTasks();
        given(vadUploadFlightTracker.awaitIdle(MEETING_ID)).willReturn(true);
        given(aiTranscriptionClient.processMeeting(MEETING_ID))
                .willThrow(nonRetryableException());

        processor.processMeeting(MEETING_ID);
        runNextExecutorTask();

        verify(
                transcriptionRetryScheduler,
                never()
        ).schedule(any(Runnable.class), any(Instant.class));
        assertThat(retryTasks).isEmpty();
        verify(vadUploadFlightTracker, never()).clear(MEETING_ID);
    }

    @Test
    @DisplayName("처리 요청은 최대 시도 횟수 이후 추가 재시도를 예약하지 않는다")
    void processMeeting_stopsAfterMaximumAttempts() {
        captureExecutorTasks();
        captureRetryTasks();
        given(vadUploadFlightTracker.awaitIdle(MEETING_ID)).willReturn(true);
        given(aiTranscriptionClient.processMeeting(MEETING_ID))
                .willThrow(retryableException());

        processor.processMeeting(MEETING_ID);
        runNextExecutorTask();
        runNextRetryTask();
        runNextExecutorTask();
        runNextRetryTask();
        runNextExecutorTask();

        verify(aiTranscriptionClient, times(3)).processMeeting(MEETING_ID);
        verify(transcriptionRetryScheduler, times(2)).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
        assertThat(retryTasks).isEmpty();
        assertThat(executorTasks).isEmpty();
    }

    @Test
    @DisplayName("Executor가 처리 작업을 거부해도 요청 스레드에서 AI를 호출하지 않는다")
    void processMeeting_doesNotCallAiWhenExecutorRejectsTask() {
        doThrow(new TaskRejectedException("executor saturated"))
                .when(transcriptionTaskExecutor)
                .execute(any(Runnable.class));

        assertThatCode(() -> processor.processMeeting(MEETING_ID))
                .doesNotThrowAnyException();

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

    private ResponseProcessMeetingDto processSuccessResponse() {
        return new ResponseProcessMeetingDto(
                "job-15",
                MEETING_ID,
                "pending"
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
