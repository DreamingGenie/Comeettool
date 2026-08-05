package com.ssafy.backend.report.export;

/**
 * AI_BE 산출물(전사/회의록/퍼실리테이터 리포트) 내보내기 파일의 S3 오브젝트 키 규칙.
 * 합의된 저장 구조:
 * <pre>
 * ai-results/{meetingId}/transcript.md
 * ai-results/{meetingId}/transcript.pdf
 * ai-results/{meetingId}/minutes.md
 * ai-results/{meetingId}/minutes.pdf
 * ai-results/{meetingId}/facilitator-report.md
 * ai-results/{meetingId}/facilitator-report.pdf
 * </pre>
 * 리포트 타입마다 baseName만 다르고 구조는 동일하므로, REPORTS-03(전사)뿐 아니라
 * REPORTS-06/07(회의록)·facilitator-report 내보내기에서도 이 유틸을 그대로 재사용한다.
 * 파일명이 고정돼 있어 같은 회의를 다시 내보내면 기존 오브젝트를 덮어쓴다(캐시 URL도 그대로 유지됨).
 */
public final class AiResultKeys {

    private static final String ROOT = "ai-results";

    private AiResultKeys() {
    }

    public static String of(Long meetingId, String baseName, String format) {
        return ROOT + "/" + meetingId + "/" + baseName + "." + format;
    }
}
