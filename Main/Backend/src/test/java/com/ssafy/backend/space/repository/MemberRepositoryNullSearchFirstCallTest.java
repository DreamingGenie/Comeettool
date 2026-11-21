package com.ssafy.backend.space.repository;

import com.ssafy.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * SPACE-03 회귀: 검색어 null이 "그 쿼리의 첫 호출"일 때도 목록 조회가 실패하지 않는지 확인한다.
 *
 * <p>{@code cast(:search as string)}이 없던 코드는 호출 순서에 따라 다르게 동작했다(2026-11-21 실측).
 * 첫 호출이 {@code search=null}이면 그 호출과 이후 호출이 모두 {@code lower(bytea)} 42883으로 실패하고,
 * 첫 호출에 문자열을 넘기면 이후의 {@code null} 호출도 통과했다. 서버를 띄운 뒤 검색어 없이 목록부터 여는
 * 실제 사용 순서가 실패하는 쪽이다.</p>
 *
 * <p>그래서 {@code MemberRepositoryTest} 안의 null 테스트는 같은 클래스의 문자열 검색 테스트보다 먼저 돌 때만
 * 결함을 잡는다. 이 클래스는 다른 테스트와 컨텍스트를 공유하지 않도록 전용 프로퍼티를 줘서, 새 컨텍스트에서
 * null 호출이 반드시 첫 호출이 되게 한다. 테스트 메서드는 하나만 둔다.</p>
 */
@Tag("integration")
@DataJpaTest(properties = "comeettool.test.context=member-null-search-first-call")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("MemberRepository 검색어 null 첫 호출 회귀 테스트")
class MemberRepositoryNullSearchFirstCallTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("SPACE-03 회귀: 새 컨텍스트의 첫 호출이 search=null이어도 예외가 나지 않는다(lower(bytea) 42883)")
    void nullSearchAsVeryFirstCallDoesNotFail() {
        // 결과 행은 보지 않는다 — 타입 오류는 조회 대상이 없어도 쿼리를 실행하는 순간 난다.
        assertThatCode(() -> memberRepository.findActiveTeamsWithMyAuthority(-1L, null))
                .doesNotThrowAnyException();
    }
}
