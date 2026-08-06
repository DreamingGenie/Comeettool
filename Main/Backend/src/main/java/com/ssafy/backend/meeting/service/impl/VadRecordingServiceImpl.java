package com.ssafy.backend.meeting.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.storage.ObjectStorageService;
import com.ssafy.backend.global.storage.StorageDirectory;
import com.ssafy.backend.global.storage.StorageObjectKey;
import com.ssafy.backend.global.storage.StorageUploadRequest;
import com.ssafy.backend.meeting.dto.ResponseVadRecordingDto;
import com.ssafy.backend.meeting.dto.ResponseVadSequenceConflictDto;
import com.ssafy.backend.meeting.dto.VadSegmentMetadataDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.repository.ParticipantRepository;
import com.ssafy.backend.meeting.service.VadRecordingService;
import com.ssafy.backend.meeting.storage.ConferenceSegmentObjectKey;
import com.ssafy.backend.meeting.vad.VadParticipantUploadLock;
import com.ssafy.backend.meeting.vad.VadUploadFlightTracker;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VadRecordingServiceImpl implements VadRecordingService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String AUDIO_CONTENT_TYPE = "audio/ogg";
    private static final String METADATA_CONTENT_TYPE = "application/json";
    private static final Pattern SEGMENT_JSON =
            Pattern.compile("segment-(\\d{6})\\.json");

    private final MeetingRoomRepository meetingRoomRepository;
    private final MemberRepository memberRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ObjectStorageService objectStorageService;
    private final VadUploadFlightTracker vadUploadFlightTracker;
    private final VadParticipantUploadLock vadParticipantUploadLock;
    private final ObjectMapper objectMapper;

    @Value("${meeting.vad.max-audio-bytes:5242880}")
    private long maxAudioBytes;

    @Override
    @Transactional(readOnly = true)
    public ResponseVadRecordingDto addVadRecording(
            Long requesterUserId,
            Long meetingId,
            Integer sequence,
            Long startedAt,
            Long endedAt,
            MultipartFile audio
    ) {
        validateRequest(sequence, startedAt, endedAt, audio);

        MeetingRoom meetingRoom = meetingRoomRepository.findActiveById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        Member member = memberRepository
                .findByTeamIdAndUserId(meetingRoom.getTeamId(), requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        Participant participant = participantRepository
                .findByMeetingRoomIdAndMemberId(meetingId, member.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        if (!participant.isInMeeting()) {
            throw new CustomException(ErrorCode.MEETING_ACCESS_DENIED);
        }

        User user = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String username = user.getNickname();

        long participantId = participant.getId();
        long durationMs = endedAt - startedAt;

        StorageObjectKey audioKey =
                ConferenceSegmentObjectKey.audio(meetingId, participantId, sequence);
        StorageObjectKey metadataKey =
                ConferenceSegmentObjectKey.metadata(meetingId, participantId, sequence);

        byte[] audioBytes = readAudioBytes(audio);
        String audioSha256 = sha256Hex(audioBytes);

        vadUploadFlightTracker.begin(meetingId);
        try {
            synchronized (vadParticipantUploadLock.lockFor(meetingId, participantId)) {
                return storeOrResolve(
                        meetingId,
                        participantId,
                        username,
                        sequence,
                        startedAt,
                        endedAt,
                        durationMs,
                        audioBytes,
                        audioSha256,
                        audioKey,
                        metadataKey
                );
            }
        } finally {
            vadUploadFlightTracker.end(meetingId);
        }
    }

    private ResponseVadRecordingDto storeOrResolve(
            long meetingId,
            long participantId,
            String username,
            int sequence,
            long startedAt,
            long endedAt,
            long durationMs,
            byte[] audioBytes,
            String audioSha256,
            StorageObjectKey audioKey,
            StorageObjectKey metadataKey
    ) {
        boolean metadataExists = storageExists(metadataKey);
        boolean audioExists = storageExists(audioKey);

        if (metadataExists) {
            VadSegmentMetadataDto existing = readMetadata(metadataKey);
            if (isSameChunk(existing, startedAt, endedAt, audioSha256)) {
                return toResponse(existing, metadataKey.value());
            }
            throw conflict(meetingId, participantId);
        }

        if (audioExists) {
            byte[] existingAudio = downloadBytes(audioKey);
            if (!sha256Hex(existingAudio).equals(audioSha256)) {
                throw conflict(meetingId, participantId);
            }
        } else {
            boolean created = storageUploadIfAbsent(audioKey, audioBytes, AUDIO_CONTENT_TYPE);
            if (!created) {
                byte[] existingAudio = downloadBytes(audioKey);
                if (!sha256Hex(existingAudio).equals(audioSha256)) {
                    throw conflict(meetingId, participantId);
                }
            }
        }

        OffsetDateTime uploadedAt = OffsetDateTime.now(KST);
        VadSegmentMetadataDto metadata = new VadSegmentMetadataDto(
                meetingId,
                participantId,
                username,
                sequence,
                startedAt,
                endedAt,
                durationMs,
                audioSha256,
                audioKey.value(),
                uploadedAt
        );

        byte[] metadataBytes = writeJson(metadata);
        boolean metadataCreated =
                storageUploadIfAbsent(metadataKey, metadataBytes, METADATA_CONTENT_TYPE);
        if (!metadataCreated) {
            VadSegmentMetadataDto existing = readMetadata(metadataKey);
            if (isSameChunk(existing, startedAt, endedAt, audioSha256)) {
                return toResponse(existing, metadataKey.value());
            }
            throw conflict(meetingId, participantId);
        }

        return toResponse(metadata, metadataKey.value());
    }

    private void validateRequest(
            Integer sequence,
            Long startedAt,
            Long endedAt,
            MultipartFile audio
    ) {
        if (sequence == null || sequence < 1) {
            throw new CustomException(ErrorCode.VAD_SEQUENCE_INVALID);
        }
        if (startedAt == null || endedAt == null || endedAt <= startedAt) {
            throw new CustomException(ErrorCode.VAD_TIME_RANGE_INVALID);
        }
        if (audio == null || audio.isEmpty()) {
            throw new CustomException(ErrorCode.VAD_AUDIO_REQUIRED);
        }
        if (audio.getSize() > maxAudioBytes) {
            throw new CustomException(ErrorCode.VAD_AUDIO_TOO_LARGE);
        }
    }

    private CustomException conflict(long meetingId, long participantId) {
        int expected = nextExpectedSequence(meetingId, participantId);
        return new CustomException(
                ErrorCode.VAD_SEQUENCE_CONFLICT,
                new ResponseVadSequenceConflictDto(expected)
        );
    }

    private int nextExpectedSequence(long meetingId, long participantId) {
        int max = 0;
        for (String objectName : listSegmentNames(meetingId, participantId)) {
            Matcher matcher = SEGMENT_JSON.matcher(objectName);
            if (matcher.matches()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return max + 1;
    }

    private VadSegmentMetadataDto readMetadata(StorageObjectKey metadataKey) {
        byte[] bytes = downloadBytes(metadataKey);
        try {
            return objectMapper.readValue(bytes, VadSegmentMetadataDto.class);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.VAD_STORAGE_FAILED);
        }
    }

    private byte[] writeJson(VadSegmentMetadataDto metadata) {
        try {
            return objectMapper.writeValueAsBytes(metadata);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.VAD_STORAGE_FAILED);
        }
    }

    private boolean isSameChunk(
            VadSegmentMetadataDto existing,
            long startedAt,
            long endedAt,
            String audioSha256
    ) {
        return existing.startedAt() == startedAt
                && existing.endedAt() == endedAt
                && audioSha256.equals(existing.audioSha256());
    }

    private ResponseVadRecordingDto toResponse(
            VadSegmentMetadataDto metadata,
            String metadataKey
    ) {
        return new ResponseVadRecordingDto(
                metadata.meetingRoomId(),
                metadata.participantId(),
                metadata.sequence(),
                metadata.audioObjectKey(),
                metadataKey,
                metadata.durationMs(),
                metadata.uploadedAt(),
                metadata.username()
        );
    }

    private boolean storageExists(StorageObjectKey objectKey) {
        try {
            return objectStorageService.exists(objectKey);
        } catch (CustomException e) {
            throw new CustomException(ErrorCode.VAD_STORAGE_FAILED);
        }
    }

    private byte[] downloadBytes(StorageObjectKey objectKey) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            objectStorageService.download(objectKey, buffer);
            return buffer.toByteArray();
        } catch (CustomException e) {
            throw new CustomException(ErrorCode.VAD_STORAGE_FAILED);
        }
    }

    private boolean storageUploadIfAbsent(
            StorageObjectKey objectKey,
            byte[] bytes,
            String contentType
    ) {
        try {
            StorageUploadRequest request = new StorageUploadRequest(
                    objectKey,
                    bytes.length,
                    contentType,
                    null
            );
            return objectStorageService.uploadIfAbsent(request, new ByteArrayInputStream(bytes));
        } catch (CustomException e) {
            throw new CustomException(ErrorCode.VAD_STORAGE_FAILED);
        }
    }

    private List<String> listSegmentNames(long meetingId, long participantId) {
        try {
            return objectStorageService.list(
                    StorageDirectory.CONFERENCES,
                    Long.toString(meetingId),
                    "participants",
                    Long.toString(participantId)
            );
        } catch (CustomException e) {
            throw new CustomException(ErrorCode.VAD_STORAGE_FAILED);
        }
    }

    private byte[] readAudioBytes(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (Exception e) {
            throw new CustomException(ErrorCode.VAD_AUDIO_REQUIRED);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
