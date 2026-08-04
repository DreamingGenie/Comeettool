package com.ssafy.backend.meeting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.ResponseVadRecordingDto;
import com.ssafy.backend.meeting.dto.ResponseVadSequenceConflictDto;
import com.ssafy.backend.meeting.dto.VadSegmentMetadataDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.repository.ParticipantRepository;
import com.ssafy.backend.meeting.storage.RecordingObjectStorage;
import com.ssafy.backend.meeting.vad.VadParticipantUploadLock;
import com.ssafy.backend.meeting.vad.VadUploadFlightTracker;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("MEET-12 VAD 업로드 서비스 테스트")
class VadRecordingServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long TEAM_ID = 10L;
    private static final Long MEETING_ID = 3L;
    private static final Long MEMBER_ID = 20L;
    private static final Long PARTICIPANT_ID = 12L;

    @Mock
    private MeetingRoomRepository meetingRoomRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RecordingObjectStorage recordingObjectStorage;

    private VadUploadFlightTracker vadUploadFlightTracker;
    private VadParticipantUploadLock vadParticipantUploadLock;
    private ObjectMapper objectMapper;
    private VadRecordingServiceImpl service;

    @BeforeEach
    void setUp() {
        vadUploadFlightTracker = new VadUploadFlightTracker(
                Duration.ofSeconds(2),
                Duration.ofMillis(50)
        );
        vadParticipantUploadLock = new VadParticipantUploadLock();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new VadRecordingServiceImpl(
                meetingRoomRepository,
                memberRepository,
                participantRepository,
                userRepository,
                recordingObjectStorage,
                vadUploadFlightTracker,
                vadParticipantUploadLock,
                objectMapper
        );
        ReflectionTestUtils.setField(service, "maxAudioBytes", 5_242_880L);
    }

    @Test
    @DisplayName("신규 청크를 저장하고 users.nickname을 username으로 반환한다")
    void addVadRecording_storesNewChunkWithUserNickname() throws Exception {
        stubAuthorizedParticipant();
        byte[] audio = "ogg-bytes".getBytes(StandardCharsets.UTF_8);
        String audioKey = "conferences/3/participants/12/segment-000001.ogg";
        String metadataKey = "conferences/3/participants/12/segment-000001.json";

        given(recordingObjectStorage.exists(metadataKey)).willReturn(false);
        given(recordingObjectStorage.exists(audioKey)).willReturn(false);
        given(recordingObjectStorage.putIfAbsent(eq(audioKey), any(), eq("audio/ogg")))
                .willReturn(true);
        given(recordingObjectStorage.putIfAbsent(eq(metadataKey), any(), eq("application/json")))
                .willReturn(true);

        ResponseVadRecordingDto response = service.addVadRecording(
                USER_ID,
                MEETING_ID,
                1,
                1785551200000L,
                1785551205800L,
                new MockMultipartFile("audio", "segment.ogg", "audio/ogg", audio)
        );

        assertThat(response.meetingRoomId()).isEqualTo(MEETING_ID);
        assertThat(response.participantId()).isEqualTo(PARTICIPANT_ID);
        assertThat(response.sequence()).isEqualTo(1);
        assertThat(response.username()).isEqualTo("junho");
        assertThat(response.audioObjectKey()).isEqualTo(audioKey);
        assertThat(response.metadataObjectKey()).isEqualTo(metadataKey);
        assertThat(response.durationMs()).isEqualTo(5800L);
    }

    @Test
    @DisplayName("동일 청크 재시도는 멱등 성공한다")
    void addVadRecording_isIdempotentForSameChunk() throws Exception {
        stubAuthorizedParticipant();
        byte[] audio = "ogg-bytes".getBytes(StandardCharsets.UTF_8);
        String sha = sha256Hex(audio);
        String audioKey = "conferences/3/participants/12/segment-000001.ogg";
        String metadataKey = "conferences/3/participants/12/segment-000001.json";
        VadSegmentMetadataDto existing = new VadSegmentMetadataDto(
                MEETING_ID,
                PARTICIPANT_ID,
                "junho",
                1,
                1785551200000L,
                1785551205800L,
                5800L,
                sha,
                audioKey,
                OffsetDateTime.parse("2026-08-01T15:25:17.64601+09:00")
        );

        given(recordingObjectStorage.exists(metadataKey)).willReturn(true);
        given(recordingObjectStorage.getBytes(metadataKey))
                .willReturn(Optional.of(objectMapper.writeValueAsBytes(existing)));

        ResponseVadRecordingDto response = service.addVadRecording(
                USER_ID,
                MEETING_ID,
                1,
                1785551200000L,
                1785551205800L,
                new MockMultipartFile("audio", "segment.ogg", "audio/ogg", audio)
        );

        assertThat(response.sequence()).isEqualTo(1);
        assertThat(response.uploadedAt()).isEqualTo(existing.uploadedAt());
        verify(recordingObjectStorage, never()).putIfAbsent(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("다른 청크가 같은 sequence면 409와 expectedSequence를 반환한다")
    void addVadRecording_conflictsWhenDifferentChunkUsesSameSequence() throws Exception {
        stubAuthorizedParticipant();
        byte[] audio = "new-audio".getBytes(StandardCharsets.UTF_8);
        String metadataKey = "conferences/3/participants/12/segment-000001.json";
        VadSegmentMetadataDto existing = new VadSegmentMetadataDto(
                MEETING_ID,
                PARTICIPANT_ID,
                "junho",
                1,
                1L,
                2L,
                1L,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "conferences/3/participants/12/segment-000001.ogg",
                OffsetDateTime.parse("2026-08-01T15:25:17.64601+09:00")
        );

        given(recordingObjectStorage.exists(metadataKey)).willReturn(true);
        given(recordingObjectStorage.getBytes(metadataKey))
                .willReturn(Optional.of(objectMapper.writeValueAsBytes(existing)));
        given(recordingObjectStorage.listKeys("conferences/3/participants/12/"))
                .willReturn(List.of(
                        "conferences/3/participants/12/segment-000001.json",
                        "conferences/3/participants/12/segment-000015.json"
                ));

        assertThatThrownBy(() -> service.addVadRecording(
                USER_ID,
                MEETING_ID,
                1,
                1785551200000L,
                1785551205800L,
                new MockMultipartFile("audio", "segment.ogg", "audio/ogg", audio)
        ))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException custom = (CustomException) ex;
                    assertThat(custom.getErrorCode()).isEqualTo(ErrorCode.VAD_SEQUENCE_CONFLICT);
                    assertThat(custom.getData())
                            .isEqualTo(new ResponseVadSequenceConflictDto(16));
                });
    }

    @Test
    @DisplayName("sequence가 1 미만이면 400이다")
    void addVadRecording_rejectsInvalidSequence() {
        assertThatThrownBy(() -> service.addVadRecording(
                USER_ID,
                MEETING_ID,
                0,
                1L,
                2L,
                new MockMultipartFile("audio", "a.ogg", "audio/ogg", new byte[]{1})
        ))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VAD_SEQUENCE_INVALID);
    }

    private void stubAuthorizedParticipant() {
        MeetingRoom meetingRoom = MeetingRoom.builder()
                .teamId(TEAM_ID)
                .hostId(MEMBER_ID)
                .name("회의")
                .build();
        ReflectionTestUtils.setField(meetingRoom, "id", MEETING_ID);

        Member member = Member.builder()
                .userId(USER_ID)
                .teamId(TEAM_ID)
                .authority(MemberAuthority.MEMBER)
                .nickname("space-nick")
                .build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);

        Participant participant = Participant.builder()
                .meetingRoomId(MEETING_ID)
                .memberId(MEMBER_ID)
                .participantRole("BE")
                .isInMeeting(true)
                .build();
        ReflectionTestUtils.setField(participant, "id", PARTICIPANT_ID);

        User user = User.builder()
                .email("junho@test.com")
                .password("hash")
                .nickname("junho")
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        given(meetingRoomRepository.findActiveById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(Optional.of(member));
        given(participantRepository.findByMeetingRoomIdAndMemberId(MEETING_ID, MEMBER_ID))
                .willReturn(Optional.of(participant));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
