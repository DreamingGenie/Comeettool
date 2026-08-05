package com.ssafy.backend.report.export;

import java.util.List;

/**
 * "제목 + 섹션별 리스트" 형태를 받아 범용 md 문자열로 변환하는 유틸.
 * 특정 리포트 타입(STT, 요약 등)에 종속되지 않는다 — REPORTS-03(전사)뿐 아니라
 * REPORTS-06/REPORTS-10 등 이후 리포트 내보내기에서도 이 렌더러를 그대로 재사용한다.
 * 각 리포트 타입은 원본 데이터를 {@link MarkdownSection} 목록으로 변환하는 부분만 별도로 구현하면 된다
 * (예: {@link TranscriptMarkdownRenderer}).
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    public static String render(String title, List<MarkdownSection> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");

        for (MarkdownSection section : sections) {
            if (section.heading() != null && !section.heading().isBlank()) {
                sb.append("## ").append(section.heading()).append("\n\n");
            }
            for (String item : section.items()) {
                sb.append("- ").append(item).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
