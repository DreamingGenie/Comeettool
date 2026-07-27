package com.ssafy.backend.space.entity;

/**
 * 스페이스 멤버의 역할. members.role / members.authority 에 문자열(name())로 저장된다.
 * OWNER = 스페이스 생성자, MEMBER = 초대로 합류한 일반 참여자(MEMBER-02 범위).
 */
public enum MemberRole {
    OWNER,
    MEMBER
}
