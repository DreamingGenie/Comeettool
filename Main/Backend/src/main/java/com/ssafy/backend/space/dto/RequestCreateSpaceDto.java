package com.ssafy.backend.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SPACE-01 스페이스 생성 요청.
 * 이름은 필수, 설명·설정(색상·프로필 이미지)은 선택. 초대 발송은 MEMBER-02 범위로 분리.
 */
public record RequestCreateSpaceDto(

        @NotBlank
        @Size(max = 100)
        String teamName,

        @Size(max = 2000)
        String teamDescription,

        // 설정: 스페이스 대표 색상(#RRGGBB). 미지정 시 기본색(#000000) 적용.
        @Size(max = 20)
        String teamColor,

        @Size(max = 500)
        String teamProfileImage
) {
}
