package com.ssafy.backend.member.service;

import com.ssafy.backend.member.dto.RequestChangeAuthorityDto;
import com.ssafy.backend.member.dto.ResponseChangeAuthorityDto;

public interface MemberService {

    // MEMBER-04: 스페이스 소유자가 특정 멤버를 강퇴한다.
    void kickMember(Long requesterId, Long spaceId, Long memberId);

    // MEMBER-07: 스페이스 소유자가 멤버의 권한(MEMBER/GUEST)을 변경한다.
    ResponseChangeAuthorityDto changeMemberAuthority(
            Long requesterId, Long spaceId, Long memberId, RequestChangeAuthorityDto request);
}