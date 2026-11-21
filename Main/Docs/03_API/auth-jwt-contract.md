# JWT 인증 규약 (Auth Contract)

**Spring API 서버가 인증·인가의 단일 진실원천(SSoT)** 으로 앱 JWT를 발급한다.
이 JWT를 검증하는 주체는 여럿이다 — ① Spring 자신(REST 필터), ② **별도 협업 서버 Hocuspocus(Node)** — 문서 전용 협업 토큰(§7),
③ 회의 입장은 Spring이 앱 JWT를 검증한 뒤 **LiveKit 전용 토큰을 따로 발급**. FE·DOC·MEET 담당이 이 문서를 기준으로 연동한다.

> **핵심 원칙:** JWT는 **인증(누구인가)만** 담는다. **인가(무엇을 할 수 있나)** 는 토큰에 넣지 않고
> 각 서버가 요청·연결 시 검사한다. 그래서 지금의 Owner/Member/Guest든, 향후 "멤버별 세분화 권한"이든
> **토큰 변경 없이** 지원된다.
>
> **구현 뒤 달라진 점(2026-11 문서 갱신):** 이 문서는 구현 전(07-22)에 쓴 규약이라 코드와 어긋난 곳이 있었다.
> 로그인 응답(§3), `iss`·`aud` 클레임(§2), 문서 협업 토큰(§7)을 실제 구현에 맞춰 고쳤다.
> 협업 토큰은 위 원칙의 예외다 — 문서 하나에 한정된 5분짜리 토큰에 `permission`을 담는다(§7).

---

## 1. 토큰 개요

| 구분 | 값 |
| --- | --- |
| 서명 알고리즘 | **RS256** (RSA 키쌍) |
| 키 관리 | **개인키(서명)** = Spring만 보유, env/시크릿매니저, **git 커밋 금지**. **공개키(검증)** = 검증자(Hocuspocus 등)에 배포(공개돼도 안전) |
| 공개키 배포 | JWKS 엔드포인트(`/.well-known/jwks.json`) 또는 공개키 파일 공유 |
| Access 만료 | **30분** |
| Refresh 만료 | **14일** |
| 협업 토큰 만료 | **5분** (문서 연결용, §7) |
| 발급자·대상 | `iss` = `a707-api` · `aud` = `a707-api`(REST) / `a707-yjs`(협업 서버) |
| 라이브러리 | jjwt (io.jsonwebtoken, RSA 지원) |

> **RS256 이유:** 별도 서버(Hocuspocus)가 토큰을 검증해야 하므로, 시크릿을 나눠주지 않고 **공개키로만 검증**하게 한다(HS256 대칭키 공유보다 안전).

## 2. 클레임

**Access Token**
```json
{ "sub": "<userId>", "iss": "a707-api", "aud": ["a707-api"], "type": "access", "iat": 0, "exp": 0 }
```
**Refresh Token** (Spring 내부 전용 — 외부 검증자 없음)
```json
{ "sub": "<userId>", "iss": "a707-api", "aud": ["a707-api"], "type": "refresh", "jti": "<uuid>", "iat": 0, "exp": 0 }
```
- `sub` = 사용자 PK(userId). 권한·PII(이메일·닉네임)는 **넣지 않음**.
- Spring은 서명·`exp`에 더해 `iss`와 `aud`(`a707-api` 포함 여부)를 확인한다. `aud`가 다른 협업 토큰은 REST에서 거부된다.
- `exp` 검증에 **30~60초 clock skew** 허용.

## 3. 발급 · 전송

- **발급**: 로그인(`POST /api/v1/auth/login`) 성공 시 응답 바디
  ```json
  { "tokenType": "Bearer", "accessToken": "...", "refreshToken": "...", "userId": 1, "onboarded": false }
  ```
  - 초안의 `expiresIn`은 구현에서 빠졌다. 만료 시각은 access 토큰의 `exp`로 읽는다. 대신 로그인 직후 화면 분기에 쓰는 `userId`·`onboarded`를 준다.
- **사용(REST)**: `Authorization: Bearer <accessToken>` 헤더
- **갱신**: `POST /api/v1/auth/token/refresh` (refresh 제출 → 새 access 발급, **refresh 회전 없음**). 응답은 `{ "tokenType": "Bearer", "accessToken": "..." }`

## 4. 검증 · 에러

- 검증자는 **공개키로 서명 검증** + `exp` + `type=access` 확인. Spring 필터는 통과 시 SecurityContext **principal = userId**(요청마다 DB 조회 안 함).
- 에러(클라이언트가 만료/무효를 구분해 자동 처리하도록 **코드 분리**):

  | 상태 | code | 클라이언트 동작 |
  | --- | --- | --- |
  | 401 | `AUTH_TOKEN_EXPIRED` | 조용히 refresh로 재발급 후 재시도 |
  | 401 | `AUTH_TOKEN_INVALID` | 재로그인 |
  | 401 | `AUTH_UNAUTHORIZED` | 토큰 누락 |
  | 403 | (인가 실패) | 권한 없음 안내 |

## 5. Refresh 저장 · 로그아웃

- Refresh는 **Spring만** 발급·검증(외부 검증 불필요). Redis 키 **`refresh:{userId}`** (사용자당 1세션), TTL = 14일.
- **로그아웃**: Redis refresh 삭제. **access는 stateless라 최대 30분 잔존**(MVP 허용).

## 6. 인가 (토큰 밖, 각 서버가 검사)

- **REST(Spring)**: 팀스페이스 멤버십·권한을 요청 시 검사(권한 공통 모듈 P1). 추방/권한제거는 **다음 요청에서 즉시 거부**(토큰 수명 무관).
- **문서(Hocuspocus)**: §7 참조 — 연결 시 JWT 검증 + 문서 열람/편집 권한 확인.
- **회의(LiveKit)**: 클라이언트가 회의 입장 요청 → **Spring이 앱 JWT 검증 + 참여 권한 확인** → **LiveKit 전용 접속 토큰(LiveKit API Key/Secret 서명)** 발급 → 클라이언트가 그 토큰으로 SFU 입장. LiveKit은 자체 토큰 서명만 검증(앱 JWT를 직접 보지 않음).

## 7. WebSocket 문서 협업 (Yjs / Hocuspocus)

문서 실시간 협업은 **별도 Node 서버(Express + Hocuspocus)** 가 담당하며, **Spring이 발급한 문서 전용 협업 토큰을 검증**한다.
초안은 앱 access 토큰을 쿼리 파라미터로 넘기고 협업 서버가 권한을 따로 조회하는 방식이었다. 구현은 아래처럼 바뀌었다.

- **협업 토큰 발급**: `POST /api/v1/document/{documentId}/collaboration-token` (앱 access 토큰으로 인증). Spring이 문서 권한을 확인한 뒤 발급한다.
  ```json
  { "sub": "<userId>", "iss": "a707-api", "aud": ["a707-yjs"], "type": "collaboration",
    "documentId": "<uuid>", "teamId": 1, "permission": "READ | WRITE", "jti": "<uuid>", "iat": 0, "exp": 0 }
  ```
- **토큰 전달**: 클라이언트가 `HocuspocusProvider`의 `token` 옵션으로 넘긴다(URL 쿼리에 싣지 않는다).
- **핸드셰이크**(`Backend-Yjs/collaboration-auth.js`):
  1. Spring JWKS(`/.well-known/jwks.json`)의 공개키로 **RS256 서명·`iss`·`aud`·만료 검증**(clock skew 60초), `kid` 헤더 필수 → 실패 시 연결 거부
  2. `type=collaboration`, `documentId`가 연결하려는 문서와 같은지, `permission`이 `READ`/`WRITE`인지 확인
  3. 문서가 활성 상태인지 조회한 뒤 연결 컨텍스트에 userId·권한 부여
- **연결 후**: 세션 유지(핸드셰이크 시 1회 인증). 30분 access여도 장시간 편집 OK.

## 8. ⚠️ 실시간 즉시 무효화 (필수 구현)

WS 연결은 오래 열려 있어, **추방·권한변경 시 활성 연결을 능동적으로 종료**해야 즉시 차단된다.
- **문서**: **협업 서버(Hocuspocus)** 가 해당 userId의 문서 연결을 종료. 백엔드가 권한 변경을 협업 서버에 통지하거나, 협업 서버가 주기적으로 재확인하는 방식(합의 필요).
- **회의**: LiveKit 참여 권한 회수(서버 API로 kick).

## 9. 기타 정책

- **공개(인증 불필요) 엔드포인트**: 회원가입, 로그인, 토큰 갱신, 헬스체크. 그 외 전부 인증 필요(default deny).
- **CORS**: dev(FE `localhost:5173`) 명시 허용. prod는 Nginx 뒤 동일 오리진 전제(필요 시 도메인 명시).
- **CSRF**: Bearer 헤더 → 비대상, 비활성.
- **비밀번호**: BCrypt 해싱.

---

## 부록. 담당별 의존 요약

**DOC (Hocuspocus, Node)**
1. **공개키(JWKS)로 협업 토큰 검증**(RS256, `aud=a707-yjs`) — 시크릿 불필요
2. **토큰 = `HocuspocusProvider`의 `token` 옵션**으로 전달
3. 핸드셰이크에서 검증 + 토큰의 `documentId`·`permission` 확인(권한 판단은 Spring이 발급 시점에 한다)
4. **추방/권한변경 시 활성 문서 연결 종료**

**MEET (LiveKit)**
- Spring이 앱 JWT 검증 + 참여 권한 확인 후 **LiveKit 전용 토큰 발급**. LiveKit은 자체 토큰만 검증.

**공통**: principal(userId)로 각 서버가 멤버십·문서/회의 권한을 검사한다.
