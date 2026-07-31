package com.ssafy.backend.meeting.livekit;

public interface LiveKitParticipantManager {

    void disconnectParticipant(
            Long meetingRoomId,
            Long participantId
    );
}
