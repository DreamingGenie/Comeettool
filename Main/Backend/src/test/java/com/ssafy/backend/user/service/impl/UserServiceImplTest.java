package com.ssafy.backend.user.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.user.dto.RequestUpdateProfileDto;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.mapper.UserProfileMapper;
import com.ssafy.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * UserServiceImpl 단위 테스트 (AUTH-05 findMyProfile, AUTH-06 modifyMyProfile).
 * UserRepository만 Mock — UserProfileMapper는 의존성이 없는 순수 변환기라 실제 구현체를 그대로 써서
 * "필드가 정확히 매핑되는지"까지 이 테스트에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 단위 테스트")
class UserServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    private final UserProfileMapper userProfileMapper = new UserProfileMapper();

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, userProfileMapper);
    }

    private User buildUser() {
        User user = User.builder().email("user@example.com").password("$2a$10$encoded").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    // =====================================================================
    // AUTH-05: findMyProfile() 테스트
    // =====================================================================

    @Nested
    @DisplayName("내 프로필 조회 성공")
    class GetMyProfileSuccess {

        @Test
        @DisplayName("모든_필드가_정확히_매핑되어_응답에_담긴다")
        void 모든_필드가_정확히_매핑되어_응답에_담긴다() {
            // given
            User user = buildUser();
            ReflectionTestUtils.setField(user, "nickname", "김인송");
            ReflectionTestUtils.setField(user, "phone", "010-1234-5678");
            ReflectionTestUtils.setField(user, "profileImageUrl", "https://cdn.example.com/profile.jpg");
            ReflectionTestUtils.setField(user, "sex", "M");
            ReflectionTestUtils.setField(user, "age", 20);
            ReflectionTestUtils.setField(user, "jobFamily", "소프트웨어 개발");
            ReflectionTestUtils.setField(user, "jobRole", "Frontend Developer");
            ReflectionTestUtils.setField(user, "description", "안녕하세요!");
            ReflectionTestUtils.setField(user, "displayColor", "#000000");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            // when
            ResponseMyProfileDto result = userService.findMyProfile(USER_ID);

            // then
            assertThat(result.userId()).isEqualTo(1L); // Long — AUTH-01과 동일하게 통일
            assertThat(result.email()).isEqualTo("user@example.com");
            assertThat(result.nickname()).isEqualTo("김인송");
            assertThat(result.phone()).isEqualTo("010-1234-5678");
            assertThat(result.profileImage()).isEqualTo("https://cdn.example.com/profile.jpg");
            assertThat(result.sex()).isEqualTo("M");
            assertThat(result.age()).isEqualTo(20);
            assertThat(result.jobFamily()).isEqualTo("소프트웨어 개발");
            assertThat(result.jobRole()).isEqualTo("Frontend Developer");
            assertThat(result.userDescription()).isEqualTo("안녕하세요!");
            assertThat(result.userColor()).isEqualTo("#000000");
        }
    }

    @Nested
    @DisplayName("내 프로필 조회 - nullable 필드")
    class GetMyProfileNullableFields {

        @Test
        @DisplayName("온보딩_전이라_nullable_필드가_모두_null인_유저도_정상적으로_null로_반환된다")
        void 온보딩_전이라_nullable_필드가_모두_null인_유저도_정상적으로_null로_반환된다() {
            // given: 회원가입 직후(AUTH-01) 상태 — email·password 외엔 아직 아무것도 채워지지 않음.
            // userColor만 NOT NULL 컬럼이라 기본값이 있다고 가정하고 명시적으로 세팅한다.
            User user = buildUser();
            ReflectionTestUtils.setField(user, "displayColor", "#000000");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            // when
            ResponseMyProfileDto result = userService.findMyProfile(USER_ID);

            // then
            assertThat(result.nickname()).isNull();
            assertThat(result.phone()).isNull();
            assertThat(result.profileImage()).isNull();
            assertThat(result.sex()).isNull();
            assertThat(result.age()).isNull();
            assertThat(result.jobFamily()).isNull();
            assertThat(result.jobRole()).isNull();
            assertThat(result.userDescription()).isNull();
            assertThat(result.userColor()).isEqualTo("#000000");
        }
    }

    @Nested
    @DisplayName("내 프로필 조회 실패")
    class GetMyProfileFailure {

        @Test
        @DisplayName("존재하지_않는_userId로_조회하면_USER_NOT_FOUND_예외가_발생한다")
        void 존재하지_않는_userId로_조회하면_USER_NOT_FOUND_예외가_발생한다() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.findMyProfile(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // =====================================================================
    // AUTH-06: modifyMyProfile() 테스트
    // =====================================================================

    private User buildFullyPopulatedUser() {
        User user = buildUser();
        ReflectionTestUtils.setField(user, "nickname", "기존닉네임");
        ReflectionTestUtils.setField(user, "phone", "010-0000-0000");
        ReflectionTestUtils.setField(user, "sex", "F");
        ReflectionTestUtils.setField(user, "age", 30);
        ReflectionTestUtils.setField(user, "jobFamily", "기존직군");
        ReflectionTestUtils.setField(user, "jobRole", "기존직무");
        ReflectionTestUtils.setField(user, "description", "기존소개");
        ReflectionTestUtils.setField(user, "displayColor", "#000000");
        return user;
    }

    @Nested
    @DisplayName("내 프로필 수정 성공 - 전체 필드")
    class ModifyMyProfileFullUpdate {

        @Test
        @DisplayName("모든_필드를_요청하면_전부_새_값으로_반영된다")
        void 모든_필드를_요청하면_전부_새_값으로_반영된다() {
            // given
            User user = buildFullyPopulatedUser();
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            RequestUpdateProfileDto request = new RequestUpdateProfileDto(
                    "새닉네임", "010-1234-5678", "M", 20,
                    "소프트웨어 개발", "Frontend Developer", "새소개", "#3B82F6"
            );

            // when
            ResponseMyProfileDto result = userService.modifyMyProfile(USER_ID, request);

            // then
            assertThat(result.nickname()).isEqualTo("새닉네임");
            assertThat(result.phone()).isEqualTo("010-1234-5678");
            assertThat(result.sex()).isEqualTo("M");
            assertThat(result.age()).isEqualTo(20);
            assertThat(result.jobFamily()).isEqualTo("소프트웨어 개발");
            assertThat(result.jobRole()).isEqualTo("Frontend Developer");
            assertThat(result.userDescription()).isEqualTo("새소개");
            assertThat(result.userColor()).isEqualTo("#3B82F6");
        }
    }

    @Nested
    @DisplayName("내 프로필 수정 성공 - 부분 수정")
    class ModifyMyProfilePartialUpdate {

        @Test
        @DisplayName("nickname만_요청하면_나머지_필드는_기존_값을_그대로_유지한다")
        void nickname만_요청하면_나머지_필드는_기존_값을_그대로_유지한다() {
            // given
            User user = buildFullyPopulatedUser();
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            RequestUpdateProfileDto request = new RequestUpdateProfileDto(
                    "새닉네임", null, null, null, null, null, null, null
            );

            // when
            ResponseMyProfileDto result = userService.modifyMyProfile(USER_ID, request);

            // then: nickname만 바뀌고 나머지는 buildFullyPopulatedUser()의 기존 값 그대로
            assertThat(result.nickname()).isEqualTo("새닉네임");
            assertThat(result.phone()).isEqualTo("010-0000-0000");
            assertThat(result.sex()).isEqualTo("F");
            assertThat(result.age()).isEqualTo(30);
            assertThat(result.jobFamily()).isEqualTo("기존직군");
            assertThat(result.jobRole()).isEqualTo("기존직무");
            assertThat(result.userDescription()).isEqualTo("기존소개");
            assertThat(result.userColor()).isEqualTo("#000000");
        }

        @Test
        @DisplayName("모든_필드가_null인_요청은_아무_것도_변경하지_않는다")
        void 모든_필드가_null인_요청은_아무_것도_변경하지_않는다() {
            // given
            User user = buildFullyPopulatedUser();
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            RequestUpdateProfileDto request = new RequestUpdateProfileDto(
                    null, null, null, null, null, null, null, null
            );

            // when
            ResponseMyProfileDto result = userService.modifyMyProfile(USER_ID, request);

            // then: buildFullyPopulatedUser()의 기존 값이 전부 그대로 유지
            assertThat(result.nickname()).isEqualTo("기존닉네임");
            assertThat(result.phone()).isEqualTo("010-0000-0000");
            assertThat(result.sex()).isEqualTo("F");
            assertThat(result.age()).isEqualTo(30);
            assertThat(result.jobFamily()).isEqualTo("기존직군");
            assertThat(result.jobRole()).isEqualTo("기존직무");
            assertThat(result.userDescription()).isEqualTo("기존소개");
            assertThat(result.userColor()).isEqualTo("#000000");
        }
    }

    @Nested
    @DisplayName("내 프로필 수정 실패")
    class ModifyMyProfileFailure {

        @Test
        @DisplayName("존재하지_않는_userId로_수정하면_USER_NOT_FOUND_예외가_발생한다")
        void 존재하지_않는_userId로_수정하면_USER_NOT_FOUND_예외가_발생한다() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());
            RequestUpdateProfileDto request = new RequestUpdateProfileDto(
                    "닉네임", null, null, null, null, null, null, null
            );

            // when & then
            assertThatThrownBy(() -> userService.modifyMyProfile(999L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}