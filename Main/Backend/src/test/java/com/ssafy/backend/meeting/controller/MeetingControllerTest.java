package com.ssafy.backend.meeting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.service.MeetingService;

/**
 * MEET-06 호스트 양도 컨트롤러 테스트.
 * 실제 DB·시큐리티 필터 없이 API 매핑, 인증 userId 전달, 입력 검증과 공통 응답 형식을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MEET-06 호스트 양도 API 테스트")
class MeetingControllerTest {

    private static final String HOST_USER_ID = "1";
    private static final Long MEETING_ID = 100L;

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
}
