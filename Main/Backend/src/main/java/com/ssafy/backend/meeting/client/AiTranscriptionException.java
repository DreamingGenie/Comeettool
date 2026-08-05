package com.ssafy.backend.meeting.client;

import lombok.Getter;

@Getter
public class AiTranscriptionException extends RuntimeException {

    private final AiTranscriptionFailureType failureType;

    public AiTranscriptionException(
            AiTranscriptionFailureType failureType,
            String message
    ) {
        super(message);
        this.failureType = failureType;
    }

    public AiTranscriptionException(
            AiTranscriptionFailureType failureType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
    }

    public boolean isRetryable() {
        return failureType == AiTranscriptionFailureType.RETRYABLE;
    }
}
