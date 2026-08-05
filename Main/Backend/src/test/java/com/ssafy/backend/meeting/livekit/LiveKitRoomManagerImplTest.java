package com.ssafy.backend.meeting.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

import io.livekit.server.RoomServiceClient;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiveKit 회의방 관리 테스트")
class LiveKitRoomManagerImplTest {

    private static final Long MEETING_ROOM_ID = 100L;
    private static final String ROOM_NAME = "meeting-100";

    @Mock
    private RoomServiceClient roomServiceClient;

    @Mock
    private LiveKitNameGenerator liveKitNameGenerator;

    @Mock
    private Call<Void> deleteRoomCall;

    @InjectMocks
    private LiveKitRoomManagerImpl liveKitRoomManager;

    @Test
    @DisplayName("LiveKit 회의방과 모든 참여자 연결을 종료한다")
    void endRoom_deletesLiveKitRoom() throws IOException {
        givenDeleteRoomCall();
        given(deleteRoomCall.execute())
                .willReturn(Response.<Void>success(null));

        assertThatCode(() -> liveKitRoomManager.endRoom(MEETING_ROOM_ID))
                .doesNotThrowAnyException();

        verify(roomServiceClient).deleteRoom(ROOM_NAME);
    }

    @Test
    @DisplayName("LiveKit 방이 없거나 이미 종료된 404 응답은 성공으로 처리한다")
    void endRoom_acceptsNotFoundResponse() throws IOException {
        givenDeleteRoomCall();
        given(deleteRoomCall.execute()).willReturn(errorResponse(404));

        assertThatCode(() -> liveKitRoomManager.endRoom(MEETING_ROOM_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LiveKit이 오류 응답을 반환하면 도메인 예외가 발생한다")
    void endRoom_rejectsLiveKitErrorResponse() throws IOException {
        givenDeleteRoomCall();
        given(deleteRoomCall.execute()).willReturn(errorResponse(500));

        assertRoomEndFailed();
    }

    @Test
    @DisplayName("LiveKit 통신 중 IOException이 발생하면 도메인 예외가 발생한다")
    void endRoom_rejectsCommunicationFailure() throws IOException {
        givenDeleteRoomCall();
        given(deleteRoomCall.execute())
                .willThrow(new IOException("network error"));

        assertRoomEndFailed();
    }

    private void givenDeleteRoomCall() {
        given(liveKitNameGenerator.generateMeetingRoomName(MEETING_ROOM_ID))
                .willReturn(ROOM_NAME);
        given(roomServiceClient.deleteRoom(ROOM_NAME))
                .willReturn(deleteRoomCall);
    }

    private Response<Void> errorResponse(int status) {
        ResponseBody responseBody = ResponseBody.create(
                "{}",
                MediaType.get("application/json")
        );
        return Response.error(status, responseBody);
    }

    private void assertRoomEndFailed() {
        assertThatThrownBy(() -> liveKitRoomManager.endRoom(MEETING_ROOM_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(exception ->
                        assertThat(((CustomException) exception).getErrorCode())
                                .isEqualTo(
                                        ErrorCode.MEETING_LIVEKIT_ROOM_END_FAILED
                                ));
    }
}
