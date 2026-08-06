package com.ssafy.backend.meeting.event;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.backend.meeting.service.MeetingTranscriptionProcessor;

@ExtendWith(MockitoExtension.class)
@DisplayName("회의 STT 이벤트 Listener 테스트")
class MeetingTranscriptionEventListenerTest {

    @Mock
    private MeetingTranscriptionProcessor transcriptionProcessor;

    @InjectMocks
    private MeetingTranscriptionEventListener eventListener;

    @Test
    @DisplayName("회의 종료 이벤트를 받으면 AI process를 호출한다")
    void handleMeetingTranscriptionEnded_callsProcessMeeting() {
        Long meetingId = 15L;
        String endedAt = "2026-08-03T16:25:17.64601+09:00";

        eventListener.handleMeetingTranscriptionEnded(
                new MeetingTranscriptionEndedEvent(
                        meetingId,
                        endedAt
                )
        );

        verify(transcriptionProcessor).processMeeting(meetingId);
    }
}
