package com.ssafy.backend.space.repository;

import com.ssafy.backend.space.entity.Member;
import com.ssafy.backend.space.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // SPACE-05: 요청자가 해당 스페이스의 멤버인지 인가 검사.
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    // SPACE-07: 나가기 대상 멤버 행 조회(멤버 여부 검사 겸용).
    Optional<Member> findByTeamIdAndUserId(Long teamId, Long userId);

    // SPACE-05: 스페이스 참여자(멤버) 목록 조회.
    List<Member> findByTeamId(Long teamId);

    // SPACE-02: 여러 스페이스의 참여자 수를 한 번에 집계([teamId, count]).
    @Query("select m.teamId, count(m) from Member m where m.teamId in :teamIds group by m.teamId")
    List<Object[]> countMembersByTeamIds(@Param("teamIds") List<Long> teamIds);

    /**
     * SPACE-02: 로그인 사용자가 참여 중인(members 조인) 삭제되지 않은 스페이스와 그 안에서의 내 권한.
     * Team↔Member 간 연관 매핑이 없어 teamId로 조인하며, 결과는 [Team, MemberAuthority] 배열이다.
     */
    @Query("""
            select t, m.authority
            from Team t, Member m
            where m.teamId = t.id
              and m.userId = :userId
              and t.isDeleted = false
            order by t.createdAt desc
            """)
    List<Object[]> findActiveTeamsWithMyAuthority(@Param("userId") Long userId);
}
