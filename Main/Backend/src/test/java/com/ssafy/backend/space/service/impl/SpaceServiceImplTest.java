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
    class AddSpace {

        @Test
        @DisplayName("스페이스를 생성하면 teams가 저장되고 생성자가 Owner 멤버로 등록된다")
        void addSpace_registersCreatorAsOwnerMember() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", "설명", "#123456", null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname("진", "u@e.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> {
                Team t = inv.getArgument(0);
                ReflectionTestUtils.setField(t, "id", TEAM_ID);
                return t;
            });

            // when
            ResponseCreateSpaceDto response = spaceService.addSpace(USER_ID, request);

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
        @DisplayName("색상 미지정 시 기본색 #000000이 적용된다")
        void addSpace_appliesDefaultColorWhenBlank() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname("진", "u@e.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            ResponseCreateSpaceDto response = spaceService.addSpace(USER_ID, request);

            // then
            assertThat(response.teamColor()).isEqualTo("#000000");
        }

        @Test
        @DisplayName("닉네임이 없으면 이메일 로컬파트를 멤버 닉네임으로 사용한다")
        void addSpace_fallsBackToEmailLocalPartForNickname() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname(null, "hong@example.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            spaceService.addSpace(USER_ID, request);

            // then
            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getNickname()).isEqualTo("hong");
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 AUTH_UNAUTHORIZED 예외가 발생하고 저장하지 않는다")
        void addSpace_throwsWhenUserNotFound() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> spaceService.addSpace(USER_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
            verify(teamRepository, never()).save(any(Team.class));
            verify(memberRepository, never()).save(any(Member.class));
        }
    }

    @Nested
    @DisplayName("SPACE-02 참여 스페이스 목록")
    class FindSpaceList {

        @Test
        @DisplayName("참여 중인 스페이스가 없으면 빈 목록을 반환한다")
        void findSpaceList_returnsEmptyWhenNoneJoined() {
            given(memberRepository.findActiveTeamsWithMyRole(USER_ID)).willReturn(List.of());

            List<ResponseSpaceListDto> result = spaceService.findSpaceList(USER_ID);

            assertThat(result).isEmpty();
            verify(memberRepository, never()).countMembersByTeamIds(anyList());
        }

        @Test
        @DisplayName("참여 스페이스마다 내 역할과 참여자 수를 채워 반환한다")
        void findSpaceList_fillsMyRoleAndMemberCount() {
            // given
            Team team = teamWithId(TEAM_ID, USER_ID);
            given(memberRepository.findActiveTeamsWithMyRole(USER_ID))
                    .willReturn(List.<Object[]>of(new Object[]{team, MemberRole.OWNER}));
            given(memberRepository.countMembersByTeamIds(List.of(TEAM_ID)))
                    .willReturn(List.<Object[]>of(new Object[]{TEAM_ID, 3L}));

            // when
            List<ResponseSpaceListDto> result = spaceService.findSpaceList(USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).spaceId()).isEqualTo(TEAM_ID);
            assertThat(result.get(0).myRole()).isEqualTo("OWNER");
            assertThat(result.get(0).memberCount()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("SPACE-05 스페이스 상세")
    class FindSpaceDetails {

        @Test
        @DisplayName("멤버면 스페이스 정보와 참여자 목록을 반환한다")
        void findSpaceDetails_returnsInfoAndMembersForMember() {
            // given
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member owner = Member.owner(USER_ID, TEAM_ID, "진");
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(true);
            given(memberRepository.findByTeamId(TEAM_ID)).willReturn(List.of(owner));

            // when
            ResponseSpaceDetailDto result = spaceService.findSpaceDetails(USER_ID, TEAM_ID);

            // then
            assertThat(result.spaceId()).isEqualTo(TEAM_ID);
            assertThat(result.members()).hasSize(1);
            assertThat(result.members().get(0).role()).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생한다")
        void findSpaceDetails_throwsNotFoundWhenAbsent() {
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> spaceService.findSpaceDetails(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
        }

        @Test
        @DisplayName("멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 참여자를 조회하지 않는다")
        void findSpaceDetails_throwsAccessDeniedForNonMember() {
            Team team = teamWithId(TEAM_ID, 99L);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(false);

            assertThatThrownBy(() -> spaceService.findSpaceDetails(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verify(memberRepository, never()).findByTeamId(TEAM_ID);
        }
    }
}
