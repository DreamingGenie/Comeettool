package com.ssafy.backend.meeting.service;

import org.springframework.web.multipart.MultipartFile;

import com.ssafy.backend.meeting.dto.ResponseVadRecordingDto;

public interface VadRecordingService {

    ResponseVadRecordingDto addVadRecording(
            Long requesterUserId,
            Long meetingId,
            Integer sequence,
            Long startedAt,
            Long endedAt,
            MultipartFile audio
    );
}
