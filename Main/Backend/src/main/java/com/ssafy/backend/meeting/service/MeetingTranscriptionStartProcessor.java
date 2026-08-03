package com.ssafy.backend.meeting.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.meeting.client.AiTranscriptionClient;
import com.ssafy.backend.meeting.dto.ResponseStartTranscriptionDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MeetingTranscriptionStartProcessor {

    private final AiTranscriptionClient aiTranscriptionClient;
    private final int maxAttempts;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;

    public MeetingTranscriptionStartProcessor(
            AiTranscriptionClient aiTranscriptionClient,
            @Value("${ai.transcription-start.max-attempts:3}")
            int maxAttempts,
            @Value("${ai.transcription-start.initial-retry-delay:1s}")
            Duration initialRetryDelay,
            @Value("${ai.transcription-start.max-retry-delay:5s}")
            Duration maxRetryDelay
    ) {
        this.aiTranscriptionClient = aiTranscriptionClient;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialRetryDelay = initialRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    public void startTranscription(Long meetingId, String startedAt) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
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
                return;
            } catch (CustomException exception) {
                if (attempt == maxAttempts) {
                    log.error(
                            "AI 회의 STT 폴링 시작 최종 실패: "
                                    + "meetingId={}, attempts={}",
                            meetingId,
                            maxAttempts,
                            exception
                    );
                    return;
                }

                Duration retryDelay = calculateRetryDelay(attempt);
                log.warn(
                        "AI 회의 STT 폴링 시작 재시도 예정: "
                                + "meetingId={}, attempt={}, retryDelayMs={}",
                        meetingId,
                        attempt,
                        retryDelay.toMillis()
                );

                if (!waitForRetry(meetingId, retryDelay)) {
                    return;
                }
            }
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

    private boolean waitForRetry(Long meetingId, Duration retryDelay) {
        try {
            Thread.sleep(retryDelay);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "AI 회의 STT 폴링 시작 재시도 중단: meetingId={}",
                    meetingId,
                    exception
            );
            return false;
        }
    }
}
