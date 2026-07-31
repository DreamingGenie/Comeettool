package com.ssafy.backend.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.backend.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // SPACE-05: 요청자가 해당 스페이스의 멤버인지 인가 검사.
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    // SPACE-07: 나가기 대상 멤버 행 조회(멤버 여부 검사 겸용).
    Optional<Member> findByTeamIdAndUserId(Long teamId, Long userId);

    // SPACE-05: 스페이스 참여자(멤버) 목록 조회.
    List<Member> findByTeamId(Long teamId);

    // SPACE-07: Owner 나가기 흐름 분기 — 스페이스의 현재 멤버 수(1이면 혼자, 2+면 위임 대상 존재).
    long countByTeamId(Long teamId);

    /**
     * MEET-01: 최초 참여자 등록 시점의 팀 역할명을 조회한다.
     */
    @Query(
            value = """
                    select team_role.role_name
                    from members member_record
                    left join team_roles team_role
                      on team_role.team_role_id = member_record.team_role_id
                    where member_record.member_id = :memberId
                    """,
            nativeQuery = true
    )
    Optional<String> findTeamRoleNameByMemberId(@Param("memberId") Long memberId);

    // SPACE-02: 여러 스페이스의 참여자 수를 한 번에 집계([teamId, count]).
    @Query("select m.teamId, count(m) from Member m where m.teamId in :teamIds group by m.teamId")
    List<Object[]> countMembersByTeamIds(@Param("teamIds") List<Long> teamIds);

    /**
     * SPACE-02/03: 로그인 사용자가 참여 중인(members 조인) 삭제되지 않은 스페이스와 그 안에서의 내 권한. Team↔Member 간 연관 매핑이 없어 teamId로 조인하며, 결과는 [Team,
     * MemberAuthority] 배열이다.
     * SPACE-03 검색: search가 null이면 전체, 값이 있으면 스페이스 이름 부분 일치(대소문자 무시)로 필터한다.
     */
    @Query("""
            select t, m.authority
            from Team t, Member m
            where m.teamId = t.id
              and m.userId = :userId
              and t.isDeleted = false
              and (:search is null or lower(t.name) like lower(concat('%', :search, '%')))
            order by t.createdAt desc
            """)
    List<Object[]> findActiveTeamsWithMyAuthority(@Param("userId") Long userId, @Param("search") String search);

    /**
     * MEET-09: 회의가 속한 팀에서 아직 해당 회의에 초대되지 않은 멤버를 조회한다.
     *
     * 반환 배열 구조:
     * row[0] = Member
     * row[1] = User
     *
     * query가 빈 문자열이면 LIKE '%%'가 되어 초대 가능한 전체 멤버를 반환한다.
     */
    @Query("""
            select m, u
            from Member m, User u
            where m.userId = u.id
              and m.teamId = :teamId
              and u.isDeleted = false
              and not exists (
                  select p.id
                  from Participant p
                  where p.meetingRoomId = :meetingId
                    and p.memberId = m.id
              )
              and (
                  lower(m.nickname) like lower(concat('%', :query, '%'))
                  or lower(u.email) like lower(concat('%', :query, '%'))
              )
            order by m.id asc
            """)
    List<Object[]> findMeetingInviteCandidates(
            @Param("teamId") Long teamId,
            @Param("meetingId") Long meetingId,
            @Param("query") String query
    );
}
