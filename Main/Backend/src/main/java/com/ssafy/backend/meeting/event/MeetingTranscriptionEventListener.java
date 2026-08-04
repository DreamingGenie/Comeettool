package com.ssafy.backend.meeting.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ssafy.backend.meeting.service.MeetingTranscriptionProcessor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MeetingTranscriptionEventListener {

    private final MeetingTranscriptionProcessor transcriptionProcessor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMeetingTranscriptionStarted(
            MeetingTranscriptionStartedEvent event
    ) {
        transcriptionProcessor.startTranscription(
                event.meetingId(),
                event.startedAt()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMeetingTranscriptionEnded(
            MeetingTranscriptionEndedEvent event
    ) {
        transcriptionProcessor.endTranscription(
                event.meetingId(),
                event.endedAt()
        );
    }
}
