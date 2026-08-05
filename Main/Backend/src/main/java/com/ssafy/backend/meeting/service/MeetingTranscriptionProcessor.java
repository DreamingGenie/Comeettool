package com.ssafy.backend.meeting.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.ssafy.backend.meeting.client.AiTranscriptionClient;
import com.ssafy.backend.meeting.client.AiTranscriptionException;
import com.ssafy.backend.meeting.config.MeetingTranscriptionTaskConfig;
import com.ssafy.backend.meeting.dto.ResponseProcessMeetingDto;
import com.ssafy.backend.meeting.vad.VadUploadFlightTracker;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MeetingTranscriptionProcessor {

    private final AiTranscriptionClient aiTranscriptionClient;
    private final VadUploadFlightTracker vadUploadFlightTracker;
    private final TaskExecutor transcriptionTaskExecutor;
    private final TaskScheduler transcriptionRetryScheduler;
    private final int maxAttempts;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;

    public MeetingTranscriptionProcessor(
            AiTranscriptionClient aiTranscriptionClient,
            VadUploadFlightTracker vadUploadFlightTracker,
            @Qualifier(
                    MeetingTranscriptionTaskConfig.TRANSCRIPTION_TASK_EXECUTOR
            )
            TaskExecutor transcriptionTaskExecutor,
            @Qualifier(
                    MeetingTranscriptionTaskConfig.TRANSCRIPTION_RETRY_SCHEDULER
            )
            TaskScheduler transcriptionRetryScheduler,
            @Value("${ai.transcription.max-attempts:3}")
            int maxAttempts,
            @Value("${ai.transcription.initial-retry-delay:1s}")
            Duration initialRetryDelay,
            @Value("${ai.transcription.max-retry-delay:5s}")
            Duration maxRetryDelay
    ) {
        this.aiTranscriptionClient = aiTranscriptionClient;
        this.vadUploadFlightTracker = vadUploadFlightTracker;
        this.transcriptionTaskExecutor = transcriptionTaskExecutor;
        this.transcriptionRetryScheduler = transcriptionRetryScheduler;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialRetryDelay = initialRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    public void processMeeting(Long meetingId) {
        submitAttempt(meetingId, 1);
    }

    private void submitAttempt(Long meetingId, int attempt) {
        try {
            transcriptionTaskExecutor.execute(
                    () -> executeAttempt(meetingId, attempt)
            );
        } catch (TaskRejectedException exception) {
            log.error(
                    "AI 회의 처리 작업 등록 실패: meetingId={}, attempt={}",
                    meetingId,
                    attempt,
                    exception
            );
        }
    }

    private void executeAttempt(Long meetingId, int attempt) {
        try {
            boolean drained = vadUploadFlightTracker.awaitIdle(meetingId);
            if (!drained) {
                log.warn(
                        "Proceeding AI meeting process after VAD drain "
                                + "timeout: meetingId={}, attempt={}",
                        meetingId,
                        attempt
                );
            }

            ResponseProcessMeetingDto response =
                    aiTranscriptionClient.processMeeting(meetingId);
            log.info(
                    "AI 회의 처리 요청 완료: meetingId={}, jobId={}, "
                            + "status={}, attempt={}",
                    response.meetingId(),
                    response.jobId(),
                    response.status(),
                    attempt
            );
            vadUploadFlightTracker.clear(meetingId);
        } catch (AiTranscriptionException exception) {
            handleFailure(meetingId, attempt, exception);
        } catch (RuntimeException exception) {
            log.error(
                    "AI 회의 처리 중 예상하지 못한 오류: "
                            + "meetingId={}, attempt={}",
                    meetingId,
                    attempt,
                    exception
            );
        }
    }

    private void handleFailure(
            Long meetingId,
            int attempt,
            AiTranscriptionException exception
    ) {
        if (!exception.isRetryable()) {
            log.error(
                    "AI 회의 처리 재시도 불가 오류: "
                            + "meetingId={}, attempt={}, failureType={}",
                    meetingId,
                    attempt,
                    exception.getFailureType(),
                    exception
            );
            return;
        }

        if (attempt >= maxAttempts) {
            log.error(
                    "AI 회의 처리 최종 실패: meetingId={}, attempts={}",
                    meetingId,
                    maxAttempts,
                    exception
            );
            return;
        }

        scheduleRetry(meetingId, attempt);
    }

    private void scheduleRetry(Long meetingId, int failedAttempt) {
        Duration retryDelay = calculateRetryDelay(failedAttempt);
        int nextAttempt = failedAttempt + 1;
        Instant retryAt = Instant.now().plus(retryDelay);

        try {
            transcriptionRetryScheduler.schedule(
                    () -> submitAttempt(meetingId, nextAttempt),
                    retryAt
            );
            log.warn(
                    "AI 회의 처리 재시도 예약: "
                            + "meetingId={}, nextAttempt={}, retryDelayMs={}",
                    meetingId,
                    nextAttempt,
                    retryDelay.toMillis()
            );
        } catch (TaskRejectedException exception) {
            log.error(
                    "AI 회의 처리 재시도 예약 실패: "
                            + "meetingId={}, nextAttempt={}",
                    meetingId,
                    nextAttempt,
                    exception
            );
        }
    }

    private Duration calculateRetryDelay(int failedAttempt) {
        long multiplier = 1L << Math.min(failedAttempt - 1, 30);
        Duration calculatedDelay = initialRetryDelay.multipliedBy(multiplier);

        if (calculatedDelay.compareTo(maxRetryDelay) > 0) {
            return maxRetryDelay;
        }
        return calculatedDelay;
    }
}
