package com.ssafy.backend.member.service;

import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;

public interface InvitationService {

    // MEMBER-02: 스페이스 소유자가 사용자를 초대한다. 초대는 invitations 테이블에 TTL 1일(expires_at)로 보관되며, 상태는 만료 여부로만 판단한다.
    ResponseInviteMemberDto inviteMember(Long inviterId, Long spaceId, RequestInviteMemberDto request);
}