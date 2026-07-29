package com.ssafy.backend.document.mapper;

import com.ssafy.backend.document.dto.ResponseDocumentDetailDto;
import com.ssafy.backend.document.dto.ResponseDocumentSummaryDto;
import com.ssafy.backend.document.entity.Document;
import org.springframework.stereotype.Component;
 
@Component
public class DocumentMapper {

    private static final int CURRENT_STATE_EPOCH = 1;

    public ResponseDocumentSummaryDto toSummary(Document document) {
        return new ResponseDocumentSummaryDto(
                document.getId(),
                document.getTeamId(),
                document.getTitle(),
                document.getFinalVersion(),
                document.getUpdatedAt()
        );
    }

    public ResponseDocumentDetailDto toDetail(Document document) {
        return new ResponseDocumentDetailDto(
                document.getId(),
                document.getTeamId(),
                document.getTitle(),
                document.getFinalVersion(),
                CURRENT_STATE_EPOCH,
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
