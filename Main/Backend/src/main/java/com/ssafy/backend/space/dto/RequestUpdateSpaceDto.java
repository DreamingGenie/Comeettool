package com.ssafy.backend.space.dto;

import jakarta.validation.constraints.Size;

/**
 * SPACE-08 스페이스 정보 수정 요청(부분 수정).
 * 모든 필드는 선택 — 전달된(null이 아닌) 필드만 갱신한다.
 * 이름·색상은 NOT NULL 컬럼이라 "전달됐다면" 공백일 수 없다(서비스에서 검증).
 * 설명·프로필 이미지는 빈 문자열을 넘겨 비울 수 있다.
 */
public record RequestUpdateSpaceDto(

        @Size(max = 100)
        String teamName,

        @Size(max = 2000)
        String teamDescription,

        @Size(max = 20)
        String teamColor,

        @Size(max = 500)
        String teamProfileImage
) {
}
