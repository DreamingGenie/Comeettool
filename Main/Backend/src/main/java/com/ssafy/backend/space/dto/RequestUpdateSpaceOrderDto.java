package com.ssafy.backend.space.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * SPACE-04 스페이스 정렬 저장 요청.
 * spaceOrder = 사용자가 원하는 순서대로 나열한 spaceId 목록(드래그앤드롭 결과).
 * 빈 목록이면 커스텀 순서 해제(최신순으로 복귀).
 */
public record RequestUpdateSpaceOrderDto(

        @NotNull
        List<Long> spaceOrder
) {
}
