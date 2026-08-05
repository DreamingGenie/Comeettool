package com.ssafy.backend.report.dto;

/**
 * REPORTS-03 리포트 내보내기 공통 요청. format 값의 허용 범위(md/pdf 등)는 리포트 타입별로
 * 서비스 계층에서 검증한다 — REPORTS-06/07/10/11에서도 동일 구조로 재사용될 예정이라
 * 특정 리포트 타입에 종속된 제약(Bean Validation 등)을 DTO에 두지 않는다.
 */
public record RequestExportDto(String format) {
}
