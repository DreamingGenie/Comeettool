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
public class LiveKitRoomManagerImpl implements LiveKitRoomManager {

    private static final int LIVEKIT_NOT_FOUND_STATUS =
            HttpStatus.NOT_FOUND.value();

    private final RoomServiceClient roomServiceClient;
    private final LiveKitNameGenerator liveKitNameGenerator;

    @Override
    public void endRoom(Long meetingRoomId) {
        String roomName =
                liveKitNameGenerator.generateMeetingRoomName(meetingRoomId);

        try {
            Response<Void> response = roomServiceClient
                    .deleteRoom(roomName)
                    .execute();

            if (response.isSuccessful()
                    || response.code() == LIVEKIT_NOT_FOUND_STATUS) {
                return;
            }

            log.error(
                    "LiveKit 회의방 종료 실패: meetingRoomId={}, status={}",
                    meetingRoomId,
                    response.code()
            );
            throw new CustomException(
                    ErrorCode.MEETING_LIVEKIT_ROOM_END_FAILED
            );
        } catch (IOException exception) {
            log.error(
                    "LiveKit 회의방 종료 중 통신 오류: meetingRoomId={}",
                    meetingRoomId,
                    exception
            );
            throw new CustomException(
                    ErrorCode.MEETING_LIVEKIT_ROOM_END_FAILED
            );
        }
    }
}
