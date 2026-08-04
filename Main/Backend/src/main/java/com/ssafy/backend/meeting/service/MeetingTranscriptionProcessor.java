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
import com.ssafy.backend.meeting.dto.ResponseEndTranscriptionDto;
import com.ssafy.backend.meeting.dto.ResponseStartTranscriptionDto;
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

    public void startTranscription(Long meetingId, String startedAt) {
        submitAttempt(
                TranscriptionOperation.START,
                meetingId,
                startedAt,
                1
        );
    }

    public void endTranscription(Long meetingId, String endedAt) {
        submitAttempt(
                TranscriptionOperation.END,
                meetingId,
                endedAt,
                1
        );
    }

    private void submitAttempt(
            TranscriptionOperation operation,
            Long meetingId,
            String occurredAt,
            int attempt
    ) {
        try {
            transcriptionTaskExecutor.execute(
                    () -> executeAttempt(
                            operation,
                            meetingId,
                            occurredAt,
                            attempt
                    )
            );
        } catch (TaskRejectedException exception) {
            log.error(
                    "AI 회의 STT {} 작업 등록 실패: meetingId={}, attempt={}",
                    operation.description,
                    meetingId,
                    attempt,
                    exception
            );
        }
    }

    private void executeAttempt(
            TranscriptionOperation operation,
            Long meetingId,
            String occurredAt,
            int attempt
    ) {
        try {
            if (operation == TranscriptionOperation.START) {
                ResponseStartTranscriptionDto response =
                        aiTranscriptionClient.startTranscription(
                                meetingId,
                                occurredAt
                        );
                log.info(
                        "AI 회의 STT 폴링 시작 완료: meetingId={}, "
                                + "s3Prefix={}, attempt={}",
                        response.meetingRoomId(),
                        response.s3Prefix(),
                        attempt
                );
                return;
            }

            boolean drained = vadUploadFlightTracker.awaitIdle(meetingId);
            if (!drained) {
                log.warn(
                        "Proceeding AI transcription end after VAD drain "
                                + "timeout: meetingId={}, attempt={}",
                        meetingId,
                        attempt
                );
            }

            ResponseEndTranscriptionDto response =
                    aiTranscriptionClient.endTranscription(
                            meetingId,
                            occurredAt
                    );
            log.info(
                    "AI 회의 STT 폴링 종료 요청 완료: meetingId={}, attempt={}",
                    response.meetingRoomId(),
                    attempt
            );
            vadUploadFlightTracker.clear(meetingId);
        } catch (AiTranscriptionException exception) {
            handleFailure(
                    operation,
                    meetingId,
                    occurredAt,
                    attempt,
                    exception
            );
        } catch (RuntimeException exception) {
            log.error(
                    "AI 회의 STT {} 중 예상하지 못한 오류: "
                            + "meetingId={}, attempt={}",
                    operation.description,
                    meetingId,
                    attempt,
                    exception
            );
        }
    }

    private void handleFailure(
            TranscriptionOperation operation,
            Long meetingId,
            String occurredAt,
            int attempt,
            AiTranscriptionException exception
    ) {
        if (!exception.isRetryable()) {
            log.error(
                    "AI 회의 STT {} 재시도 불가 오류: "
                            + "meetingId={}, attempt={}, failureType={}",
                    operation.description,
                    meetingId,
                    attempt,
                    exception.getFailureType(),
                    exception
            );
            return;
        }

        if (attempt >= maxAttempts) {
            log.error(
                    "AI 회의 STT {} 최종 실패: meetingId={}, attempts={}",
                    operation.description,
                    meetingId,
                    maxAttempts,
                    exception
            );
            return;
        }

        scheduleRetry(operation, meetingId, occurredAt, attempt);
    }

    private void scheduleRetry(
            TranscriptionOperation operation,
            Long meetingId,
            String occurredAt,
            int failedAttempt
    ) {
        Duration retryDelay = calculateRetryDelay(failedAttempt);
        int nextAttempt = failedAttempt + 1;
        Instant retryAt = Instant.now().plus(retryDelay);

        try {
            transcriptionRetryScheduler.schedule(
                    () -> submitAttempt(
                            operation,
                            meetingId,
                            occurredAt,
                            nextAttempt
                    ),
                    retryAt
            );
            log.warn(
                    "AI 회의 STT {} 재시도 예약: "
                            + "meetingId={}, nextAttempt={}, retryDelayMs={}",
                    operation.description,
                    meetingId,
                    nextAttempt,
                    retryDelay.toMillis()
            );
        } catch (TaskRejectedException exception) {
            log.error(
                    "AI 회의 STT {} 재시도 예약 실패: "
                            + "meetingId={}, nextAttempt={}",
                    operation.description,
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

    private enum TranscriptionOperation {
        START("시작"),
        END("종료");

        private final String description;

        TranscriptionOperation(String description) {
            this.description = description;
        }
    }
}
