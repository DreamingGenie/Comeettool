# 프론트엔드 SPACE API 연동 계획

## 목표

현재 백엔드 소스에 실제 구현된 SPACE API를 프론트엔드의
`API → dataSource → store → Vue 화면` 흐름으로 연결한다.

## 구현 대상

| API | 백엔드 엔드포인트 | 프론트 처리 |
| --- | --- | --- |
| SPACE-01 스페이스 생성 | `POST /api/v1/spaces` | 기존 생성 모달 연동 회귀 확인 |
| SPACE-02 내 스페이스 목록 | `GET /api/v1/spaces` | 기존 홈·팀 도크 목록 연동 회귀 확인 |
| SPACE-05 스페이스 상세 | `GET /api/v1/spaces/{spaceId}` | 기존 팀 정보·멤버 목록 연동 회귀 확인 |
| SPACE-07 스페이스 나가기 | `DELETE /api/v1/spaces/{spaceId}/members/me` | 팀 설정 위험 영역에 확인 UI와 이동 처리 추가 |
| SPACE-11 스페이스 삭제 | `DELETE /api/v1/spaces/{spaceId}` | Owner 전용 삭제 확인 UI와 상태 정리 추가 |
| SPACE-101 소유권 위임 | `PATCH /api/v1/spaces/{spaceId}/owner` | Owner 전용 대상 멤버 선택·위임 UI 추가 |

## 구조 변경

1. `boardApi`에 스페이스 삭제와 소유권 위임 요청 함수를 추가한다.
2. `boardMockApi`에도 같은 인터페이스를 제공해 dataSource 전환 가능성을 유지한다.
3. `dataSource.board`에서 SPACE 관련 함수가 `VITE_USE_MOCK_SPACE_API` 설정을 따르게 한다.
4. `boardStore`에 삭제·위임 액션과 관련 반응형 상태 갱신을 구현한다.
5. `spaceMapper`에서 팀 상세의 멤버 식별자와 권한을 보존한다.
6. `TeamSettingsView`에서 사용자 권한에 따라 나가기·삭제·소유권 위임 기능을 제공한다.

## 오류 처리

- API 오류 메시지는 기존 공통 HTTP client가 만든 오류 객체를 사용해 토스트로 표시한다.
- Owner가 소유권 위임 없이 나가려는 경우 백엔드의 `409` 정책 응답을 그대로 안내한다.
- 삭제·나가기 성공 시 board store를 정리하고 `/home`으로 이동한다.
- 소유권 위임 성공 시 팀 상세와 워크스페이스 목록을 다시 조회한다.

## 검증

1. `npm run build`
2. 백엔드 SPACE 테스트 실행
3. 실제 API 모드에서 생성·목록·상세·나가기·삭제·소유권 위임 확인
4. 새로고침 후 목록 및 권한 상태 확인
5. Owner/Member별 버튼 노출과 오류 흐름 확인

## 제외 범위

백엔드에 아직 구현되지 않은 검색, 정렬, 정보 수정, 접근 제한, 초대 링크 API는
임의 엔드포인트를 만들어 연결하지 않는다.
