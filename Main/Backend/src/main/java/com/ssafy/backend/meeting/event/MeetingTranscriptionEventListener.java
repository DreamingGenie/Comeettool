package com.ssafy.backend.meeting.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ssafy.backend.meeting.config.MeetingAsyncConfig;
import com.ssafy.backend.meeting.service.MeetingTranscriptionStartProcessor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MeetingTranscriptionEventListener {

    private final MeetingTranscriptionStartProcessor transcriptionStartProcessor;

    @Async(MeetingAsyncConfig.TRANSCRIPTION_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMeetingTranscriptionStarted(
            MeetingTranscriptionStartedEvent event
    ) {
        transcriptionStartProcessor.startTranscription(
                event.meetingId(),
                event.startedAt()
        );
    }
}
