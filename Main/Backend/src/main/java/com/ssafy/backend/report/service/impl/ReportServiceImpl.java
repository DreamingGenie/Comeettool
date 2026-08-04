package com.ssafy.backend.report.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;
import com.ssafy.backend.report.repository.AudioTranscriptionRepository;
import com.ssafy.backend.report.service.ReportService;
import com.ssafy.backend.space.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final AudioTranscriptionRepository audioTranscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TranscriptSummaryDto> getTranscripts(Long requesterId, Long spaceId, Pageable pageable) {
        teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        // 조회는 OWNER 제한 없이 스페이스 멤버 전체(OWNER/MEMBER/GUEST)에게 허용 — MEMBER-15와 동일한 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(spaceId, requesterId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        Page<TranscriptSummaryDto> page = audioTranscriptionRepository.findAllByTeamId(spaceId, pageable);
        return PageResponse.from(page);
    }
}
