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
@DisplayName("LiveKit 참여자 관리 테스트")
class LiveKitParticipantManagerImplTest {

    private static final Long MEETING_ROOM_ID = 100L;
    private static final Long PARTICIPANT_ID = 30L;
    private static final String ROOM_NAME = "meeting-100";
    private static final String PARTICIPANT_IDENTITY = "participant-30";

    @Mock
    private RoomServiceClient roomServiceClient;

    @Mock
    private LiveKitNameGenerator liveKitNameGenerator;

    @Mock
    private Call<Void> removeParticipantCall;

    @InjectMocks
    private LiveKitParticipantManagerImpl liveKitParticipantManager;

    @Test
    @DisplayName("LiveKit 참여자 연결을 종료한다")
    void disconnectParticipant_removesLiveKitParticipant() throws IOException {
        givenLiveKitNames();
        given(roomServiceClient.removeParticipant(
                ROOM_NAME,
                PARTICIPANT_IDENTITY
        )).willReturn(removeParticipantCall);
        given(removeParticipantCall.execute())
                .willReturn(Response.<Void>success(null));

        assertThatCode(() -> liveKitParticipantManager.disconnectParticipant(
                MEETING_ROOM_ID,
                PARTICIPANT_ID
        )).doesNotThrowAnyException();

        verify(roomServiceClient)
                .removeParticipant(ROOM_NAME, PARTICIPANT_IDENTITY);
    }

    @Test
    @DisplayName("이미 LiveKit에서 퇴장한 참여자의 404 응답은 성공으로 처리한다")
    void disconnectParticipant_acceptsNotFoundResponse() throws IOException {
        givenLiveKitNames();
        given(roomServiceClient.removeParticipant(
                ROOM_NAME,
                PARTICIPANT_IDENTITY
        )).willReturn(removeParticipantCall);
        given(removeParticipantCall.execute())
                .willReturn(errorResponse(404));

        assertThatCode(() -> liveKitParticipantManager.disconnectParticipant(
                MEETING_ROOM_ID,
                PARTICIPANT_ID
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LiveKit이 오류 응답을 반환하면 도메인 예외가 발생한다")
    void disconnectParticipant_rejectsLiveKitErrorResponse() throws IOException {
        givenLiveKitNames();
        given(roomServiceClient.removeParticipant(
                ROOM_NAME,
                PARTICIPANT_IDENTITY
        )).willReturn(removeParticipantCall);
        given(removeParticipantCall.execute())
                .willReturn(errorResponse(500));

        assertDisconnectFailed();
    }

    @Test
    @DisplayName("LiveKit 통신 중 IOException이 발생하면 도메인 예외가 발생한다")
    void disconnectParticipant_rejectsCommunicationFailure() throws IOException {
        givenLiveKitNames();
        given(roomServiceClient.removeParticipant(
                ROOM_NAME,
                PARTICIPANT_IDENTITY
        )).willReturn(removeParticipantCall);
        given(removeParticipantCall.execute())
                .willThrow(new IOException("network error"));

        assertDisconnectFailed();
    }

    private void givenLiveKitNames() {
        given(liveKitNameGenerator.generateMeetingRoomName(MEETING_ROOM_ID))
                .willReturn(ROOM_NAME);
        given(liveKitNameGenerator.generateParticipantIdentity(PARTICIPANT_ID))
                .willReturn(PARTICIPANT_IDENTITY);
    }

    private Response<Void> errorResponse(int status) {
        ResponseBody responseBody = ResponseBody.create(
                "{}",
                MediaType.get("application/json")
        );
        return Response.error(status, responseBody);
    }

    private void assertDisconnectFailed() {
        assertThatThrownBy(() ->
                liveKitParticipantManager.disconnectParticipant(
                        MEETING_ROOM_ID,
                        PARTICIPANT_ID
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception ->
                        assertThat(((CustomException) exception).getErrorCode())
                                .isEqualTo(
                                        ErrorCode.MEETING_LIVEKIT_DISCONNECT_FAILED
                                ));
    }
}
