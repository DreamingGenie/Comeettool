# CHAT 도메인 API 명세

- **Base URL**: `/api/v1` (각 EndPoint 표기에서는 생략)
- **인증**: `Auth: O`인 엔드포인트는 `Authorization: Bearer <accessToken>` 필요
- **응답 래퍼**: 성공 `{ "code": "SUCCESS", "message": ..., "data": ... }` / 실패 `{ "code": ..., "message": ..., "errors": [] }`
- **날짜/시간**: ISO 8601 UTC (예 `2026-08-05T00:31:16Z`)

## 구현 상태

| 항목 | 상태 |
|---|---|
| CHAT-01 실시간 전달 (LiveKit 데이터 채널) | ✅ **구현 완료** — REST 아님 |
| CHAT-01 채팅 저장 (`POST /meetings/{meetingId}/chats`) | ⬜ 계획 (2단계) |
| CHAT-02 채팅 내역 조회 (`GET /meetings/{meetingId}/chats`) | ⬜ 계획 (2단계) |
| CHAT-04 파일 첨부 | ⬜ 미착수 (SUB) |
| CHAT-05 / CHAT-06 DM | ⬜ 미착수 (Extra) |

> ⚠️ **기능 ID 확인 필요**: 인벤토리의 CHAT-01은 "실시간 메시지 송수신(WebSocket)" 하나로 정의돼 있다.
> 실제 구현은 **실시간 전달(LiveKit 데이터 채널)** 과 **저장(REST POST)** 두 메커니즘으로 나뉘므로
> CHAT-01을 2개로 분해할지, 저장에 새 ID를 부여할지 팀 확정이 필요하다.

---

# CHAT-01 실시간 채팅 전달 (LiveKit 데이터 채널)

카테고리: CHAT
설명: 회의 참여자 간 전체 채팅 메시지를 실시간 전달
Method: — (REST 아님 / LiveKit Data Channel)
EndPoint: — (LiveKit 시그널링 연결을 그대로 사용)
Auth: O (회의 입장 토큰의 `CanPublishData` 권한)
사용자: Participant

### Request

전송은 `LocalParticipant.publishData(payload, options)`로 이루어진다.

**publishData options**

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| topic | 메시지 종류 식별자. 이 값이 아니면 수신 측이 무시한다 | string | 필수 | N | `chat` |
| reliable | 순서·전달 보장 사용 여부 | boolean | 필수 | N | `true` |

**payload** (UTF-8 인코딩된 JSON)

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| v | 페이로드 스키마 버전 | int | 필수 | N | 1 |
| body | 메시지 본문 | string | 필수 | N | `회의 자료 올렸습니다` |
| sentAt | 보낸 시각 (발신자 기기 기준) | string(ISO 8601) | 필수 | N | `2026-08-05T00:31:16Z` |

**Example**

```jsx
{
	"v": 1,
	"body": "회의 자료 올렸습니다",
	"sentAt": "2026-08-05T00:31:16Z"
}
```

### Response

응답 개념이 없다(단방향 발행). 수신 측은 `RoomEvent.DataReceived`로 아래를 조립한다.

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| body | payload에서 그대로 사용 | string | 필수 | N | `회의 자료 올렸습니다` |
| sentAt | payload에서 그대로 사용 | string(ISO 8601) | 필수 | N | `2026-08-05T00:31:16Z` |
| sender | **payload가 아니라** `participant.name`에서 가져온다 | string | 필수 | N | `지니` |
| senderIdentity | LiveKit participant identity | string | 필수 | N | `participant-12` |

### Status

| status | response content |
| --- | --- |
| — | 전송 성공 시 반환값 없음 |
| — | `topic`이 `chat`이 아니거나 JSON 파싱 실패 시 수신 측에서 조용히 폐기 |

### 비고

- **발신자 이름은 payload를 신뢰하지 않는다.** 서버가 입장 토큰에 넣은
  `participant.name`(= `member.nickname`)을 사용한다. payload의 값을 쓰면 참가자가
  타인의 이름으로 메시지를 보낼 수 있다.
- **발신자에게는 에코되지 않는다.** 보낸 사람은 자기 메시지를 직접 화면에 추가해야 한다.
- **저장되지 않는다.** 새로고침·재입장 시 사라지고, 늦게 입장한 참가자는 이전 대화를 볼 수 없다.
  → CHAT-01 저장 / CHAT-02 조회로 해결한다.
- TURN/TLS 443 릴레이 구간에서도 동일하게 동작한다(시그널링 연결을 공유).

---

# CHAT-01 채팅 저장

카테고리: CHAT
설명: 전체 채팅 메시지를 서버에 저장
Method: POST
EndPoint: /meetings/{meetingId}/chats
Auth: O
사용자: Participant

### Request

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| meetingId | 회의 id (path) | int | 필수 | N | 1 |
| body | 메시지 본문 (1~2000자) | string | 필수 | N | `회의 자료 올렸습니다` |

**Query parameter**

없음

**Example**

```jsx
{
	"body": "회의 자료 올렸습니다"
}
```

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| chatId | 저장된 채팅 id | int | 필수 | N | 1024 |
| senderMemberId | 발신자 member id | int | 필수 | N | 7 |
| senderNickname | 발신 당시 닉네임 스냅샷 | string | 필수 | N | `지니` |
| body | 메시지 본문 | string | 필수 | N | `회의 자료 올렸습니다` |
| sentAt | 서버 저장 시각 | string(ISO 8601) | 필수 | N | `2026-08-05T00:31:16Z` |

**Example**

```jsx
{
	"code": "SUCCESS",
	"message": "요청이 정상 처리되었습니다.",
	"data": {
		"chatId": 1024,
		"senderMemberId": 7,
		"senderNickname": "지니",
		"body": "회의 자료 올렸습니다",
		"sentAt": "2026-08-05T00:31:16Z"
	}
}
```

### Status

| status | response content |
| --- | --- |
| 201 | `SUCCESS` — 저장 성공 |
| 400 | `VALIDATION_FAILED` — body 누락 / 길이 초과 |
| 401 | `AUTH_UNAUTHORIZED` — 토큰 없음 또는 무효 |
| 403 | `MEETING_ACCESS_DENIED` — 회의가 속한 팀의 멤버가 아님 |
| 404 | `MEETING_NOT_FOUND` — 삭제되었거나 존재하지 않는 회의 |
| 404 | `MEETING_PARTICIPANT_NOT_FOUND` — 해당 회의에 등록된 참여자가 아님 |

### 비고

- **실시간 전달 경로와 독립이다.** 프론트는 데이터 채널로 먼저 보내고 이 API를 별도로 호출한다.
  저장이 실패해도 진행 중인 대화는 끊기지 않는다.
- `sentAt`은 **서버 시각**을 쓴다. 데이터 채널의 `sentAt`(발신자 기기 시각)과 달라질 수 있으며,
  이력의 정렬 기준은 서버 시각이다.
- `senderNickname`을 컬럼으로 저장하는 이유: 이후 닉네임이 변경돼도 **당시 기록이 보존**된다.
  조인으로 현재 닉네임을 끌어오면 과거 대화의 발신자 이름이 소급 변경된다.

---

# CHAT-02 채팅 내역 조회

카테고리: CHAT
설명: 채팅 내역 조회
Method: GET
EndPoint: /meetings/{meetingId}/chats
Auth: O
사용자: Participant

### Request

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| meetingId | 회의 id (path) | int | 필수 | N | 1 |

**Query parameter**

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| before | 이 chatId보다 과거의 메시지만 조회. 생략 시 최신부터 | int | 선택 | Y | 1001 |
| size | 한 번에 가져올 개수 (기본 50, 최대 100) | int | 선택 | Y | 50 |

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |
| items | 메시지 목록. **과거 → 최신 오름차순** | array | 필수 | N | 아래 참조 |
| items[].chatId | 채팅 id | int | 필수 | N | 1024 |
| items[].senderMemberId | 발신자 member id | int | 필수 | N | 7 |
| items[].senderNickname | 발신 당시 닉네임 스냅샷 | string | 필수 | N | `지니` |
| items[].body | 메시지 본문 | string | 필수 | N | `회의 자료 올렸습니다` |
| items[].sentAt | 서버 저장 시각 | string(ISO 8601) | 필수 | N | `2026-08-05T00:31:16Z` |
| items[].mine | 요청자가 보낸 메시지인지. 서버가 계산 | boolean | 필수 | N | false |
| nextBefore | 더 과거를 요청할 때 쓸 커서. 없으면 null | int | 필수 | Y | 1001 |
| hasNext | 더 과거 메시지가 남았는지 | boolean | 필수 | N | true |

**Example**

```jsx
{
	"code": "SUCCESS",
	"message": "요청이 정상 처리되었습니다.",
	"data": {
		"items": [
			{
				"chatId": 1001,
				"senderMemberId": 3,
				"senderNickname": "이지은",
				"body": "회의 시작하겠습니다",
				"sentAt": "2026-08-05T00:30:02Z",
				"mine": false
			},
			{
				"chatId": 1024,
				"senderMemberId": 7,
				"senderNickname": "지니",
				"body": "회의 자료 올렸습니다",
				"sentAt": "2026-08-05T00:31:16Z",
				"mine": true
			}
		],
		"nextBefore": 1001,
		"hasNext": true
	}
}
```

### Status

| status | response content |
| --- | --- |
| 200 | `SUCCESS` — 조회 성공. 메시지가 없으면 `items: []`, `hasNext: false` |
| 400 | `VALIDATION_FAILED` — `size`가 범위를 벗어남 / `before`가 정수가 아님 |
| 401 | `AUTH_UNAUTHORIZED` — 토큰 없음 또는 무효 |
| 403 | `MEETING_ACCESS_DENIED` — 회의가 속한 팀의 멤버가 아님 |
| 404 | `MEETING_NOT_FOUND` — 삭제되었거나 존재하지 않는 회의 |

### 비고

- **커서 방식을 택한 이유**: 회의 하나의 채팅이 수천 건이 될 수 있어 전체 반환은 위험하다.
  offset 방식은 조회 중 새 메시지가 들어오면 경계가 밀려 중복·누락이 발생한다.
- `items`를 **오름차순으로 반환**하므로 프론트는 그대로 위→아래 렌더링하면 된다.
  더 과거를 불러올 때는 `nextBefore`를 `before`로 넘기고, 받은 배열을 기존 목록 **앞에** 붙인다.
- 회의 입장 시 이 API를 먼저 호출해 화면을 채우고, 이후 증분은 데이터 채널로 받는다.

---

## 데이터 모델 (계획)

`chats` 테이블은 아직 마이그레이션에 없다. `V3__add_chats.sql`로 추가한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| chat_id | BIGINT IDENTITY | PK |
| meeting_room_id | BIGINT NOT NULL | FK → `meeting_rooms` |
| member_id | BIGINT NOT NULL | 발신자 (FK → `members`) |
| sender_nickname | VARCHAR NOT NULL | 발신 당시 닉네임 스냅샷 |
| chat | TEXT NOT NULL | 메시지 본문 |
| date | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | 서버 저장 시각 |

- 인덱스: `(meeting_room_id, chat_id)` — 커서 페이징 조회 경로
- **설계 문서([database-schema.md](../../01_Plan/database-schema.md))와 다른 점 2가지**
  1. `member_id` + `sender_nickname` **추가** — 원안에는 발신자 컬럼이 없어 누가 썼는지 저장할 수 없다
  2. `team_id` **제거** — `meeting_rooms`에 이미 있어 중복이며 불일치 위험만 생긴다
- `member_id`를 쓰는 이유: `participant_id`는 회의 참여 레코드라 강퇴 등으로 삭제되면 이력이 고아가 된다.
- 파일 첨부(CHAT-04)·DM(CHAT-05/06)은 이 테이블 범위 밖이다.
