package com.ssafy.backend.member.service;

import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseMyInvitationDto;

import java.util.List;

public interface InvitationService {

    // MEMBER-02: 스페이스 소유자가 사용자를 초대한다. 초대는 invitations 테이블에 TTL 1일(expires_at)로 보관되며, 상태는 만료 여부로만 판단한다.
    ResponseInviteMemberDto inviteMember(Long inviterId, Long spaceId, RequestInviteMemberDto request);

    // MEMBER-03: 내가 받은(만료되지 않은) 초대 목록을 최신순으로 조회한다.
    List<ResponseMyInvitationDto> findMyInvitations(Long userId);

    // MEMBER-03: 받은 초대를 수락해 스페이스 멤버(MEMBER 권한)로 등록하고, 처리된 초대는 삭제한다.
    void acceptInvitation(Long userId, String invitationId);

    // MEMBER-03: 받은 초대를 거절하고 초대 레코드를 삭제한다.
    void rejectInvitation(Long userId, String invitationId);
}