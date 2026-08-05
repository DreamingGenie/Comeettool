package com.ssafy.backend.member.entity;

/**
 * 스페이스 멤버의 권한(고정 3종). members.authority 에 문자열(name())로 저장된다.
 * OWNER = 스페이스 생성자, MEMBER = 일반 참여자, GUEST = 게스트(제한적 참여).
 * (역할(team_roles)은 권한과 독립된 축으로, members.team_role_id 로 참조한다.)
 */
public enum MemberAuthority {
    OWNER,
    MEMBER,
    GUEST
}