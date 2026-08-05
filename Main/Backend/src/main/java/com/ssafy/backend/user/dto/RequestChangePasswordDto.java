package com.ssafy.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// AUTH-07: 비밀번호 변경 요청. currentPassword는 기존 BCrypt 검증용, newPassword는 AUTH-01과 동일 정책.
public record RequestChangePasswordDto(

        @NotBlank
        String currentPassword,

        // AUTH-01(RequestSignupDto)과 동일한 비밀번호 정책
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=-])[A-Za-z\\d!@#$%^&*()_+=-]{8,20}$",
                message = "8~20자, 영문+숫자+특수문자를 모두 포함해야 합니다."
        )
        String newPassword
) {
}