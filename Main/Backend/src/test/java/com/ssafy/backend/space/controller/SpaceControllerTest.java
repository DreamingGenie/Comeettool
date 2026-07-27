package com.ssafy.backend.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.dto.ResponseSpaceMemberDto;
import com.ssafy.backend.space.service.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SpaceController 슬라이스 테스트 (standalone MockMvc).
 * SpaceService는 Mock, @AuthenticationPrincipal은 SecurityContext + ArgumentResolver로 주입한다.
 * 전체 시큐리티 체인·DB 없이 요청 매핑·검증·응답 포맷·예외 변환만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpaceController 슬라이스 테스트")
class SpaceControllerTest {

    private static final String USER_ID = "7";

    @Mock
    private SpaceService spaceService;

    @InjectMocks
    private SpaceController spaceController;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(spaceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        // principal = userId(String) — JwtAuthenticationFilter가 설정하는 형태와 동일.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @Nested
    @DisplayName("SPACE-01 POST /api/v1/spaces")
    class CreateSpace {

        @Test
        @DisplayName("유효한_요청이면_201과_생성된_스페이스를_반환한다")
        void 유효한_요청이면_201과_생성된_스페이스를_반환한다() throws Exception {
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", "설명", "#123456", null);
            ResponseCreateSpaceDto response =
                    new ResponseCreateSpaceDto(10L, "팀A", "설명", "#123456", null, 7L, null);
            given(spaceService.createSpace(eq(7L), any(RequestCreateSpaceDto.class))).willReturn(response);

            mockMvc.perform(post("/api/v1/spaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.spaceId").value(10))
                    .andExpect(jsonPath("$.data.ownerId").value(7));

            verify(spaceService).createSpace(eq(7L), any(RequestCreateSpaceDto.class));
        }

        @Test
        @DisplayName("이름이_비어있으면_400_VALIDATION_FAILED를_반환한다")
        void 이름이_비어있으면_400을_반환한다() throws Exception {
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("  ", null, null, null);

            mockMvc.perform(post("/api/v1/spaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("SPACE-02 GET /api/v1/spaces")
    class FindMySpaces {

        @Test
        @DisplayName("참여_스페이스_목록을_200으로_반환한다")
        void 참여_스페이스_목록을_200으로_반환한다() throws Exception {
            ResponseSpaceListDto item =
                    new ResponseSpaceListDto(10L, "팀A", "설명", "#123456", null, 7L, "OWNER", 3L);
            given(spaceService.findMySpaces(7L)).willReturn(List.of(item));

            mockMvc.perform(get("/api/v1/spaces"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data[0].spaceId").value(10))
                    .andExpect(jsonPath("$.data[0].myRole").value("OWNER"))
                    .andExpect(jsonPath("$.data[0].memberCount").value(3));
        }
    }

    @Nested
    @DisplayName("SPACE-05 GET /api/v1/spaces/{spaceId}")
    class FindSpaceDetail {

        @Test
        @DisplayName("멤버면_상세_정보와_참여자를_200으로_반환한다")
        void 멤버면_상세_정보와_참여자를_200으로_반환한다() throws Exception {
            ResponseSpaceMemberDto member =
                    new ResponseSpaceMemberDto(1L, 7L, "진", "OWNER", "OWNER");
            ResponseSpaceDetailDto detail =
                    new ResponseSpaceDetailDto(10L, "팀A", "설명", "#123456", null, 7L, null, List.of(member));
            given(spaceService.findSpaceDetail(7L, 10L)).willReturn(detail);

            mockMvc.perform(get("/api/v1/spaces/{spaceId}", 10L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.spaceId").value(10))
                    .andExpect(jsonPath("$.data.members[0].role").value("OWNER"));
        }

        @Test
        @DisplayName("멤버가_아니면_403_SPACE_ACCESS_DENIED를_반환한다")
        void 멤버가_아니면_403을_반환한다() throws Exception {
            given(spaceService.findSpaceDetail(7L, 10L))
                    .willThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED));

            mockMvc.perform(get("/api/v1/spaces/{spaceId}", 10L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("존재하지_않는_스페이스면_404_SPACE_NOT_FOUND를_반환한다")
        void 존재하지_않는_스페이스면_404를_반환한다() throws Exception {
            given(spaceService.findSpaceDetail(7L, 99L))
                    .willThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND));

            mockMvc.perform(get("/api/v1/spaces/{spaceId}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }
    }
}
