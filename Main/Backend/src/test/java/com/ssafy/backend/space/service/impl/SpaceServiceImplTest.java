package com.ssafy.backend.space.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.entity.Member;
import com.ssafy.backend.space.entity.MemberRole;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.mapper.SpaceMapper;
import com.ssafy.backend.space.repository.MemberRepository;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SpaceServiceImpl 단위 테스트. Repository/Mapper는 전부 Mock — DB 없이 서비스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpaceServiceImpl 단위 테스트")
class SpaceServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long TEAM_ID = 10L;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    // 실제 매퍼를 주입해 변환 결과까지 검증한다(순수 변환 로직이라 @Spy로 실제 구현 사용).
    @Spy
    private SpaceMapper spaceMapper = new SpaceMapper();

    @InjectMocks
    private SpaceServiceImpl spaceService;

    private User userWithNickname(String nickname, String email) {
        User user = User.builder().email(email).password("enc").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "nickname", nickname);
        return user;
    }

    private Team teamWithId(Long id, Long ownerId) {
        Team team = Team.builder().name("팀A").description("설명").ownerId(ownerId).color("#123456").build();
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    @Nested
    @DisplayName("SPACE-01 스페이스 생성")
    class CreateSpace {

        @Test
        @DisplayName("스페이스를_생성하면_teams가_저장되고_생성자가_Owner_멤버로_등록된다")
        void 스페이스를_생성하면_teams가_저장되고_생성자가_Owner_멤버로_등록된다() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", "설명", "#123456", null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname("진", "u@e.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> {
                Team t = inv.getArgument(0);
                ReflectionTestUtils.setField(t, "id", TEAM_ID);
                return t;
            });

            // when
            ResponseCreateSpaceDto response = spaceService.createSpace(USER_ID, request);

            // then
            assertThat(response.spaceId()).isEqualTo(TEAM_ID);
            assertThat(response.ownerId()).isEqualTo(USER_ID);
            assertThat(response.teamName()).isEqualTo("팀A");
            assertThat(response.teamColor()).isEqualTo("#123456");

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());
            Member savedMember = memberCaptor.getValue();
            assertThat(savedMember.getUserId()).isEqualTo(USER_ID);
            assertThat(savedMember.getTeamId()).isEqualTo(TEAM_ID);
            assertThat(savedMember.getRole()).isEqualTo(MemberRole.OWNER);
            assertThat(savedMember.getAuthority()).isEqualTo(MemberRole.OWNER);
        }

        @Test
        @DisplayName("색상_미지정_시_기본색_000000이_적용된다")
        void 색상_미지정_시_기본색_000000이_적용된다() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname("진", "u@e.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            ResponseCreateSpaceDto response = spaceService.createSpace(USER_ID, request);

            // then
            assertThat(response.teamColor()).isEqualTo("#000000");
        }

        @Test
        @DisplayName("닉네임이_없으면_이메일_로컬파트를_멤버_닉네임으로_사용한다")
        void 닉네임이_없으면_이메일_로컬파트를_멤버_닉네임으로_사용한다() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname(null, "hong@example.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            spaceService.createSpace(USER_ID, request);

            // then
            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getNickname()).isEqualTo("hong");
        }

        @Test
        @DisplayName("존재하지_않는_사용자면_AUTH_UNAUTHORIZED_예외가_발생하고_저장하지_않는다")
        void 존재하지_않는_사용자면_예외가_발생하고_저장하지_않는다() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> spaceService.createSpace(USER_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
            verify(teamRepository, never()).save(any(Team.class));
            verify(memberRepository, never()).save(any(Member.class));
        }
    }

    @Nested
    @DisplayName("SPACE-02 참여 스페이스 목록")
    class FindMySpaces {

        @Test
        @DisplayName("참여_중인_스페이스가_없으면_빈_목록을_반환한다")
        void 참여_중인_스페이스가_없으면_빈_목록을_반환한다() {
            given(memberRepository.findActiveTeamsByUserId(USER_ID)).willReturn(List.of());

            List<ResponseSpaceListDto> result = spaceService.findMySpaces(USER_ID);

            assertThat(result).isEmpty();
            verify(memberRepository, never()).countMembersByTeamIds(anyList());
        }

        @Test
        @DisplayName("참여_스페이스마다_내_역할과_참여자_수를_채워_반환한다")
        void 참여_스페이스마다_내_역할과_참여자_수를_채워_반환한다() {
            // given
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member myMembership = Member.owner(USER_ID, TEAM_ID, "진");
            given(memberRepository.findActiveTeamsByUserId(USER_ID)).willReturn(List.of(team));
            given(memberRepository.findByUserId(USER_ID)).willReturn(List.of(myMembership));
            given(memberRepository.countMembersByTeamIds(List.of(TEAM_ID)))
                    .willReturn(List.<Object[]>of(new Object[]{TEAM_ID, 3L}));

            // when
            List<ResponseSpaceListDto> result = spaceService.findMySpaces(USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).spaceId()).isEqualTo(TEAM_ID);
            assertThat(result.get(0).myRole()).isEqualTo("OWNER");
            assertThat(result.get(0).memberCount()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("SPACE-05 스페이스 상세")
    class FindSpaceDetail {

        @Test
        @DisplayName("멤버면_스페이스_정보와_참여자_목록을_반환한다")
        void 멤버면_스페이스_정보와_참여자_목록을_반환한다() {
            // given
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member owner = Member.owner(USER_ID, TEAM_ID, "진");
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(true);
            given(memberRepository.findByTeamId(TEAM_ID)).willReturn(List.of(owner));

            // when
            ResponseSpaceDetailDto result = spaceService.findSpaceDetail(USER_ID, TEAM_ID);

            // then
            assertThat(result.spaceId()).isEqualTo(TEAM_ID);
            assertThat(result.members()).hasSize(1);
            assertThat(result.members().get(0).role()).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("존재하지_않거나_삭제된_스페이스면_SPACE_NOT_FOUND_예외가_발생한다")
        void 존재하지_않거나_삭제된_스페이스면_SPACE_NOT_FOUND_예외가_발생한다() {
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> spaceService.findSpaceDetail(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
        }

        @Test
        @DisplayName("멤버가_아니면_SPACE_ACCESS_DENIED_예외가_발생하고_참여자를_조회하지_않는다")
        void 멤버가_아니면_SPACE_ACCESS_DENIED_예외가_발생한다() {
            Team team = teamWithId(TEAM_ID, 99L);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(false);

            assertThatThrownBy(() -> spaceService.findSpaceDetail(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verify(memberRepository, never()).findByTeamId(TEAM_ID);
        }
    }
}
