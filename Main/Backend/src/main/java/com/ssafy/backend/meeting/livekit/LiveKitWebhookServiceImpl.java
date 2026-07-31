package com.ssafy.backend.meeting.livekit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.repository.ParticipantRepository;

import io.livekit.server.WebhookReceiver;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitWebhookServiceImpl implements LiveKitWebhookService {

    private static final String PARTICIPANT_LEFT_EVENT = "participant_left";

    private final WebhookReceiver webhookReceiver;
    private final LiveKitNameGenerator liveKitNameGenerator;
    private final ParticipantRepository participantRepository;

    @Override
    @Transactional
    public void handle(String rawBody, String authorizationHeader) {
        LivekitWebhook.WebhookEvent webhookEvent =
                receiveWebhookEvent(rawBody, authorizationHeader);

        if (!PARTICIPANT_LEFT_EVENT.equals(webhookEvent.getEvent())) {
            return;
        }

        handleParticipantLeft(webhookEvent);
    }

    private LivekitWebhook.WebhookEvent receiveWebhookEvent(
            String rawBody,
            String authorizationHeader
    ) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new CustomException(
                    ErrorCode.MEETING_LIVEKIT_WEBHOOK_UNAUTHORIZED
            );
        }

        try {
            return webhookReceiver.receive(rawBody, authorizationHeader);
        } catch (Exception exception) {
            log.warn("LiveKit 웹훅 서명 검증 실패", exception);
            throw new CustomException(
                    ErrorCode.MEETING_LIVEKIT_WEBHOOK_UNAUTHORIZED
            );
        }
    }

    private void handleParticipantLeft(
            LivekitWebhook.WebhookEvent webhookEvent
    ) {
        if (!webhookEvent.hasRoom() || !webhookEvent.hasParticipant()) {
            log.warn(
                    "LiveKit participant_left 필수 정보 누락: eventId={}",
                    webhookEvent.getId()
            );
            return;
        }

        LivekitModels.ParticipantInfo participantInfo =
                webhookEvent.getParticipant();
        if (participantInfo.getDisconnectReason()
                == LivekitModels.DisconnectReason.DUPLICATE_IDENTITY) {
            return;
        }

        Long meetingRoomId;
        Long participantId;
        try {
            meetingRoomId = liveKitNameGenerator.parseMeetingRoomId(
                    webhookEvent.getRoom().getName()
            );
            participantId = liveKitNameGenerator.parseParticipantId(
                    participantInfo.getIdentity()
            );
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "프로젝트 형식이 아닌 LiveKit 식별자: eventId={}, roomName={}, identity={}",
                    webhookEvent.getId(),
                    webhookEvent.getRoom().getName(),
                    participantInfo.getIdentity()
            );
            return;
        }

        Participant participant = participantRepository
                .findByIdAndMeetingRoomId(participantId, meetingRoomId)
                .orElse(null);
        if (participant == null) {
            log.warn(
                    "LiveKit 퇴장 대상 Participant 없음: eventId={}, meetingRoomId={}, participantId={}",
                    webhookEvent.getId(),
                    meetingRoomId,
                    participantId
            );
            return;
        }

        participant.leaveMeeting();
    }
}
