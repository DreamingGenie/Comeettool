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

    public Long parseMeetingRoomId(String roomName) {
        return parseIdentifier(roomName, MEETING_ROOM_PREFIX);
    }

    public Long parseParticipantId(String participantIdentity) {
        return parseIdentifier(participantIdentity, PARTICIPANT_PREFIX);
    }

    private Long parseIdentifier(String value, String prefix) {
        if (value == null || !value.startsWith(prefix)) {
            throw new IllegalArgumentException("LiveKit 식별자 형식이 올바르지 않습니다.");
        }

        String identifier = value.substring(prefix.length());
        try {
            long parsedIdentifier = Long.parseLong(identifier);
            if (parsedIdentifier <= 0) {
                throw new IllegalArgumentException("LiveKit 식별자는 양수여야 합니다.");
            }
            return parsedIdentifier;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "LiveKit 식별자 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }
}
