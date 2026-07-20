# REST API 명세 (작성 형식 + 예시)

이 문서는 도메인별 REST 엔드포인트 명세의 **작성 형식**을 정의하고, AUTH 도메인 2건을 **예시**로 보인다.
각 도메인 담당은 이 형식에 맞춰 `auth.md`, `space.md` 등에 엔드포인트를 채운다.

> ✅ **확정:** 아래 응답 래퍼·에러 포맷·인증 방식·명세 작성 형식은 팀에서 **제안안을 수정 없이 그대로 사용하기로 확정**했다.
> 단, 페이징 방식·식별자(ID) 타입 등 §0에 명시되지 않은 세부는 도메인 명세 작성 중 필요 시 팀이 보완한다.

---

## 0. 공통 규약 (확정)

- **Base URL**: `/api/v1`
- **인증**: 보호 엔드포인트는 `Authorization: Bearer <accessToken>` 헤더 필요
- **성공 응답 래퍼**
  ```json
  { "code": "SUCCESS", "message": "요청이 정상 처리되었습니다.", "data": { } }
  ```
- **에러 응답 래퍼**
  ```json
  { "code": "AUTH_EMAIL_DUPLICATED", "message": "이미 사용 중인 이메일입니다.", "errors": [] }
  ```
  - `errors[]`: 필드 검증 실패 시 `{ "field": "email", "reason": "형식이 올바르지 않습니다." }` 목록
- **날짜/시간**: ISO 8601 (UTC), 예 `2026-07-19T08:30:00Z`
- **DTO 네이밍**(코드 컨벤션): 요청 `Request+기능명+Dto`, 응답 `Response+기능명+Dto`

---

## 1. 엔드포인트 명세 작성 형식

각 엔드포인트는 아래 항목을 **모두** 기재한다. 값이 없으면 `없음`으로 표기한다.

| 항목 | 설명 |
| --- | --- |
| **기능 ID** | 기능명세서 ID (예: AUTH-01) |
| **메서드 · 경로** | `POST /api/v1/auth/signup` |
| **설명** | 엔드포인트가 하는 일 1~2줄 |
| **인증** | 필요 여부 / 필요 권한(역할) |
| **Path 파라미터** | 이름·타입·설명 (없으면 없음) |
| **Query 파라미터** | 이름·타입·필수여부·기본값·설명 |
| **Request Body** | 필드·타입·필수여부·제약(길이/형식) / DTO명 |
| **성공 응답** | 상태코드 + 예시 JSON / DTO명 |
| **에러 응답** | 상태코드 + `code` + 상황 |
| **비고** | 참고사항, 미확정 사항 |

---

## 2. 예시 ① — 회원가입

- **기능 ID**: AUTH-01
- **메서드 · 경로**: `POST /api/v1/auth/signup`
- **설명**: 이메일·비밀번호·닉네임으로 신규 계정을 생성한다.
- **인증**: 불필요
- **Path 파라미터**: 없음
- **Query 파라미터**: 없음
- **Request Body** — `RequestSignupDto`

  | 필드 | 타입 | 필수 | 제약 |
  | --- | --- | --- | --- |
  | `email` | string | ✅ | 이메일 형식, 최대 100자 |
  | `password` | string | ✅ | 8~20자, 영문+숫자 포함 |
  | `nickname` | string | ✅ | 2~20자 |

  ```json
  {
    "email": "user@example.com",
    "password": "pass1234",
    "nickname": "지니"
  }
  ```

- **성공 응답**: `201 Created` — `ResponseSignupDto`

  ```json
  {
    "code": "SUCCESS",
    "message": "회원가입이 완료되었습니다.",
    "data": {
      "userId": 1,
      "email": "user@example.com",
      "nickname": "지니",
      "createdAt": "2026-07-19T08:30:00Z"
    }
  }
  ```

- **에러 응답**

  | 상태코드 | `code` | 상황 |
  | --- | --- | --- |
  | `400 Bad Request` | `VALIDATION_FAILED` | 필드 검증 실패 (`errors[]`에 필드별 사유) |
  | `409 Conflict` | `AUTH_EMAIL_DUPLICATED` | 이미 가입된 이메일 |

  ```json
  {
    "code": "VALIDATION_FAILED",
    "message": "입력값이 올바르지 않습니다.",
    "errors": [
      { "field": "password", "reason": "8~20자여야 합니다." }
    ]
  }
  ```

- **비고**: 비밀번호는 BCrypt 해싱 후 저장(응답에 절대 포함하지 않음).

---

## 3. 예시 ② — 로그인

- **기능 ID**: AUTH-02
- **메서드 · 경로**: `POST /api/v1/auth/login`
- **설명**: 이메일·비밀번호를 검증하고 Access/Refresh Token을 발급한다.
- **인증**: 불필요
- **Path 파라미터**: 없음
- **Query 파라미터**: 없음
- **Request Body** — `RequestLoginDto`

  | 필드 | 타입 | 필수 | 제약 |
  | --- | --- | --- | --- |
  | `email` | string | ✅ | 이메일 형식 |
  | `password` | string | ✅ | 없음 |

  ```json
  {
    "email": "user@example.com",
    "password": "pass1234"
  }
  ```

- **성공 응답**: `200 OK` — `ResponseLoginDto`

  ```json
  {
    "code": "SUCCESS",
    "message": "로그인되었습니다.",
    "data": {
      "tokenType": "Bearer",
      "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
      "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
      "expiresIn": 1800
    }
  }
  ```

- **에러 응답**

  | 상태코드 | `code` | 상황 |
  | --- | --- | --- |
  | `400 Bad Request` | `VALIDATION_FAILED` | 필드 누락·형식 오류 |
  | `401 Unauthorized` | `AUTH_LOGIN_FAILED` | 이메일 없음 또는 비밀번호 불일치 |

  ```json
  {
    "code": "AUTH_LOGIN_FAILED",
    "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
    "errors": []
  }
  ```

- **비고**:
  - 보안상 "이메일 없음"과 "비밀번호 불일치"를 **구분하지 않고** 동일한 `AUTH_LOGIN_FAILED`로 응답한다.
  - **토큰 전달 방식**(확정): 로그인 응답 바디로 토큰을 발급하고, 이후 요청은 `Authorization: Bearer` 헤더로 전달한다.
  - `refreshToken`은 Redis에 저장(AUTH-04 로그아웃 시 폐기).
