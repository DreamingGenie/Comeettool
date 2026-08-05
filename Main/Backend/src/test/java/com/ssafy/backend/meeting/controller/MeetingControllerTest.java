package com.ssafy.backend.meeting.controller;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.meeting.dto.RequestCreateMeetingDto;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseJoinMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseLeaveMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInvitationDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInviteCandidateDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingListDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseVadRecordingDto;
import com.ssafy.backend.meeting.dto.ResponseVadSequenceConflictDto;
import com.ssafy.backend.meeting.service.MeetingService;
import com.ssafy.backend.meeting.service.VadRecordingService;

/**
 * 회의 컨트롤러 단위 테스트.
 * 실제 DB·시큐리티 필터 없이 MEET-01·02·03·04·05·06·07·08·09·10 매핑과 공통 응답 형식을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("회의 API 테스트")
class MeetingControllerTest {

    private static final String HOST_USER_ID = "1";
    private static final Long SPACE_ID = 10L;
    private static final Long MEETING_ID = 100L;
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-07-30T12:00:00+09:00");

    @Mock
    private MeetingService meetingService;

    @Mock
    private VadRecordingService vadRecordingService;

    @InjectMocks
    private MeetingController meetingController;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(meetingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(HOST_USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("회의 생성에 성공하면 201과 생성 결과를 반환한다")
    void addMeeting_returns201WithCreatedMeeting() throws Exception {
        RequestCreateMeetingDto request = new RequestCreateMeetingDto("데일리 미팅");
        ResponseCreateMeetingDto response = new ResponseCreateMeetingDto(
                MEETING_ID,
                SPACE_ID,
                new ResponseMeetingHostDto(1L),
                CREATED_AT,
                1
        );
        given(meetingService.addMeeting(eq(1L), eq(SPACE_ID), any(RequestCreateMeetingDto.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/spaces/{spaceId}/meetings", SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("회의 생성 성공"))
                .andExpect(jsonPath("$.data.meetingRoomId").value(100))
                .andExpect(jsonPath("$.data.teamId").value(10))
                .andExpect(jsonPath("$.data.host.userId").value(1))
                .andExpect(jsonPath("$.data.participantCount").value(1));
    }

    @Test
    @DisplayName("회의방 이름이 공백이면 400을 반환한다")
    void addMeeting_returns400WhenMeetingRoomNameIsBlank() throws Exception {
        RequestCreateMeetingDto request = new RequestCreateMeetingDto("   ");

        mockMvc.perform(post("/api/v1/spaces/{spaceId}/meetings", SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("스페이스가 없으면 404를 반환한다")
    void addMeeting_returns404WhenSpaceIsMissing() throws Exception {
        RequestCreateMeetingDto request = new RequestCreateMeetingDto("데일리 미팅");
        given(meetingService.addMeeting(eq(1L), eq(SPACE_ID), any(RequestCreateMeetingDto.class)))
                .willThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND));

        mockMvc.perform(post("/api/v1/spaces/{spaceId}/meetings", SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("스페이스 멤버가 아니면 403을 반환한다")
    void addMeeting_returns403WhenRequesterIsNotSpaceMember() throws Exception {
        RequestCreateMeetingDto request = new RequestCreateMeetingDto("데일리 미팅");
        given(meetingService.addMeeting(eq(1L), eq(SPACE_ID), any(RequestCreateMeetingDto.class)))
                .willThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED));

        mockMvc.perform(post("/api/v1/spaces/{spaceId}/meetings", SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("게스트가 회의를 생성하면 403을 반환한다")
    void addMeeting_returns403WhenRequesterIsGuest() throws Exception {
        RequestCreateMeetingDto request = new RequestCreateMeetingDto("데일리 미팅");
        given(meetingService.addMeeting(eq(1L), eq(SPACE_ID), any(RequestCreateMeetingDto.class)))
                .willThrow(new CustomException(ErrorCode.MEETING_CREATE_FORBIDDEN));

        mockMvc.perform(post("/api/v1/spaces/{spaceId}/meetings", SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_CREATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("활성 회의가 3개이면 409를 반환한다")
    void addMeeting_returns409WhenActiveMeetingRoomLimitIsExceeded() throws Exception {
        RequestCreateMeetingDto request = new RequestCreateMeetingDto("데일리 미팅");
        given(meetingService.addMeeting(eq(1L), eq(SPACE_ID), any(RequestCreateMeetingDto.class)))
                .willThrow(new CustomException(ErrorCode.MEETING_ROOM_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/v1/spaces/{spaceId}/meetings", SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_ROOM_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("참가 중인 회의 목록을 조회하면 200과 회의 정보를 반환한다")
    void getParticipatingMeetings_returns200WithMeetingList() throws Exception {
        ResponseMeetingListDto meeting = new ResponseMeetingListDto(
                MEETING_ID,
                SPACE_ID,
                "데일리 미팅",
                new ResponseMeetingHostDto(1L),
                CREATED_AT,
                2L,
                false
        );
        given(meetingService.getParticipatingMeetings(1L, SPACE_ID))
                .willReturn(List.of(meeting));

        mockMvc.perform(get("/api/v1/spaces/{spaceId}/meetings", SPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("참가 중인 회의 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data[0].meetingRoomId").value(100))
                .andExpect(jsonPath("$.data[0].teamId").value(10))
                .andExpect(jsonPath("$.data[0].meetingRoomName").value("데일리 미팅"))
                .andExpect(jsonPath("$.data[0].host.userId").value(1))
                .andExpect(jsonPath("$.data[0].participantCount").value(2))
                .andExpect(jsonPath("$.data[0].isInMeeting").value(false));
    }

    @Test
    @DisplayName("회의 목록 조회 시 스페이스 멤버가 아니면 403을 반환한다")
    void getParticipatingMeetings_returns403WhenRequesterIsNotSpaceMember() throws Exception {
        given(meetingService.getParticipatingMeetings(1L, SPACE_ID))
                .willThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/spaces/{spaceId}/meetings", SPACE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("회의 입장에 성공하면 LiveKit 연결 정보를 반환한다")
    void joinMeeting_returns200WithLiveKitConnectionInfo() throws Exception {
        ResponseJoinMeetingDto response = new ResponseJoinMeetingDto(
                MEETING_ID,
                "BE",
                true,
                "livekit-token",
                "wss://test.livekit.cloud"
        );
        given(meetingService.joinMeeting(1L, MEETING_ID))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/join", MEETING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("회의 입장 성공"))
                .andExpect(jsonPath("$.data.meetingRoomId").value(100))
                .andExpect(jsonPath("$.data.role").value("BE"))
                .andExpect(jsonPath("$.data.isHost").value(true))
                .andExpect(jsonPath("$.data.token").value("livekit-token"))
                .andExpect(jsonPath("$.data.url").value("wss://test.livekit.cloud"));
    }

    @Test
    @DisplayName("회의에 초대되지 않은 사용자가 입장하면 403을 반환한다")
    void joinMeeting_returns403WhenAccessIsDenied() throws Exception {
        given(meetingService.joinMeeting(1L, MEETING_ID))
                .willThrow(new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/join", MEETING_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("회의 퇴장에 성공하면 isKick=false를 반환한다")
    void leaveMeeting_returns200WithVoluntaryLeaveResult() throws Exception {
        ResponseLeaveMeetingDto response = new ResponseLeaveMeetingDto(false);
        given(meetingService.leaveMeeting(1L, MEETING_ID))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/leave", MEETING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("퇴장되었습니다."))
                .andExpect(jsonPath("$.data.isKick").value(false));
    }

    @Test
    @DisplayName("회의에 초대되지 않은 사용자가 퇴장하면 403을 반환한다")
    void leaveMeeting_returns403WhenAccessIsDenied() throws Exception {
        given(meetingService.leaveMeeting(1L, MEETING_ID))
                .willThrow(new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/leave", MEETING_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("현재 호스트가 일반 퇴장을 요청하면 409를 반환한다")
    void leaveMeeting_returns409WhenRequesterIsHost() throws Exception {
        given(meetingService.leaveMeeting(1L, MEETING_ID))
                .willThrow(new CustomException(ErrorCode.MEETING_HOST_CANNOT_LEAVE));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/leave", MEETING_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_HOST_CANNOT_LEAVE"));
    }

    @Test
    @DisplayName("LiveKit 연결 종료에 실패하면 502를 반환한다")
    void leaveMeeting_returns502WhenLiveKitDisconnectFails() throws Exception {
        given(meetingService.leaveMeeting(1L, MEETING_ID))
                .willThrow(new CustomException(
                        ErrorCode.MEETING_LIVEKIT_DISCONNECT_FAILED
                ));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/leave", MEETING_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code")
                        .value("MEETING_LIVEKIT_DISCONNECT_FAILED"));
    }

    @Test
    @DisplayName("호스트가 참여자를 강퇴하면 isKick=true를 반환한다")
    void kickParticipant_returns200WithKickResult() throws Exception {
        long participantId = 30L;
        given(meetingService.kickParticipant(1L, MEETING_ID, participantId))
                .willReturn(new ResponseLeaveMeetingDto(true));

        mockMvc.perform(delete(
                        "/api/v1/meetings/{meetingId}/participants/{participantId}",
                        MEETING_ID,
                        participantId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("참여자를 강퇴했습니다."))
                .andExpect(jsonPath("$.data.isKick").value(true));

        verify(meetingService).kickParticipant(1L, MEETING_ID, participantId);
    }

    @Test
    @DisplayName("호스트가 아닌 사용자가 강퇴하면 403을 반환한다")
    void kickParticipant_returns403WhenRequesterIsNotHost() throws Exception {
        long participantId = 30L;
        given(meetingService.kickParticipant(1L, MEETING_ID, participantId))
                .willThrow(new CustomException(ErrorCode.MEETING_HOST_REQUIRED));

        mockMvc.perform(delete(
                        "/api/v1/meetings/{meetingId}/participants/{participantId}",
                        MEETING_ID,
                        participantId
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_HOST_REQUIRED"));
    }

    @Test
    @DisplayName("회의 호스트 자신을 강퇴하면 409를 반환한다")
    void kickParticipant_returns409WhenTargetIsHost() throws Exception {
        long participantId = 30L;
        given(meetingService.kickParticipant(1L, MEETING_ID, participantId))
                .willThrow(new CustomException(
                        ErrorCode.MEETING_HOST_CANNOT_BE_KICKED
                ));

        mockMvc.perform(delete(
                        "/api/v1/meetings/{meetingId}/participants/{participantId}",
                        MEETING_ID,
                        participantId
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("MEETING_HOST_CANNOT_BE_KICKED"));
    }

    @Test
    @DisplayName("호스트가 회의를 종료하면 200을 반환한다")
    void endMeeting_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/meetings/{meetingId}/end", MEETING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("회의가 종료되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(meetingService).endMeeting(1L, MEETING_ID);
    }

    @Test
    @DisplayName("호스트가 아닌 사용자가 회의를 종료하면 403을 반환한다")
    void endMeeting_returns403ForNonHost() throws Exception {
        willThrow(new CustomException(ErrorCode.MEETING_HOST_REQUIRED))
                .given(meetingService)
                .endMeeting(1L, MEETING_ID);

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/end", MEETING_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("MEETING_HOST_REQUIRED"));
    }

    @Test
    @DisplayName("활성 회의가 없으면 종료 요청에 404를 반환한다")
    void endMeeting_returns404ForMissingMeeting() throws Exception {
        willThrow(new CustomException(ErrorCode.MEETING_NOT_FOUND))
                .given(meetingService)
                .endMeeting(1L, MEETING_ID);

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/end", MEETING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("MEETING_NOT_FOUND"));
    }

    @Test
    @DisplayName("LiveKit 회의방 종료에 실패하면 502를 반환한다")
    void endMeeting_returns502ForLiveKitFailure() throws Exception {
        willThrow(new CustomException(
                ErrorCode.MEETING_LIVEKIT_ROOM_END_FAILED
        )).given(meetingService).endMeeting(1L, MEETING_ID);

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/end", MEETING_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code")
                        .value("MEETING_LIVEKIT_ROOM_END_FAILED"));
    }

    @Test
    @DisplayName("호스트 양도에 성공하면 변경 전·후 host userId를 반환한다")
    void transferHost_returns200WithTransferResult() throws Exception {
        RequestTransferHostDto request = new RequestTransferHostDto(30L);
        ResponseTransferHostDto response = new ResponseTransferHostDto(MEETING_ID, 1L, 2L);
        given(meetingService.transferHost(eq(1L), eq(MEETING_ID), any(RequestTransferHostDto.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("호스트 권한이 양도되었습니다."))
                .andExpect(jsonPath("$.data.meetingId").value(100))
                .andExpect(jsonPath("$.data.previousHostId").value(1))
                .andExpect(jsonPath("$.data.nextHostId").value(2));
    }

    @Test
    @DisplayName("새 호스트 Participant ID가 없으면 400을 반환한다")
    void transferHost_returns400WhenParticipantIdIsNull() throws Exception {
        RequestTransferHostDto request = new RequestTransferHostDto(null);

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("새 호스트 Participant ID가 0이면 400을 반환한다")
    void transferHost_returns400WhenParticipantIdIsZero() throws Exception {
        RequestTransferHostDto request = new RequestTransferHostDto(0L);

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("요청자가 호스트가 아니면 403을 반환한다")
    void transferHost_returns403ForNonHost() throws Exception {
        RequestTransferHostDto request = new RequestTransferHostDto(30L);
        given(meetingService.transferHost(eq(1L), eq(MEETING_ID), any(RequestTransferHostDto.class)))
                .willThrow(new CustomException(ErrorCode.MEETING_HOST_REQUIRED));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_HOST_REQUIRED"));
    }

    @Test
    @DisplayName("회의가 없으면 404를 반환한다")
    void transferHost_returns404WhenMeetingIsMissing() throws Exception {
        RequestTransferHostDto request = new RequestTransferHostDto(30L);
        given(meetingService.transferHost(eq(1L), eq(MEETING_ID), any(RequestTransferHostDto.class)))
                .willThrow(new CustomException(ErrorCode.MEETING_NOT_FOUND));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    @DisplayName("현재 회의 참여자 조회에 성공하면 참여자 기본 정보를 반환한다")
    void getParticipants_returns200WithCurrentParticipants() throws Exception {
        ResponseMeetingParticipantDto participant =
                new ResponseMeetingParticipantDto(
                        30L,
                        20L,
                        1L,
                        "호스트",
                        "https://example.com/host.png",
                        "BE",
                        true,
                        true
                );
        given(meetingService.getParticipants(1L, MEETING_ID))
                .willReturn(List.of(participant));

        mockMvc.perform(get("/api/v1/meetings/{meetingId}/participants", MEETING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("회의 참여자 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data[0].participantId").value(30))
                .andExpect(jsonPath("$.data[0].memberId").value(20))
                .andExpect(jsonPath("$.data[0].userId").value(1))
                .andExpect(jsonPath("$.data[0].nickname").value("호스트"))
                .andExpect(jsonPath("$.data[0].profileImageUrl")
                        .value("https://example.com/host.png"))
                .andExpect(jsonPath("$.data[0].participantRole").value("BE"))
                .andExpect(jsonPath("$.data[0].isHost").value(true))
                .andExpect(jsonPath("$.data[0].isInMeeting").value(true));
    }

    @Test
    @DisplayName("회의에 초대되지 않은 요청자의 참여자 조회는 403을 반환한다")
    void getParticipants_returns403WhenAccessIsDenied() throws Exception {
        given(meetingService.getParticipants(1L, MEETING_ID))
                .willThrow(new CustomException(ErrorCode.MEETING_ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/meetings/{meetingId}/participants", MEETING_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("초대 후보 검색에 성공하면 200과 후보 정보를 반환한다")
    void getInviteCandidates_returns200WithCandidates() throws Exception {
        ResponseMeetingInviteCandidateDto candidate =
                new ResponseMeetingInviteCandidateDto(
                        20L,
                        2L,
                        "BackendDev",
                        "backend@example.com",
                        "https://example.com/backend.png"
                );
        given(meetingService.getInviteCandidates(1L, MEETING_ID, "backend"))
                .willReturn(List.of(candidate));

        mockMvc.perform(get(
                        "/api/v1/meetings/{meetingId}/invite-candidates",
                        MEETING_ID
                ).param("keyword", "backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("초대 가능한 멤버를 조회했습니다."))
                .andExpect(jsonPath("$.data[0].memberId").value(20))
                .andExpect(jsonPath("$.data[0].userId").value(2))
                .andExpect(jsonPath("$.data[0].nickname").value("BackendDev"))
                .andExpect(jsonPath("$.data[0].email")
                        .value("backend@example.com"))
                .andExpect(jsonPath("$.data[0].profileImage")
                        .value("https://example.com/backend.png"));

        verify(meetingService).getInviteCandidates(1L, MEETING_ID, "backend");
    }

    @Test
    @DisplayName("검색어를 생략하면 빈 문자열로 전체 초대 후보를 조회한다")
    void getInviteCandidates_usesEmptyKeywordWhenKeywordIsOmitted() throws Exception {
        given(meetingService.getInviteCandidates(1L, MEETING_ID, ""))
                .willReturn(List.of());

        mockMvc.perform(get(
                        "/api/v1/meetings/{meetingId}/invite-candidates",
                        MEETING_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("초대 가능한 멤버가 없습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(meetingService).getInviteCandidates(1L, MEETING_ID, "");
    }

    @Test
    @DisplayName("검색 결과가 없으면 200과 유저를 찾지 못했다는 메시지를 반환한다")
    void getInviteCandidates_returnsNotFoundMessageWhenSearchResultIsEmpty()
            throws Exception {
        given(meetingService.getInviteCandidates(1L, MEETING_ID, "nobody"))
                .willReturn(List.of());

        mockMvc.perform(get(
                        "/api/v1/meetings/{meetingId}/invite-candidates",
                        MEETING_ID
                ).param("keyword", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("유저를 찾지 못했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("호스트가 아닌 사용자가 초대 후보를 조회하면 403을 반환한다")
    void getInviteCandidates_returns403ForNonHost() throws Exception {
        given(meetingService.getInviteCandidates(1L, MEETING_ID, ""))
                .willThrow(new CustomException(ErrorCode.MEETING_HOST_REQUIRED));

        mockMvc.perform(get(
                        "/api/v1/meetings/{meetingId}/invite-candidates",
                        MEETING_ID
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_HOST_REQUIRED"));
    }

    @Test
    @DisplayName("초대 후보 조회 시 회의가 없으면 404를 반환한다")
    void getInviteCandidates_returns404WhenMeetingIsMissing() throws Exception {
        given(meetingService.getInviteCandidates(1L, MEETING_ID, ""))
                .willThrow(new CustomException(ErrorCode.MEETING_NOT_FOUND));

        mockMvc.perform(get(
                        "/api/v1/meetings/{meetingId}/invite-candidates",
                        MEETING_ID
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    @DisplayName("회의 초대에 성공하면 201과 생성된 Participant 정보를 반환한다")
    void inviteMember_returns201WithInvitation() throws Exception {
        ResponseMeetingInvitationDto response =
                new ResponseMeetingInvitationDto(
                        40L,
                        MEETING_ID,
                        20L,
                        2L,
                        "BE",
                        false
                );
        given(meetingService.inviteMember(1L, MEETING_ID, 2L))
                .willReturn(response);

        mockMvc.perform(post(
                        "/api/v1/meetings/{meetingId}/invitations/users/{inviteeUserId}",
                        MEETING_ID,
                        2L
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("회의 멤버 초대 성공"))
                .andExpect(jsonPath("$.data.participantId").value(40))
                .andExpect(jsonPath("$.data.meetingRoomId").value(100))
                .andExpect(jsonPath("$.data.memberId").value(20))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.participantRole").value("BE"))
                .andExpect(jsonPath("$.data.isInMeeting").value(false));

        verify(meetingService).inviteMember(1L, MEETING_ID, 2L);
    }

    @Test
    @DisplayName("호스트가 아닌 사용자가 회의 초대를 요청하면 403을 반환한다")
    void inviteMember_returns403ForNonHost() throws Exception {
        given(meetingService.inviteMember(1L, MEETING_ID, 2L))
                .willThrow(new CustomException(ErrorCode.MEETING_HOST_REQUIRED));

        mockMvc.perform(post(
                        "/api/v1/meetings/{meetingId}/invitations/users/{inviteeUserId}",
                        MEETING_ID,
                        2L
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_HOST_REQUIRED"));
    }

    @Test
    @DisplayName("회의 초대 시 회의가 없으면 404를 반환한다")
    void inviteMember_returns404WhenMeetingIsMissing() throws Exception {
        given(meetingService.inviteMember(1L, MEETING_ID, 2L))
                .willThrow(new CustomException(ErrorCode.MEETING_NOT_FOUND));

        mockMvc.perform(post(
                        "/api/v1/meetings/{meetingId}/invitations/users/{inviteeUserId}",
                        MEETING_ID,
                        2L
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    @DisplayName("회의 상위 팀의 멤버가 아니면 초대 요청에 404를 반환한다")
    void inviteMember_returns404WhenInviteeIsOutsideMeetingTeam() throws Exception {
        given(meetingService.inviteMember(1L, MEETING_ID, 2L))
                .willThrow(new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND));

        mockMvc.perform(post(
                        "/api/v1/meetings/{meetingId}/invitations/users/{inviteeUserId}",
                        MEETING_ID,
                        2L
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 초대된 멤버를 다시 초대하면 409를 반환한다")
    void inviteMember_returns409WhenMemberIsAlreadyInvited() throws Exception {
        given(meetingService.inviteMember(1L, MEETING_ID, 2L))
                .willThrow(new CustomException(ErrorCode.MEETING_ALREADY_INVITED));

        mockMvc.perform(post(
                        "/api/v1/meetings/{meetingId}/invitations/users/{inviteeUserId}",
                        MEETING_ID,
                        2L
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_ALREADY_INVITED"));
    }

    @Test
    @DisplayName("MEET-12 VAD 업로드 성공 시 200과 응답 데이터를 반환한다")
    void addVadRecording_returns200OnSuccess() throws Exception {
        ResponseVadRecordingDto response = new ResponseVadRecordingDto(
                MEETING_ID,
                12L,
                1,
                "conferences/100/participants/12/segment-000001.ogg",
                "conferences/100/participants/12/segment-000001.json",
                5800L,
                OffsetDateTime.parse("2026-08-01T15:25:17.64601+09:00"),
                "junho"
        );
        given(vadRecordingService.addVadRecording(
                eq(1L), eq(MEETING_ID), eq(1), eq(1785551200000L), eq(1785551205800L), any()
        )).willReturn(response);

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "segment-000001.ogg",
                "audio/ogg",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/meetings/{meetingId}/vad-recordings", MEETING_ID)
                        .file(audio)
                        .param("sequence", "1")
                        .param("startedAt", "1785551200000")
                        .param("endedAt", "1785551205800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.meetingRoomId").value(100))
                .andExpect(jsonPath("$.data.participantId").value(12))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.username").value("junho"))
                .andExpect(jsonPath("$.data.audioObjectKey")
                        .value("conferences/100/participants/12/segment-000001.ogg"));
    }

    @Test
    @DisplayName("MEET-12 sequence 충돌 시 409와 expectedSequence를 반환한다")
    void addVadRecording_returns409OnSequenceConflict() throws Exception {
        given(vadRecordingService.addVadRecording(
                eq(1L), eq(MEETING_ID), eq(1), eq(1785551200000L), eq(1785551205800L), any()
        )).willThrow(new CustomException(
                ErrorCode.VAD_SEQUENCE_CONFLICT,
                new ResponseVadSequenceConflictDto(16)
        ));

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "segment-000001.ogg",
                "audio/ogg",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/meetings/{meetingId}/vad-recordings", MEETING_ID)
                        .file(audio)
                        .param("sequence", "1")
                        .param("startedAt", "1785551200000")
                        .param("endedAt", "1785551205800"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAD_SEQUENCE_CONFLICT"))
                .andExpect(jsonPath("$.data.expectedSequence").value(16));
    }
}
