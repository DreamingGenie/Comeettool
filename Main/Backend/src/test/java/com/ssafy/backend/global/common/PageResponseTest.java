package com.ssafy.backend.global.common;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResponse 단위 테스트")
class PageResponseTest {

    @Test
    @DisplayName("Page를 변환하면 content·페이지 메타 필드가 정확히 매핑된다")
    void from_convertsPageFieldsCorrectly() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 5), 23);

        PageResponse<String> result = PageResponse.from(page);

        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(23);
        assertThat(result.totalPages()).isEqualTo(5);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지면 hasNext가 false다")
    void from_hasNextFalseOnLastPage() {
        PageImpl<String> page = new PageImpl<>(List.of("a"), PageRequest.of(4, 5), 21);

        PageResponse<String> result = PageResponse.from(page);

        assertThat(result.totalPages()).isEqualTo(5);
        assertThat(result.hasNext()).isFalse();
    }
}
