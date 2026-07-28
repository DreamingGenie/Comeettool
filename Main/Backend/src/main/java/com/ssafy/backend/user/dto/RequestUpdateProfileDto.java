package com.ssafy.backend.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH 부분 수정 — 필드를 요청에 안 보내면(null) 미변경. 전부 선택 항목.
public record RequestUpdateProfileDto(

        @Size(max = 20)
        String nickname,

        String phone,

        @Pattern(regexp = "^[MF]$", message = "sex는 M 또는 F만 허용됩니다.")
        String sex,

        @ValidAgeGroup
        Integer age,

        String jobFamily,

        String jobRole,

        @Size(max = 255)
        String userDescription,

        // hex 색상 코드 제약은 명세에 없던 제안 사항 — 필요 없으면 제거 가능.
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "userColor는 #RRGGBB 형식의 hex 코드여야 합니다.")
        String userColor
) {
}