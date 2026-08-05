package com.ssafy.backend.document.dto;

import jakarta.validation.constraints.NotNull;

public record RequestCreateDocumentDto(
        @NotNull
        Long teamId
) {
}
