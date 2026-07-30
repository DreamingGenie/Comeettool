package com.ssafy.backend.meeting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.meeting.dto.RequestCreateMeetingDto;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingHostDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.service.MeetingService;

/**
 * MEET-06 호스트 양도 및 MEET-07 참여자 조회 컨트롤러 테스트.
 * 실제 DB·시큐리티 필터 없이 API 매핑, 인증 userId 전달, 입력 검증과 공통 응답 형식을 검증한다.
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
}
