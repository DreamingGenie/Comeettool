package com.ssafy.backend.space.service;

import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;

import java.util.List;

public interface SpaceService {

    // SPACE-01: 스페이스 생성 + 생성자를 Owner 멤버로 등록.
    ResponseCreateSpaceDto createSpace(Long userId, RequestCreateSpaceDto request);

    // SPACE-02: 로그인 사용자가 참여 중인 스페이스 목록(삭제되지 않은 것만).
    List<ResponseSpaceListDto> findMySpaces(Long userId);

    // SPACE-05: 스페이스 상세(정보 + 참여자). 요청자가 멤버가 아니면 접근 거부.
    ResponseSpaceDetailDto findSpaceDetail(Long userId, Long spaceId);
}
