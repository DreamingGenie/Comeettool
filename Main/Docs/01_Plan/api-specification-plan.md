# API 명세 작성 계획

## 개요

실시간 화상회의 + AI 회의록 협업 플랫폼(7인 / 6주)의 API 명세를 작성하기 위한 계획 문서다.
목표 흐름은 **명세는 다 같이 작성(design-first) → 구현·연결은 각자 담당 파트**다.
FE 1명 + 풀스택 5명이 동일한 계약(contract)을 보고 병렬 작업하려면, 명세 작성 전에
**공통 규약**을 먼저 고정해야 한다.

- 근거 문서: [기능 명세서](functional-specification.md), [서비스 아키텍처](architecture.md),
  [백엔드·인프라 준비](backend-infra-prep.md)(특히 §2-1 팀 합의 사항)
- 컨벤션: [코드 컨벤션](../00_Convention/02_code-convention.md), [API 컨벤션](../00_Convention/03_api-convention.md)

---

## 1. 작성 방식 (확정)

### 1-1. 하이브리드 design-first

OpenAPI 계약을 **먼저 합의**하고, 구현할 때 springdoc 어노테이션으로 **살아있는 문서**를 유지한다.

| 단계 | 진실의 원천(source of truth) | 역할 |
| --- | --- | --- |
| 설계 | OpenAPI 계약 문서 | 합의 · 리뷰 · FE mock · 병렬 작업 기준 |
| 구현 후 | springdoc(Swagger UI) | 실제 서버와 일치하는 런타임 문서, FE 호출 테스트 |

> ⚠️ 손으로 쓴 YAML과 코드 어노테이션을 **동시에 영구 동기화하지 않는다.** 구현 시작 시점에
> 진실의 원천을 springdoc로 넘기고, 설계 YAML은 v1 기록으로 동결한다.
> (여력 시 CI에서 코드 생성 openapi와 커밋 YAML을 비교해 drift 감지 — 선택)

### 1-2. 산출물 형식 — 고도(altitude) 분리

같은 내용을 두 번 쓰지 않도록 역할을 나눈다.

| 형식 | 담는 내용 |
| --- | --- |
| **Markdown** | API 계획, 공통 규약, 도메인별 엔드포인트 **목록·설명**(사람 합의층) |
| **OpenAPI YAML** | 요청/응답 **스키마 상세**(기계 계약층) |

- "md에 전체 상세 → 그대로 yaml 재작성"은 실질 중복이므로 지양.

### 1-3. 범위

- **REST 전체 MVP** (AUTH / SPACE / MEMBER / MEET / DOC·CHAT·AI 중 REST 성격).
- 실시간 채널(RTC 시그널링 · CHAT 실시간 · DOC 동시편집)은 **WebSocket**이라 OpenAPI로 담기지 않는다 → **별도 문서로 분리**.

---

## 2. 산출물 구조

```
Main/Docs/01_Plan/
  api-specification-plan.md      # 본 문서
  api-conventions.md             # 공통 규약 합의안 (§4) — 03_api-convention.md 보강/링크
Main/Docs/03_API/                # 신규
  rest/
    openapi.yaml                 # 통합 진입점 (또는 도메인별 분할 + $ref)
    auth.md / space.md / ...     # 도메인별 엔드포인트 목록·설명 (사람 합의층)
  websocket/
    realtime-protocol.md         # 시그널링·채팅·문서 WebSocket 메시지 규약 (추후)
```

> 폴더명·분할 방식(도메인별 YAML vs 단일 openapi.yaml)은 파일럿 후 팀과 최종 확정.

---

## 3. 작업 단계

| 단계 | 내용 | 산출물 | 선행 조건 |
| --- | --- | --- | --- |
| 1 | 계획 문서화 | `api-specification-plan.md` | - |
| 2 | 공통 규약 합의안 초안 ★ | `api-conventions.md` | 팀 회의 안건 |
| 3 | REST 엔드포인트 인벤토리 | 도메인별 md | 기능명세 |
| 4 | 상세 스키마 작성 (AUTH 파일럿) | `openapi.yaml` | **2단계 확정** |
| 5 | 구현 + springdoc 연계 | 각 파트 코드 | 계약 v1 동결 |

★ **2단계(공통 규약)는 명세 작성의 선행 조건**이다. 확정 전까지 상세 스키마(4단계)는 보류하되,
엔드포인트 목록(3단계)은 병행 가능하다.

---

## 4. 공통 규약 합의 항목 (§2단계 상세) — 팀 합의 필요

backend-infra-prep §2-1을 반영한 선결 항목. 각 항목은 **결정할 내용 + 고려사항**을 정리했으며,
`api-conventions.md`에 **제안안 + 결정 대기** 형태로 옮겨 팀 회의 안건으로 상정한다.

### 4-1. 응답 포맷

**결정할 내용**
- 공통 래퍼 사용 여부 (`{ code, message, data }` 등) vs 순수 `ResponseEntity` 바디
- 래퍼 채택 시 필드 구성: `success`(bool) / `code` / `message` / `data` / `timestamp` 중 무엇을 둘지
- 성공·실패를 **모두** 래핑할지, 실패만 별도 에러 포맷(§4-2)으로 갈지
- `data`가 없을 때(null vs 빈 객체), 리스트 응답을 어떻게 감쌀지(§4-4 페이징과 연계)

**고려사항**
- 컨벤션(03_api-convention.md)은 "우선 `ResponseEntity`"만 정함 → 래퍼 여부는 미결
- 래퍼는 FE 파싱 일관성↑ 이지만, HTTP 상태코드와 `code` 필드의 **의미 중복** 관리 필요
- springdoc 문서화 시 **제네릭 래퍼 스키마** 표현이 번거로움(예: `ApiResponse<LoginData>`)
- 실시간(WebSocket) 메시지 포맷과 통일할지 여부(통일 시 FE 처리 단순화)

### 4-2. 에러 규약

**결정할 내용**
- 에러 코드 체계: 도메인 prefix + 번호(예: `AUTH-401-01`) vs HTTP 상태코드만 사용
- 에러 응답 바디 필드: `code` / `message` / `errors[]`(필드 검증 오류) / `path` / `timestamp`
- 검증 실패(400) 시 **필드별 오류** 표현 형식
- 공통 예외 클래스 · `@RestControllerAdvice` 전역 처리 구조(코드 컨벤션 `exception` 패키지와 정합)

**고려사항**
- **사용자 노출 메시지 vs 개발용 상세 메시지** 분리 (보안상 내부 정보·스택트레이스 노출 금지)
- i18n(다국어 메시지) 필요 여부 — 필요 없으면 한글 고정으로 단순화
- FE가 `code`로 분기 처리할 수 있게 **코드 목록을 문서로 공유**(에러 카탈로그)

### 4-3. 인증 / 인가 표기

**결정할 내용**
- 토큰 전달 방식: 헤더 `Authorization: Bearer` / HttpOnly 쿠키 / 하이브리드 (infra §1-1 미결)
- 명세상 인증 필요 엔드포인트 표기 방법(Swagger `securityScheme` `bearerAuth` 정의)
- **401(미인증) vs 403(권한 없음)** 사용 기준
- 토큰 만료·갱신(AUTH-03) 흐름의 명세 표현, WebSocket 인증 방식

**고려사항**
- **토큰 발급 주체가 백엔드 파트** → 우리가 규약을 선제시해야 함(infra §2-1 #3)
- 시그널링·채팅·문서 **WebSocket 3채널이 이 결정에 종속** → FE·실시간 담당 합의 필수
- 역할(Owner/Member/Guest) × 계층(스페이스/회의/문서) 접근 제어를 명세에 어떻게 표기할지

### 4-4. 페이징 / 정렬 / 필터

**결정할 내용**
- 페이징 방식: offset 기반(`page`, `size`) vs cursor 기반
- 파라미터 이름·기본값·최대 `size` 상한
- 응답 메타: `totalElements` / `totalPages` / `hasNext` 등 무엇을 내려줄지
- 정렬 파라미터 포맷(예: `sort=createdAt,desc`)

**고려사항**
- Spring Data `Pageable`을 그대로 노출할지 vs 커스텀 규약으로 감쌀지
- 목록 API 다수(SPACE-02, MEET-02, CHAT-02 등)에 **일괄 적용**되므로 먼저 확정
- FE가 무한 스크롤을 쓰면 cursor 방식이 유리, 페이지 번호 UI면 offset이 단순
- 응답 래퍼(§4-1)와의 조합 형태(래퍼 `data` 안에 페이징 메타를 둘지)

### 4-5. URL / 리소스 네이밍

**결정할 내용**
- 경로 규칙: 복수 명사·소문자·하이픈, 중첩 리소스 표현(예: `/spaces/{id}/meetings`)
- HTTP 메서드 매핑(GET/POST/PUT·PATCH/DELETE) 원칙
- API 버전 prefix(`/api/v1`) 사용 여부
- 로그인·토큰갱신 같은 **행위형 엔드포인트** 표기 규칙

**고려사항**
- 컨벤션 메서드 네이밍(`find`/`add`/`modify`/`remove`)·DTO 네이밍(`RequestLoginDto`/`ResponsePostListDto`)과 정합
- 중첩 리소스 **깊이 제한**(너무 깊어지면 최상위 리소스로 분리)
- Nginx 경로 라우팅(`/api`, `/ws`, `/ai`, architecture §1)과 충돌 없이 정렬

### 4-6. 공통 데이터 표현 (추가 권장)

**결정할 내용**
- 날짜·시간 포맷(ISO 8601, UTC 저장·표기 여부)
- 식별자 타입(`Long` 자동증가 vs `UUID`) — 도메인별 상이 가능
- enum 직렬화(문자열 고정 권장), boolean·null 표현
- 공통 필드(`createdAt`/`updatedAt` 등) 노출 여부·이름

**고려사항**
- 두 DB 경계(MySQL↔AI PostgreSQL, infra §2-1 #5)에서 **ID 정합성**과 연결 → ID 타입 결정에 영향
- FE·AI 담당과 포맷을 통일해야 파싱 오류를 예방

---

## 5. REST 엔드포인트 인벤토리 대상 (§3단계)

기능명세 ID → 엔드포인트 매핑 표(method, path, 요약, 인증, 우선순위)로 작성. MVP 우선.

- **AUTH**: AUTH-01~06 (회원가입 / 로그인 / 토큰갱신 / 로그아웃 / 프로필 / 비번변경)
- **SPACE**: SPACE-01~03, 10, 15, 17, 18 등 MVP
- **MEMBER**: MEMBER-01~05
- **MEET**: MEET-01~07
- **DOC / CHAT / AI**: 조회·관리성 REST 엔드포인트만 (실시간 채널 제외)

---

## 6. 담당 분배

<!-- - 명세 **작성**은 전원 공동, 도메인별 1차 초안 작성자만 지정(리뷰는 교차).
- 백엔드/인프라 2인: AUTH / SPACE / MEMBER / MEET 초안 주도. DOC·CHAT·AI REST는 해당 담당과 공동.
- 공통 규약(§4)은 백엔드 파트가 초안 제시 → 전체 합의. -->

---

## 7. 리스크 / 선행 의존성

- **공통 규약 미확정 시 상세 스키마 진행 불가** → 팀 회의로 §4를 우선 확정.
- 인증 토큰 방식은 WebSocket 3채널에 영향 → FE·실시간 담당 합의 필요.
- WebSocket 규약은 OpenAPI로 담기지 않음 → 별도 문서, 이번 범위에서 분리.

---

## 8. 검증 방법

- 계획·규약·인벤토리 md: 기능명세 ID 누락 없이 매핑됐는지 교차 점검.
- `openapi.yaml`: Swagger Editor(editor.swagger.io) 또는 `npx @redocly/cli lint`로 문법 검증.
- 파일럿(AUTH) YAML로 mock 서버(`prism mock`) 실행 → FE 호출 가능 여부로 계약 유효성 확인.
- 각 단계는 컨벤션 커밋 형식(`docs: ... (#1)`)으로 커밋, `docs/1-api-spec` → develop MR.
