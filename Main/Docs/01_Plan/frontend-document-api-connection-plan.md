# 프론트엔드 DOC API 연동 계획

## 목표

채원찬 담당 백엔드에서 구현한 문서 REST API와 Yjs 협업 서버를 프론트엔드의
`API → DataSource → Store → Vue 화면` 흐름으로 연결하고 실제 동작을 검증한다.

## 기준 이슈

- `S15P11A707-110` `[FE] DOC 문서 REST API 연동`
- `S15P11A707-157` `DOC-01 독립 문서 생성 API 연동`
- 백엔드 구현 기준: `S15P11A707-19`, `S15P11A707-22`, `S15P11A707-84`

## 우선 연동 범위

| 기능 | 백엔드 엔드포인트 | 프론트엔드 처리 |
| --- | --- | --- |
| 문서 생성 | `POST /api/v1/document` | 생성 후 편집 화면으로 이동 |
| 문서 목록 | `GET /api/v1/document/list` | 팀별 목록, 빈 상태, 오류 상태 표시 |
| 문서 상세 | `GET /api/v1/document/{documentId}` | 편집기 진입 전 문서 정보 조회 |
| 협업 토큰 | `POST /api/v1/document/{documentId}/collaboration-token` | 권한과 만료 시간을 반영해 Yjs 연결 |
| 문서 삭제 | `DELETE /api/v1/document/{documentId}` | Owner 권한 확인 후 목록 상태 갱신 |
| 실시간 편집 | `WS /collaboration` | Tiptap, Yjs, 사용자 커서 및 읽기 권한 연결 |

## 작업 원칙

1. Vue 화면은 백엔드나 mock을 직접 호출하지 않고 `documentStore`만 사용한다.
2. `documentStore`는 `dataSource.document`를 통해 실제 API와 mock을 전환한다.
3. REST 응답 모델과 편집기에서 사용하는 ViewModel의 책임을 분리한다.
4. Spring REST 서버와 Yjs WebSocket 서버의 오류 상태를 구분해서 표시한다.
5. 백엔드 수정이 필요한 문제가 확인되면 프론트에서 우회 구현하지 않고 작업을 중단한 뒤 공유한다.

## 검증 순서

1. 최신 `develop` 기반 프론트엔드 빌드 확인
2. Spring 서버 `8080`, Yjs 서버 `3000` 실행
3. 로그인 후 실제 팀 ID로 문서 생성
4. 목록 및 상세 응답 확인
5. 협업 토큰 발급과 WebSocket 연결 확인
6. 두 브라우저 세션에서 실시간 편집과 커서 동기화 확인
7. Owner/Member/GUEST별 쓰기·읽기 권한 확인
8. 문서 삭제 후 목록 갱신 확인
9. 서버 중단·토큰 만료·권한 오류 UI 확인

## 후속 범위

다음 기능은 대응하는 Spring 공개 API가 준비된 뒤 별도 Jira 작업 단위로 연결한다.

- `DOC-06` 문서 권한 설정
- `DOC-07` Markdown 내보내기
- `DOC-08` 자동저장 버전 조회 및 복원

