package com.ssafy.backend.schedule.controller;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.schedule.dto.RequestCreateScheduleDto;
import com.ssafy.backend.schedule.dto.RequestUpdateScheduleDto;
import com.ssafy.backend.schedule.dto.ResponseScheduleDto;
import com.ssafy.backend.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCHEDULE 도메인 REST 컨트롤러 (inventory.md §4).
 * 인증 필요(SecurityConfig anyRequest().authenticated()) — principal = userId(String).
 * 경로 루트가 /me·/spaces·/schedules 3종이라 단일 컨트롤러에 전체 경로로 배치한다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // SCHEDULE-01: 내 일정 일괄 확인(내가 참여자로 포함된 모든 일정)
    @GetMapping("/me/schedules")
    public ResponseEntity<ApiResponse<List<ResponseScheduleDto>>> findMySchedules(
            @AuthenticationPrincipal String userId) {
        List<ResponseScheduleDto> response = scheduleService.findMySchedules(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // SCHEDULE-02: 팀 일정 목록 조회(스페이스 멤버 전용)
    @GetMapping("/spaces/{spaceId}/schedules")
    public ResponseEntity<ApiResponse<List<ResponseScheduleDto>>> findScheduleList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId) {
        List<ResponseScheduleDto> response =
                scheduleService.findScheduleList(Long.parseLong(userId), spaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // SCHEDULE-03: 일정 생성(스페이스 멤버 전용)
    @PostMapping("/spaces/{spaceId}/schedules")
    public ResponseEntity<ApiResponse<ResponseScheduleDto>> addSchedule(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @Valid @RequestBody RequestCreateScheduleDto request) {
        ResponseScheduleDto response =
                scheduleService.addSchedule(Long.parseLong(userId), spaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("일정이 생성되었습니다.", response));
    }

    // SCHEDULE-04: 일정 수정(생성자 또는 스페이스 Owner)
    @PatchMapping("/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ResponseScheduleDto>> modifySchedule(
            @AuthenticationPrincipal String userId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody RequestUpdateScheduleDto request) {
        ResponseScheduleDto response =
                scheduleService.modifySchedule(Long.parseLong(userId), scheduleId, request);
        return ResponseEntity.ok(ApiResponse.success("일정이 수정되었습니다.", response));
    }

    // SCHEDULE-05: 일정 삭제(생성자 또는 스페이스 Owner, soft delete)
    @DeleteMapping("/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<Void>> removeSchedule(
            @AuthenticationPrincipal String userId,
            @PathVariable Long scheduleId) {
        scheduleService.removeSchedule(Long.parseLong(userId), scheduleId);
        return ResponseEntity.ok(ApiResponse.success("일정이 삭제되었습니다.", null));
    }
}
