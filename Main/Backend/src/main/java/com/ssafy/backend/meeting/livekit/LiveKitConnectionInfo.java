package com.ssafy.backend.meeting.livekit;

public record LiveKitConnectionInfo(
        String token,
        String url,
        String roomName,
        String participantIdentity
) {
}
