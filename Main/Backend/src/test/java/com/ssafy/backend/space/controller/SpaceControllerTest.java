package com.ssafy.backend.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.RequestTransferOwnerDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.dto.ResponseSpaceMemberDto;
import com.ssafy.backend.space.dto.ResponseTransferOwnerDto;
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
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    class AddSpace {

        @Test
        @DisplayName("유효한 요청이면 201과 생성된 스페이스를 반환한다")
        void addSpace_returns201WithCreatedSpace() throws Exception {
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", "설명", "#123456", null);
            ResponseCreateSpaceDto response =
                    new ResponseCreateSpaceDto(10L, "팀A", "설명", "#123456", null, 7L, null);
            given(spaceService.addSpace(eq(7L), any(RequestCreateSpaceDto.class))).willReturn(response);

            mockMvc.perform(post("/api/v1/spaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.spaceId").value(10))
                    .andExpect(jsonPath("$.data.ownerId").value(7));

            verify(spaceService).addSpace(eq(7L), any(RequestCreateSpaceDto.class));
        }

        @Test
        @DisplayName("이름이 비어있으면 400 VALIDATION_FAILED를 반환한다")
        void addSpace_returns400WhenNameBlank() throws Exception {
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
    class FindSpaceList {

        @Test
        @DisplayName("참여 스페이스 목록을 200으로 반환한다")
        void findSpaceList_returns200WithSpaces() throws Exception {
            ResponseSpaceListDto item =
                    new ResponseSpaceListDto(10L, "팀A", "설명", "#123456", null, 7L, "OWNER", 3L);
            given(spaceService.findSpaceList(7L, null)).willReturn(List.of(item));

            mockMvc.perform(get("/api/v1/spaces"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data[0].spaceId").value(10))
                    .andExpect(jsonPath("$.data[0].myAuthority").value("OWNER"))
                    .andExpect(jsonPath("$.data[0].memberCount").value(3));
        }

        @Test
        @DisplayName("참여 스페이스가 없으면 200과 빈 배열(null 아님)을 반환한다")
        void findSpaceList_returns200WithEmptyArray() throws Exception {
            given(spaceService.findSpaceList(7L, null)).willReturn(List.of());

            mockMvc.perform(get("/api/v1/spaces"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    // FE 계약: 빈 목록은 null이 아니라 [] 로 직렬화된다.
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("SPACE-03: search 파라미터를 서비스에 전달한다")
        void findSpaceList_passesSearchParam() throws Exception {
            given(spaceService.findSpaceList(7L, "기획")).willReturn(List.of());

            mockMvc.perform(get("/api/v1/spaces").param("search", "기획"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"));

            verify(spaceService).findSpaceList(7L, "기획");
        }
    }

    @Nested
    @DisplayName("SPACE-05 GET /api/v1/spaces/{spaceId}")
    class FindSpaceDetails {

        @Test
        @DisplayName("멤버면 상세 정보와 참여자를 200으로 반환한다")
        void findSpaceDetails_returns200WithInfoAndMembers() throws Exception {
            ResponseSpaceMemberDto member =
                    new ResponseSpaceMemberDto(1L, 7L, "진", "OWNER", null);
            ResponseSpaceDetailDto detail =
                    new ResponseSpaceDetailDto(10L, "팀A", "설명", "#123456", null, 7L, null, List.of(member));
            given(spaceService.findSpaceDetails(7L, 10L)).willReturn(detail);

            mockMvc.perform(get("/api/v1/spaces/{spaceId}", 10L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.spaceId").value(10))
                    .andExpect(jsonPath("$.data.members[0].authority").value("OWNER"));
        }

        @Test
        @DisplayName("멤버가 아니면 403 SPACE_ACCESS_DENIED를 반환한다")
        void findSpaceDetails_returns403ForNonMember() throws Exception {
            given(spaceService.findSpaceDetails(7L, 10L))
                    .willThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED));

            mockMvc.perform(get("/api/v1/spaces/{spaceId}", 10L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void findSpaceDetails_returns404WhenAbsent() throws Exception {
            given(spaceService.findSpaceDetails(7L, 99L))
                    .willThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND));

            mockMvc.perform(get("/api/v1/spaces/{spaceId}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("SPACE-07 DELETE /api/v1/spaces/{spaceId}/members/me")
    class RemoveMyMembership {

        @Test
        @DisplayName("나가기에 성공하면 200 SUCCESS를 반환한다")
        void removeMyMembership_returns200() throws Exception {
            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/me", 10L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"));

            verify(spaceService).removeMyMembership(7L, 10L);
        }

        @Test
        @DisplayName("혼자인 소유자가 나가기를 시도하면 409 SPACE_OWNER_LAST_MEMBER를 반환한다")
        void removeMyMembership_returns409ForSoloOwner() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_OWNER_LAST_MEMBER))
                    .when(spaceService).removeMyMembership(7L, 10L);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/me", 10L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_LAST_MEMBER"));
        }

        @Test
        @DisplayName("다른 멤버가 있는 소유자가 나가기를 시도하면 409 SPACE_OWNER_MUST_TRANSFER를 반환한다")
        void removeMyMembership_returns409ForOwnerWithMembers() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_OWNER_MUST_TRANSFER))
                    .when(spaceService).removeMyMembership(7L, 10L);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/me", 10L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_MUST_TRANSFER"));
        }

        @Test
        @DisplayName("멤버가 아니면 403 SPACE_ACCESS_DENIED를 반환한다")
        void removeMyMembership_returns403ForNonMember() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED))
                    .when(spaceService).removeMyMembership(7L, 10L);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/me", 10L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void removeMyMembership_returns404WhenAbsent() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND))
                    .when(spaceService).removeMyMembership(7L, 99L);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/me", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("SPACE-11 DELETE /api/v1/spaces/{spaceId}")
    class RemoveSpace {

        @Test
        @DisplayName("삭제에 성공하면 200 SUCCESS를 반환한다")
        void removeSpace_returns200() throws Exception {
            mockMvc.perform(delete("/api/v1/spaces/{spaceId}", 10L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"));

            verify(spaceService).removeSpace(7L, 10L);
        }

        @Test
        @DisplayName("소유자가 아니면 403 SPACE_OWNER_ONLY를 반환한다")
        void removeSpace_returns403ForNonOwner() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_OWNER_ONLY))
                    .when(spaceService).removeSpace(7L, 10L);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}", 10L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_ONLY"));
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void removeSpace_returns404WhenAbsent() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND))
                    .when(spaceService).removeSpace(7L, 99L);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("SPACE-101 PATCH /api/v1/spaces/{spaceId}/owner")
    class TransferOwner {

        @Test
        @DisplayName("위임에 성공하면 200과 변경 전·후 소유자를 반환한다")
        void transferOwner_returns200() throws Exception {
            RequestTransferOwnerDto request = new RequestTransferOwnerDto(2L);
            given(spaceService.transferOwner(eq(7L), eq(10L), any(RequestTransferOwnerDto.class)))
                    .willReturn(new ResponseTransferOwnerDto(10L, 7L, 2L));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/owner", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.previousOwnerId").value(7))
                    .andExpect(jsonPath("$.data.newOwnerId").value(2));
        }

        @Test
        @DisplayName("newOwnerUserId가 없으면 400 VALIDATION_FAILED를 반환한다")
        void transferOwner_returns400WhenTargetMissing() throws Exception {
            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/owner", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("소유자가 아니면 403 SPACE_OWNER_ONLY를 반환한다")
        void transferOwner_returns403ForNonOwner() throws Exception {
            given(spaceService.transferOwner(eq(7L), eq(10L), any(RequestTransferOwnerDto.class)))
                    .willThrow(new CustomException(ErrorCode.SPACE_OWNER_ONLY));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/owner", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestTransferOwnerDto(2L))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_ONLY"));
        }

        @Test
        @DisplayName("대상이 GUEST면 400 SPACE_TRANSFER_TARGET_NOT_ELIGIBLE를 반환한다")
        void transferOwner_returns400ForGuestTarget() throws Exception {
            given(spaceService.transferOwner(eq(7L), eq(10L), any(RequestTransferOwnerDto.class)))
                    .willThrow(new CustomException(ErrorCode.SPACE_TRANSFER_TARGET_NOT_ELIGIBLE));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/owner", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestTransferOwnerDto(2L))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SPACE_TRANSFER_TARGET_NOT_ELIGIBLE"));
        }
    }
}
