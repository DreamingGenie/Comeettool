# REST API 엔드포인트 인벤토리 (초안)

개정된 [기능 명세서](../../01_Plan/functional-specification.md)를 바탕으로 뽑은 **MVP REST 엔드포인트 목록 초안**이다.
기능 ID를 교차참조 키로 사용하며, 상세 계약은 도메인별 상세 명세(`auth.md` 등)에 작성한다.

> ⚠️ **초안 · 제안:** 경로(path)·분해 방식은 제안이며 팀 검토로 확정한다.
> 공통 규약(Base URL `/api/v1`, 인증 헤더, 응답 래퍼 등)은 [README.md](README.md) §0 **확정본**을 따른다.
> 우선순위: `MVP` / `SUB` / `Extra`. WebSocket·내부처리는 §6에 별도 정리.

---

## 1. AUTH — 사용자·인증

| 기능 ID | 메서드 · 경로 | 설명 | 인증/권한 | 우선순위 |
| --- | --- | --- | --- | --- |
| AUTH-01 | `POST /auth/signup` | 회원가입 | 불필요 | MVP |
| AUTH-02 | `POST /auth/login` | 로그인(토큰 발급) | 불필요 | MVP |
| AUTH-03 | `POST /auth/token/refresh` | Access Token 재발급 | Refresh Token | MVP |
| AUTH-04 | `POST /auth/logout` | 로그아웃(Refresh 폐기) | 필요 | MVP |
| AUTH-05 | `GET /users/me` | 내 프로필 조회 | 필요 | MVP |
| AUTH-06 | `PATCH /users/me` | 내 프로필(닉네임·이미지) 수정 | 필요 | MVP |
| AUTH-07 | `PATCH /users/me/password` | 비밀번호 변경 | 필요 | MVP |
| AUTH-08 | `DELETE /users/me` | 회원 탈퇴 | 필요 | MVP |
| AUTH-09 | `POST /auth/password/reset` | 비밀번호 재설정(이메일) | 불필요 | SUB |
| AUTH-10 | `GET /users?query=` | 사용자 검색(이름·이메일) | 필요 | MVP |

## 2. SPACE — 팀 스페이스

| 기능 ID | 메서드 · 경로 | 설명 | 인증/권한 | 우선순위 |
| --- | --- | --- | --- | --- |
| SPACE-01 | `POST /spaces` | 팀 스페이스 생성 | 필요 | MVP |
| SPACE-02 | `GET /spaces` | 내 스페이스 목록(참여중/초대대기) | 필요 | MVP |
| SPACE-05 | `GET /spaces/{spaceId}` | 스페이스 상세 | 필요(멤버) | MVP |
| SPACE-07 | `DELETE /spaces/{spaceId}/members/me` | 스페이스 나가기 | 필요 | MVP |
| SPACE-09 | `POST /spaces/{spaceId}/invite-links` | 초대 링크/코드 생성·공유 (게스트 초대 포함) | Owner | MVP |
| SPACE-11 | `DELETE /spaces/{spaceId}` | 스페이스 삭제 | Owner | MVP |
| SPACE-03 | `GET /spaces?search=` | 스페이스 검색 | 필요 | Extra |
| SPACE-04 | `GET /spaces` · `PATCH /spaces/order` | 스페이스 정렬(사용자별 커스텀 순서, 기본 최신순) | 필요 | Extra |
| SPACE-08 | `PATCH /spaces/{spaceId}` | 스페이스 정보 수정 | Owner | Extra |

> SPACE-06 사이드바는 UI 내비게이션으로 전용 엔드포인트 없음(기존 조회 API 조합). SPACE-12 채팅은 WebSocket(§6).
> SPACE-10(접근 제한 정책)은 제거됨 — 정책 정의가 부재하고, 후보 의미(공개범위·가입승인·권한세분화)가 모두 불필요/중복으로 판단(2026-07-31).

## 3. MEMBER — 멤버·권한

| 기능 ID | 메서드 · 경로 | 설명 | 인증/권한 | 우선순위 |
| --- | --- | --- | --- | --- |
| MEMBER-01 | `GET /spaces/{spaceId}/members` | 멤버·권한 조회 | 필요(멤버) | MVP |
| MEMBER-02 | `POST /spaces/{spaceId}/invitations` | 멤버 초대(이름/닉네임 조회) | Owner | MVP |
| MEMBER-03 | `POST /invitations/{invitationId}/accept` | 초대 수락 | 필요 | MVP |
| MEMBER-04 | `POST /invitations/{invitationId}/reject` | 초대 거절 | 필요 | MVP |
| MEMBER-05 | `DELETE /spaces/{spaceId}/members/{memberId}` | 멤버 강퇴 | Owner | MVP |
| MEMBER-06 | `PATCH /spaces/{spaceId}/members/{memberId}/role` | 권한 변경 | Owner | MVP |
| MEMBER-07 | `PATCH /spaces/{spaceId}/members/me/profile` | 스페이스 프로필 설정 | 필요 | MVP |

> 게스트 초대 링크 생성은 SPACE-09로 통합(MEMBER에서 제거).

## 4. SCHEDULE — 일정

| 기능 ID | 메서드 · 경로 | 설명 | 인증/권한 | 우선순위 |
| --- | --- | --- | --- | --- |
| SCHEDULE-01 | `GET /me/schedules` | 내 일정 일괄 확인 | 필요 | SUB |
| SCHEDULE-02 | `GET /spaces/{spaceId}/schedules` | 팀 일정 목록 조회 | 필요(멤버) | SUB |
| SCHEDULE-03 | `POST /spaces/{spaceId}/schedules` | 일정 생성 | 필요(멤버) | SUB |
| SCHEDULE-04 | `PATCH /schedules/{scheduleId}` | 일정 수정 | 필요 | SUB |
| SCHEDULE-05 | `DELETE /schedules/{scheduleId}` | 일정 삭제 | 필요 | SUB |

## 5. MEET — 회의 생명주기

| 기능 ID | 메서드 · 경로 | 설명 | 인증/권한 | 우선순위 |
| --- | --- | --- | --- | --- |
| MEET-01 | `POST /spaces/{spaceId}/meetings` | 회의 생성(=시작) | 필요(멤버) | MVP |
| MEET-02 | `GET /spaces/{spaceId}/meetings` | 진행 중인 회의 목록 | 필요(멤버) | MVP |
| MEET-03 | `POST /meetings/{meetingId}/join` | 회의 입장 | 필요 | MVP |
| MEET-04 | `POST /meetings/{meetingId}/leave` | 회의 퇴장 | 필요 | MVP |
| MEET-05 | `POST /meetings/{meetingId}/end` | 회의 종료 | Host | MVP |
| MEET-06 | `GET /meetings/{meetingId}/participants` | 참여자·상태 조회 | 필요 | MVP |
| MEET-07 | `DELETE /meetings/{meetingId}/participants/{participantId}` | 참여자 강퇴 | Host | MVP |

## 6. CHAT / DOC / AI — REST 조회·관리성만

| 기능 ID | 메서드 · 경로 | 설명 | 인증/권한 | 우선순위 |
| --- | --- | --- | --- | --- |
| CHAT-02 | `GET /meetings/{meetingId}/chats` | 채팅 내역 조회 | 필요 | MVP |
| CHAT-04 | `POST /meetings/{meetingId}/chats/files` | 채팅 파일 첨부 | 필요 | SUB |
| DOC-01 | `POST /documents` | 문서 생성(회의 비종속·독립) | 필요 | MVP |
| DOC-05 | `GET /documents/{documentId}` | 문서 조회 | 필요 | MVP |
| DOC-06 | `PATCH /documents/{documentId}/permissions` | 편집 권한 설정 | 호스트/소유 | MVP |
| DOC-07 | `GET /documents/{documentId}/export` | Markdown 내보내기 | 필요 | SUB |
| DOC-08 | `GET /documents/{documentId}/versions` · `POST /documents/{documentId}/restore` | 자동저장 버전 목록·복원 | 필요 | SUB |
| AI-08 | `GET /meetings/{meetingId}/minutes` | 회의록 조회 | 필요 | MVP |
| AI-09 | `PATCH /meetings/{meetingId}/minutes` | 회의록 수정 | 필요 | MVP |
| AI-10 | `POST /meetings/{meetingId}/minutes/confirm` | 회의록 최종 확정 | Owner | MVP |
| AI-11 | `GET /meetings/{meetingId}/minutes/status` | 회의록 생성 상태 조회 | 필요 | MVP |
| AI-11 | `POST /meetings/{meetingId}/minutes/retry` | 회의록 생성 재시도 | Owner | MVP |
| AI-12 | `GET /meetings/{meetingId}/feedback` | 회의 피드백 조회 | 필요 | SUB |

---

## 7. REST 제외 항목 (WebSocket / 내부 처리) — 커버리지 명시용

| 기능 ID | 성격 | 처리 방식 |
| --- | --- | --- |
| RTC-01~09 | 실시간 미디어·장치 제어·화면공유·녹화 | WebRTC / SFU(LiveKit), 시그널링은 WebSocket |
| CHAT-01,03 | 실시간 메시지 송수신·시간 표시 | WebSocket (조회는 CHAT-02) |
| CHAT-05,06 | DM·스페이스 채팅 | WebSocket (Extra) |
| DOC-02~04 | 동시 편집·커서·자동 저장 | WebSocket + CRDT(Yjs) |
| AI-01~06 | 오디오 수집·STT·요약·항목추출 | 회의 종료 후 파이프라인(SQS→AI), 내부 처리 |
| AI-07(생성) | 회의록 생성 트리거 | 회의 종료 시 내부 트리거 (조회는 AI-08 GET) |
| AI-13 | 실시간 발언 피드백 | WebSocket (Extra) |
| SPACE-06 | 사이드바 | UI 집계, 전용 엔드포인트 없음 |

---

## 8. 확인·논의 필요 (인벤토리 발견 사항)

> ✅ **반영 완료:** 초대 링크 통합(SPACE-09, MEMBER에서 제거) · 사용자 검색 통합(AUTH-10) · 회의록 조회 추가(AI-08). 기능 명세서에도 반영됨.

1. **회의 status 파라미터** — MEET-01 생성=시작, MEET-02 진행 중만 조회이므로 status 필터가 불필요할 수 있음. 종료 회의 이력을 어디서 볼지(회의록 목록?) 확인.
2. **문서 경로** — 문서가 회의에 비종속(독립 운용)이라 `/documents` 최상위로 뒀다. 스페이스 소속 여부(`/spaces/{id}/documents`)를 팀이 확정.
3. **AI-11 2개 엔드포인트** — 상태 조회(GET)와 재시도(POST)를 한 기능 ID로 묶었다. 분해 표기 확인.
