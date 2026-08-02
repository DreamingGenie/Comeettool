package com.ssafy.backend.member.service;

public interface MemberService {

    // MEMBER-04: 스페이스 소유자가 특정 멤버를 강퇴한다.
    void kickMember(Long requesterId, Long spaceId, Long memberId);
}