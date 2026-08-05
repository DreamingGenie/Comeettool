package com.ssafy.backend.meeting.livekit;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

import io.livekit.server.RoomServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveKitParticipantManagerImpl implements LiveKitParticipantManager {

    private static final int LIVEKIT_NOT_FOUND_STATUS = HttpStatus.NOT_FOUND.value();

    private final RoomServiceClient roomServiceClient;
    private final LiveKitNameGenerator liveKitNameGenerator;

    @Override
    public void disconnectParticipant(
            Long meetingRoomId,
            Long participantId
    ) {
        String roomName = liveKitNameGenerator.generateMeetingRoomName(meetingRoomId);
        String participantIdentity =
                liveKitNameGenerator.generateParticipantIdentity(participantId);

        try {
            Response<Void> response = roomServiceClient
                    .removeParticipant(roomName, participantIdentity)
                    .execute();

            if (response.isSuccessful()) {
                return;
            }

            if (response.code() == LIVEKIT_NOT_FOUND_STATUS) {
                return;
            }

            log.error(
                    "LiveKit 참여자 연결 종료 실패: meetingRoomId={}, participantId={}, status={}",
                    meetingRoomId,
                    participantId,
                    response.code()
            );
            throw new CustomException(
                    ErrorCode.MEETING_LIVEKIT_DISCONNECT_FAILED
            );
        } catch (IOException exception) {
            log.error(
                    "LiveKit 참여자 연결 종료 중 통신 오류: meetingRoomId={}, participantId={}",
                    meetingRoomId,
                    participantId,
                    exception
            );
            throw new CustomException(
                    ErrorCode.MEETING_LIVEKIT_DISCONNECT_FAILED
            );
        }
    }
}
