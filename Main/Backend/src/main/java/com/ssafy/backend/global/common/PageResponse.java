package com.ssafy.backend.global.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 공통 페이지네이션 응답 DTO. 도메인 전반에서 재사용한다(README §0 규약).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
