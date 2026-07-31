package com.ssafy.backend.meeting.livekit;

public interface LiveKitTokenProvider {

    LiveKitConnectionInfo generateJoinToken(
            Long meetingRoomId,
            Long participantId,
            String participantName
    );
}
