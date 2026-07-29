package com.ssafy.backend.document.client;

import com.ssafy.backend.document.dto.ResponseDocumentDetailDto;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DocumentClient {
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;

    public DocumentClient(
            RestClient.Builder restClientBuilder,
            @Value("${yjs.internal-base-url}") String baseUrl,
            @Value("${yjs.internal-token}") String internalToken
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
                .build();
    }

    public ResponseDocumentDetailDto addDocument(Long teamId) {
        YjsDocumentResponse response = restClient.post()
                .uri("/internal/documents")
                .body(new YjsCreateDocumentRequest(teamId))
                .retrieve()
                .body(YjsDocumentResponse.class);

        if (response == null) {
            throw new IllegalStateException("Yjs 문서 생성 응답이 없습니다.");
        }

        return new ResponseDocumentDetailDto(
                response.id(),
                response.teamId(),
                response.title(),
                response.finalVersion(),
                response.stateEpoch(),
                response.createdAt(),
                response.updatedAt()
        );
    }

    private record YjsCreateDocumentRequest(Long teamId) {
    }

    private record YjsDocumentResponse(
            UUID id,
            Long teamId,
            String title,
            int finalVersion,
            int stateEpoch,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
