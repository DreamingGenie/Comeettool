package com.ssafy.backend.meeting.livekit;

import org.springframework.stereotype.Component;

import com.ssafy.backend.meeting.livekit.config.LiveKitProperties;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LiveKitTokenProviderImpl implements LiveKitTokenProvider {

    private final LiveKitProperties liveKitProperties;
    private final LiveKitNameGenerator liveKitNameGenerator;

    @Override
    public LiveKitConnectionInfo generateJoinToken(
            Long meetingRoomId,
            Long participantId,
            String participantName
    ) {
        String roomName = liveKitNameGenerator.generateMeetingRoomName(meetingRoomId);
        String participantIdentity =
                liveKitNameGenerator.generateParticipantIdentity(participantId);

        AccessToken accessToken = new AccessToken(
                liveKitProperties.apiKey(),
                liveKitProperties.apiSecret()
        );
        accessToken.setIdentity(participantIdentity);
        accessToken.setName(participantName);
        accessToken.setTtl(liveKitProperties.tokenTtl().toMillis());
        accessToken.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(true),
                new CanSubscribe(true),
                new CanPublishData(true)
        );

        return new LiveKitConnectionInfo(
                accessToken.toJwt(),
                liveKitProperties.url(),
                roomName,
                participantIdentity
        );
    }
}
