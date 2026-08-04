package com.ssafy.backend.schedule.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.schedule.dto.RequestCreateScheduleDto;
import com.ssafy.backend.schedule.dto.RequestUpdateScheduleDto;
import com.ssafy.backend.schedule.dto.ResponseScheduleDto;
import com.ssafy.backend.schedule.service.ScheduleService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ScheduleController 슬라이스 테스트 (standalone MockMvc).
 * ScheduleService는 Mock, @AuthenticationPrincipal은 SecurityContext + ArgumentResolver로 주입한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleController 슬라이스 테스트")
class ScheduleControllerTest {

    private static final String USER_ID = "7";

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private ScheduleController scheduleController;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(scheduleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    private ResponseScheduleDto sample(Long id, String title) {
        return new ResponseScheduleDto(id, 10L, 7L, "회의", title, "설명", null, null, List.of(7L));
    }

    @Test
    @DisplayName("SCHEDULE-01 GET /me/schedules → 200")
    void findMySchedules() throws Exception {
        given(scheduleService.findMySchedules(7L)).willReturn(List.of(sample(1L, "내 일정")));

        mockMvc.perform(get("/api/v1/me/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].scheduleId").value(1));
    }

    @Test
    @DisplayName("SCHEDULE-02 GET /spaces/{id}/schedules → 200")
    void findScheduleList() throws Exception {
        given(scheduleService.findScheduleList(7L, 10L)).willReturn(List.of(sample(1L, "팀 일정")));

        mockMvc.perform(get("/api/v1/spaces/10/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teamId").value(10));
    }

    @Test
    @DisplayName("SCHEDULE-03 POST /spaces/{id}/schedules → 201")
    void addSchedule() throws Exception {
        RequestCreateScheduleDto request =
                new RequestCreateScheduleDto("회의", "주간", "설명", null, null, null);
        given(scheduleService.addSchedule(eq(7L), eq(10L), any(RequestCreateScheduleDto.class)))
                .willReturn(sample(1L, "주간"));

        mockMvc.perform(post("/api/v1/spaces/10/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduleId").value(1));
    }

    @Test
    @DisplayName("SCHEDULE-04 PATCH /schedules/{id} → 200")
    void modifySchedule() throws Exception {
        RequestUpdateScheduleDto request =
                new RequestUpdateScheduleDto(null, "수정", null, null, null, null);
        given(scheduleService.modifySchedule(eq(7L), eq(1L), any(RequestUpdateScheduleDto.class)))
                .willReturn(sample(1L, "수정"));

        mockMvc.perform(patch("/api/v1/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정"));
    }

    @Test
    @DisplayName("SCHEDULE-05 DELETE /schedules/{id} → 200")
    void removeSchedule() throws Exception {
        mockMvc.perform(delete("/api/v1/schedules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(scheduleService).removeSchedule(7L, 1L);
    }

    @Test
    @DisplayName("없는 일정 수정 시 404 SCHEDULE_NOT_FOUND")
    void modifyNotFound() throws Exception {
        RequestUpdateScheduleDto request =
                new RequestUpdateScheduleDto(null, "수정", null, null, null, null);
        given(scheduleService.modifySchedule(eq(7L), eq(1L), any(RequestUpdateScheduleDto.class)))
                .willThrow(new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));

        mockMvc.perform(patch("/api/v1/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));
    }

    @Test
    @DisplayName("권한 없는 삭제 시 403 SCHEDULE_ACCESS_DENIED")
    void removeForbidden() throws Exception {
        doThrow(new CustomException(ErrorCode.SCHEDULE_ACCESS_DENIED))
                .when(scheduleService).removeSchedule(7L, 1L);

        mockMvc.perform(delete("/api/v1/schedules/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ACCESS_DENIED"));
    }
}
