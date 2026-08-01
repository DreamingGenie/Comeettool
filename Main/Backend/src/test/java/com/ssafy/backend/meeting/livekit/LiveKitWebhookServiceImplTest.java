package com.ssafy.backend.meeting.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.repository.ParticipantRepository;

import io.livekit.server.WebhookReceiver;
import livekit.LivekitModels;
import livekit.LivekitWebhook;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiveKit 웹훅 서비스 테스트")
class LiveKitWebhookServiceImplTest {

    private static final String RAW_BODY = "{\"event\":\"participant_left\"}";
    private static final String AUTHORIZATION_HEADER = "Bearer livekit-signature";
    private static final Long MEETING_ROOM_ID = 100L;
    private static final Long PARTICIPANT_ID = 30L;

    @Mock
    private WebhookReceiver webhookReceiver;

    @Mock
    private ParticipantRepository participantRepository;

    private final LiveKitNameGenerator liveKitNameGenerator =
            new LiveKitNameGenerator();

    @InjectMocks
    private LiveKitWebhookServiceImpl liveKitWebhookService;

    @BeforeEach
    void setUp() {
        liveKitWebhookService = new LiveKitWebhookServiceImpl(
                webhookReceiver,
                liveKitNameGenerator,
                participantRepository
        );
    }

    @Test
    @DisplayName("participant_left 이벤트는 Participant 입장 상태를 false로 변경한다")
    void handle_changesParticipantPresenceForParticipantLeft() {
        Participant participant = createParticipant(true);
        LivekitWebhook.WebhookEvent webhookEvent =
                createWebhookEvent("participant_left");

        given(webhookReceiver.receive(RAW_BODY, AUTHORIZATION_HEADER))
                .willReturn(webhookEvent);
        given(participantRepository.findByIdAndMeetingRoomId(
                PARTICIPANT_ID,
                MEETING_ROOM_ID
        )).willReturn(Optional.of(participant));

        liveKitWebhookService.handle(RAW_BODY, AUTHORIZATION_HEADER);

        assertThat(participant.isInMeeting()).isFalse();
    }

    @Test
    @DisplayName("이미 퇴장한 Participant의 중복 이벤트도 멱등 처리한다")
    void handle_isIdempotentForAlreadyLeftParticipant() {
        Participant participant = createParticipant(false);
        LivekitWebhook.WebhookEvent webhookEvent =
                createWebhookEvent("participant_left");

        given(webhookReceiver.receive(RAW_BODY, AUTHORIZATION_HEADER))
                .willReturn(webhookEvent);
        given(participantRepository.findByIdAndMeetingRoomId(
                PARTICIPANT_ID,
                MEETING_ROOM_ID
        )).willReturn(Optional.of(participant));

        liveKitWebhookService.handle(RAW_BODY, AUTHORIZATION_HEADER);

        assertThat(participant.isInMeeting()).isFalse();
    }

    @Test
    @DisplayName("동일 identity의 새 연결이 기존 연결을 대체한 퇴장 이벤트는 무시한다")
    void handle_ignoresDuplicateIdentityDisconnect() {
        LivekitWebhook.WebhookEvent webhookEvent = createWebhookEvent(
                "participant_left",
                LivekitModels.DisconnectReason.DUPLICATE_IDENTITY
        );
        given(webhookReceiver.receive(RAW_BODY, AUTHORIZATION_HEADER))
                .willReturn(webhookEvent);

        liveKitWebhookService.handle(RAW_BODY, AUTHORIZATION_HEADER);

        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("이번 범위가 아닌 participant_joined 이벤트는 상태를 변경하지 않는다")
    void handle_ignoresParticipantJoinedUntilJoinMigration() {
        given(webhookReceiver.receive(RAW_BODY, AUTHORIZATION_HEADER))
                .willReturn(createWebhookEvent("participant_joined"));

        liveKitWebhookService.handle(RAW_BODY, AUTHORIZATION_HEADER);

        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("room_finished 이벤트는 회의의 모든 Participant를 퇴장 처리한다")
    void handle_leavesAllParticipantsForRoomFinished() {
        given(webhookReceiver.receive(RAW_BODY, AUTHORIZATION_HEADER))
                .willReturn(createRoomFinishedEvent("meeting-100"));

        liveKitWebhookService.handle(RAW_BODY, AUTHORIZATION_HEADER);

        verify(participantRepository)
                .leaveAllByMeetingRoomId(MEETING_ROOM_ID);
    }

    @Test
    @DisplayName("프로젝트 형식이 아닌 room_finished 방 이름은 무시한다")
    void handle_ignoresRoomFinishedWithInvalidRoomName() {
        given(webhookReceiver.receive(RAW_BODY, AUTHORIZATION_HEADER))
                .willReturn(createRoomFinishedEvent("unknown-room"));

        liveKitWebhookService.handle(RAW_BODY, AUTHORIZATION_HEADER);

        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("프로젝트 규칙과 다른 roomName 또는 identity는 무시한다")
    void handle_ignoresInvalidProjectIdentifier() {
        LivekitWebhook.WebhookEvent webhookEvent =
                LivekitWebhook.WebhookEvent.newBuilder()
                        .setId("event-id")
                        .setEvent("participant_left")
                        .setRoom(LivekitModels.Room.newBuilder()
                                .setName("unknown-room"))
                        .setParticipant(
                                LivekitModels.ParticipantInfo.newBuilder()
                                        .setIdentity("unknown-participant")
                        )
                        .build();
        given(webhookReceiver.receive(RAW_BODY, AUTHORIZATION_HEADER))
                .willReturn(webhookEvent);

        liveKitWebhookService.handle(RAW_BODY, AUTHORIZATION_HEADER);

        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("LiveKit 서명 검증 실패는 401 도메인 예외로 변환한다")
    void handle_rejectsInvalidSignature() {
        willThrow(new IllegalArgumentException("invalid signature"))
                .given(webhookReceiver)
                .receive(RAW_BODY, AUTHORIZATION_HEADER);

        assertThatThrownBy(() -> liveKitWebhookService.handle(
                RAW_BODY,
                AUTHORIZATION_HEADER
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEETING_LIVEKIT_WEBHOOK_UNAUTHORIZED);
        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 서명 검증 전에 거부한다")
    void handle_rejectsMissingAuthorizationHeader() {
        assertThatThrownBy(() -> liveKitWebhookService.handle(RAW_BODY, null))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEETING_LIVEKIT_WEBHOOK_UNAUTHORIZED);
        verifyNoInteractions(webhookReceiver, participantRepository);
    }

    private Participant createParticipant(boolean isInMeeting) {
        return Participant.builder()
                .meetingRoomId(MEETING_ROOM_ID)
                .memberId(20L)
                .participantRole("BE")
                .isInMeeting(isInMeeting)
                .build();
    }

    private LivekitWebhook.WebhookEvent createWebhookEvent(String eventName) {
        return createWebhookEvent(
                eventName,
                LivekitModels.DisconnectReason.UNKNOWN_REASON
        );
    }

    private LivekitWebhook.WebhookEvent createWebhookEvent(
            String eventName,
            LivekitModels.DisconnectReason disconnectReason
    ) {
        return LivekitWebhook.WebhookEvent.newBuilder()
                .setId("event-id")
                .setEvent(eventName)
                .setRoom(LivekitModels.Room.newBuilder()
                        .setName("meeting-" + MEETING_ROOM_ID))
                .setParticipant(
                        LivekitModels.ParticipantInfo.newBuilder()
                                .setIdentity("participant-" + PARTICIPANT_ID)
                                .setDisconnectReason(disconnectReason)
                )
                .build();
    }

    private LivekitWebhook.WebhookEvent createRoomFinishedEvent(
            String roomName
    ) {
        return LivekitWebhook.WebhookEvent.newBuilder()
                .setId("event-id")
                .setEvent("room_finished")
                .setRoom(LivekitModels.Room.newBuilder()
                        .setName(roomName))
                .build();
    }
}
