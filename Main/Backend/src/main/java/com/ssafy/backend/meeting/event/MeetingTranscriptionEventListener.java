package com.ssafy.backend.meeting.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ssafy.backend.meeting.service.MeetingTranscriptionStartProcessor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MeetingTranscriptionEventListener {

    private final MeetingTranscriptionStartProcessor transcriptionStartProcessor;

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
