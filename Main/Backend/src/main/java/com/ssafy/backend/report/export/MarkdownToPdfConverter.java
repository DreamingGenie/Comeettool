package com.ssafy.backend.report.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link MarkdownRenderer} 출력(md 문자열)을 PDF로 변환한다.
 * MarkdownRenderer가 생성하는 문법("# ", "## ", "- "와 빈 줄 구분)만 지원하는 최소 변환기이며
 * 범용 마크다운 파서가 아니다 — 인라인 강조(**bold** 등)는 다루지 않는다.
 * PDFBox 기본(AFM) 폰트는 한글을 지원하지 않으므로, 번들된 Noto Sans KR을 등록해 사용한다.
 */
@Slf4j
@Component
public class MarkdownToPdfConverter {

    private static final String FONT_FAMILY = "Noto Sans KR";
    private static final String FONT_RESOURCE = "fonts/NotoSansKR-Variable.ttf";

    public byte[] convert(String markdown) {
        String html = toHtml(markdown);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(this::openFontStream, FONT_FAMILY);
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            log.error("PDF 변환 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        return out.toByteArray();
    }

    private InputStream openFontStream() {
        try {
            return new ClassPathResource(FONT_RESOURCE).getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 변환용 폰트를 로드할 수 없습니다: " + FONT_RESOURCE, e);
        }
    }

    private String toHtml(String markdown) {
        StringBuilder body = new StringBuilder();
        boolean inList = false;

        for (String line : markdown.split("\n", -1)) {
            if (line.startsWith("## ")) {
                inList = closeList(body, inList);
                body.append("<h2>").append(escape(line.substring(3))).append("</h2>\n");
            } else if (line.startsWith("# ")) {
                inList = closeList(body, inList);
                body.append("<h1>").append(escape(line.substring(2))).append("</h1>\n");
            } else if (line.startsWith("- ")) {
                if (!inList) {
                    body.append("<ul>\n");
                    inList = true;
                }
                body.append("<li>").append(escape(line.substring(2))).append("</li>\n");
            } else if (line.isBlank()) {
                inList = closeList(body, inList);
            } else {
                inList = closeList(body, inList);
                body.append("<p>").append(escape(line)).append("</p>\n");
            }
        }
        closeList(body, inList);

        return "<html><head><meta charset=\"UTF-8\"/><style>body{font-family:'"
                + FONT_FAMILY + "';}</style></head><body>\n" + body + "</body></html>";
    }

    private boolean closeList(StringBuilder body, boolean inList) {
        if (inList) {
            body.append("</ul>\n");
        }
        return false;
    }

    private String escape(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
