package com.ssafy.backend.member.dto;

/**
 * MEMBER-16 팀 역할 부분 수정 요청. 각 필드가 null(또는 미포함)이면 해당 값은 변경하지 않는다.
 * roleName이 오면 1~20자·공백만으로 구성 불가 검증을 서비스 계층에서 수행한다
 * (null 허용 필드라 Bean Validation 대신 changeMemberAuthority의 수동 검증 패턴을 따른다).
 */
public record RequestUpdateTeamRoleDto(
        String roleName,
        String color
) {
}