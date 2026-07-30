package com.ssafy.backend.space.service;

import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;

import java.util.List;

public interface SpaceService {

    // SPACE-01: 스페이스 생성 + 생성자를 Owner 멤버로 등록.
    ResponseCreateSpaceDto addSpace(Long userId, RequestCreateSpaceDto request);

    // SPACE-02: 로그인 사용자가 참여 중인 스페이스 목록(삭제되지 않은 것만).
    List<ResponseSpaceListDto> findSpaceList(Long userId);

    // SPACE-05: 스페이스 상세(정보 + 참여자). 요청자가 멤버가 아니면 접근 거부.
    ResponseSpaceDetailDto findSpaceDetails(Long userId, Long spaceId);

    // SPACE-07: 요청자가 스페이스에서 나간다(members 행 hard delete). Owner는 소유권 위임 후에만 가능(정책 SP-1).
    void removeMyMembership(Long userId, Long spaceId);

    // SPACE-11: Owner가 스페이스를 삭제한다(teams + 하위 documents·meeting_rooms 전파 soft delete, 정책 SP-2).
    void removeSpace(Long userId, Long spaceId);
}
