package com.ssafy.backend.report.export;

import java.util.List;

/**
 * {@link MarkdownRenderer}가 렌더링하는 md 문서의 한 섹션.
 * heading이 null/blank면 "## 제목" 줄 없이 items만 렌더링한다(단일 섹션 문서에 사용).
 */
public record MarkdownSection(
        String heading,
        List<String> items
) {
}
