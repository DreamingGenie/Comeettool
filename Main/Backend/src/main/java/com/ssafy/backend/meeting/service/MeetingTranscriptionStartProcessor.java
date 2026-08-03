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
import com.ssafy.backend.meeting.dto.ResponseStartTranscriptionDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MeetingTranscriptionStartProcessor {

    private final AiTranscriptionClient aiTranscriptionClient;
    private final TaskExecutor transcriptionTaskExecutor;
    private final TaskScheduler transcriptionRetryScheduler;
    private final int maxAttempts;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;

    public MeetingTranscriptionStartProcessor(
            AiTranscriptionClient aiTranscriptionClient,
            @Qualifier(
                    MeetingTranscriptionTaskConfig.TRANSCRIPTION_TASK_EXECUTOR
            )
            TaskExecutor transcriptionTaskExecutor,
            @Qualifier(
                    MeetingTranscriptionTaskConfig.TRANSCRIPTION_RETRY_SCHEDULER
            )
            TaskScheduler transcriptionRetryScheduler,
            @Value("${ai.transcription-start.max-attempts:3}")
            int maxAttempts,
            @Value("${ai.transcription-start.initial-retry-delay:1s}")
            Duration initialRetryDelay,
            @Value("${ai.transcription-start.max-retry-delay:5s}")
            Duration maxRetryDelay
    ) {
        this.aiTranscriptionClient = aiTranscriptionClient;
        this.transcriptionTaskExecutor = transcriptionTaskExecutor;
        this.transcriptionRetryScheduler = transcriptionRetryScheduler;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialRetryDelay = initialRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    public void startTranscription(Long meetingId, String startedAt) {
        submitAttempt(meetingId, startedAt, 1);
    }

    private void submitAttempt(
            Long meetingId,
            String startedAt,
            int attempt
    ) {
        try {
            transcriptionTaskExecutor.execute(
                    () -> executeAttempt(meetingId, startedAt, attempt)
            );
        } catch (TaskRejectedException exception) {
            log.error(
                    "AI 회의 STT 시작 작업 등록 실패: "
                            + "meetingId={}, attempt={}",
                    meetingId,
                    attempt,
                    exception
            );
        }
    }

    private void executeAttempt(
            Long meetingId,
            String startedAt,
            int attempt
    ) {
        try {
            ResponseStartTranscriptionDto response =
                    aiTranscriptionClient.startTranscription(
                            meetingId,
                            startedAt
                    );

            log.info(
                    "AI 회의 STT 폴링 시작 완료: meetingId={}, "
                            + "s3Prefix={}, attempt={}",
                    response.meetingRoomId(),
                    response.s3Prefix(),
                    attempt
            );
        } catch (AiTranscriptionException exception) {
            handleFailure(
                    meetingId,
                    startedAt,
                    attempt,
                    exception
            );
        } catch (RuntimeException exception) {
            log.error(
                    "AI 회의 STT 시작 중 예상하지 못한 오류: "
                            + "meetingId={}, attempt={}",
                    meetingId,
                    attempt,
                    exception
            );
        }
    }

    private void handleFailure(
            Long meetingId,
            String startedAt,
            int attempt,
            AiTranscriptionException exception
    ) {
        if (!exception.isRetryable()) {
            log.error(
                    "AI 회의 STT 시작 재시도 불가 오류: "
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
                    "AI 회의 STT 시작 최종 실패: "
                            + "meetingId={}, attempts={}",
                    meetingId,
                    maxAttempts,
                    exception
            );
            return;
        }

        scheduleRetry(meetingId, startedAt, attempt);
    }

    private void scheduleRetry(
            Long meetingId,
            String startedAt,
            int failedAttempt
    ) {
        Duration retryDelay = calculateRetryDelay(failedAttempt);
        int nextAttempt = failedAttempt + 1;
        Instant retryAt = Instant.now().plus(retryDelay);

        try {
            transcriptionRetryScheduler.schedule(
                    () -> submitAttempt(
                            meetingId,
                            startedAt,
                            nextAttempt
                    ),
                    retryAt
            );
            log.warn(
                    "AI 회의 STT 시작 재시도 예약: "
                            + "meetingId={}, nextAttempt={}, retryDelayMs={}",
                    meetingId,
                    nextAttempt,
                    retryDelay.toMillis()
            );
        } catch (TaskRejectedException exception) {
            log.error(
                    "AI 회의 STT 시작 재시도 예약 실패: "
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
