package com.ssafy.backend.meeting.livekit;

import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class LiveKitNameGenerator {

    private static final String MEETING_ROOM_PREFIX = "meeting-";
    private static final String PARTICIPANT_PREFIX = "participant-";

    public String generateMeetingRoomName(Long meetingRoomId) {
        Objects.requireNonNull(meetingRoomId, "회의방 ID는 필수입니다.");
        return MEETING_ROOM_PREFIX + meetingRoomId;
    }

    public String generateParticipantIdentity(Long participantId) {
        Objects.requireNonNull(participantId, "참여자 ID는 필수입니다.");
        return PARTICIPANT_PREFIX + participantId;
    }
}
