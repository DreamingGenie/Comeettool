package com.ssafy.backend.meeting.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.meeting.dto.RequestCreateMeetingDto;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseJoinMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseLeaveMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInviteCandidateDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInvitationDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingListDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.service.MeetingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회의 기능 API.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    /**
     * MEET-01: 팀 스페이스에 회의를 생성하고 생성자를 최초 참여자로 등록한다.
     */
    @PostMapping("/spaces/{spaceId}/meetings")
    public ResponseEntity<ApiResponse<ResponseCreateMeetingDto>> addMeeting(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @Valid @RequestBody RequestCreateMeetingDto request
    ) {
        ResponseCreateMeetingDto response = meetingService.addMeeting(
                Long.parseLong(userId),
                spaceId,
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회의 생성 성공", response));
    }

    /**
     * MEET-03: 초대된 참여자의 LiveKit 회의 입장 정보를 발급한다.
     */
    @PostMapping("/meetings/{meetingId}/join")
    public ResponseEntity<ApiResponse<ResponseJoinMeetingDto>> joinMeeting(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId
    ) {
        ResponseJoinMeetingDto response =
                meetingService.joinMeeting(Long.parseLong(userId), meetingId);
        return ResponseEntity.ok(ApiResponse.success("회의 입장 성공", response));
    }

    /**
     * MEET-02: 로그인 사용자가 참가 권한을 가진 진행 중인 회의 목록을 조회한다.
     */
    @GetMapping("/spaces/{spaceId}/meetings")
    public ResponseEntity<ApiResponse<List<ResponseMeetingListDto>>> getParticipatingMeetings(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId
    ) {
        List<ResponseMeetingListDto> response = meetingService.getParticipatingMeetings(
                Long.parseLong(userId),
                spaceId
        );

        return ResponseEntity.ok(
                ApiResponse.success("참가 중인 회의 목록을 조회했습니다.", response)
        );
    }

    /**
     * MEET-04: 현재 사용자를 회의에서 퇴장시킨다.
     */
    @PostMapping("/meetings/{meetingId}/leave")
    public ResponseEntity<ApiResponse<ResponseLeaveMeetingDto>> leaveMeeting(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId
    ) {
        ResponseLeaveMeetingDto response =
                meetingService.leaveMeeting(Long.parseLong(userId), meetingId);

        return ResponseEntity.ok(
                ApiResponse.success("퇴장되었습니다.", response)
        );
    }

    /**
     * MEET-05: 호스트가 모든 참여자의 회의를 종료한다.
     */
    @PostMapping("/meetings/{meetingId}/end")
    public ResponseEntity<ApiResponse<Void>> endMeeting(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId
    ) {
        meetingService.endMeeting(
                Long.parseLong(userId),
                meetingId
        );

        return ResponseEntity.ok(
                ApiResponse.success("회의가 종료되었습니다.", null)
        );
    }

    /**
     * MEET-06: 현재 호스트가 회의 참여자에게 호스트 권한을 양도한다.
     */
    @PostMapping("/meetings/{meetingId}/grant")
    public ResponseEntity<ApiResponse<ResponseTransferHostDto>> transferHost(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId,
            @Valid @RequestBody RequestTransferHostDto request
    ) {
        ResponseTransferHostDto response =
                meetingService.transferHost(Long.parseLong(userId), meetingId, request);
        return ResponseEntity.ok(ApiResponse.success("호스트 권한이 양도되었습니다.", response));
    }

    /**
     * MEET-07: 현재 회의에 입장 중인 참여자의 기본 정보를 조회한다.
     * 카메라·마이크 상태는 추후 LiveKit에서 실시간으로 결합한다.
     */
    @GetMapping("/meetings/{meetingId}/participants")
    public ResponseEntity<ApiResponse<List<ResponseMeetingParticipantDto>>> getParticipants(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId
    ) {
        List<ResponseMeetingParticipantDto> response =
                meetingService.getParticipants(Long.parseLong(userId), meetingId);
        return ResponseEntity.ok(ApiResponse.success("회의 참여자 목록을 조회했습니다.", response));
    }

    /**
     * MEET-08: 호스트가 지정한 참여자를 회의에서 강퇴한다.
     */
    @DeleteMapping("/meetings/{meetingId}/participants/{participantId}")
    public ResponseEntity<ApiResponse<ResponseLeaveMeetingDto>> kickParticipant(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId,
            @PathVariable Long participantId
    ) {
        ResponseLeaveMeetingDto response = meetingService.kickParticipant(
                Long.parseLong(userId),
                meetingId,
                participantId
        );

        return ResponseEntity.ok(
                ApiResponse.success("참여자를 강퇴했습니다.", response)
        );
    }

    /**
     * MEET-09: 호스트가 회의에 초대할 수 있는 팀 멤버를 조회한다.
     */
    @GetMapping("/meetings/{meetingId}/invite-candidates")
    public ResponseEntity<ApiResponse<List<ResponseMeetingInviteCandidateDto>>> getInviteCandidates(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId,
            @RequestParam(defaultValue = "") String keyword
    ) {
        List<ResponseMeetingInviteCandidateDto> response =
                meetingService.getInviteCandidates(
                        Long.parseLong(userId),
                        meetingId,
                        keyword
                );

        String message;
        if (response.isEmpty()) {
            message = keyword.isBlank()
                    ? "초대 가능한 멤버가 없습니다."
                    : "유저를 찾지 못했습니다.";
        } else {
            message = "초대 가능한 멤버를 조회했습니다.";
        }

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    /**
     * MEET-10: 호스트가 회의와 같은 팀의 사용자를 초대한다.
     */
    @PostMapping("/meetings/{meetingId}/invitations/users/{inviteeUserId}")
    public ResponseEntity<ApiResponse<ResponseMeetingInvitationDto>> inviteMember(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId,
            @PathVariable Long inviteeUserId
    ) {
        ResponseMeetingInvitationDto response = meetingService.inviteMember(
                Long.parseLong(userId),
                meetingId,
                inviteeUserId
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회의 멤버 초대 성공", response));
    }
}
