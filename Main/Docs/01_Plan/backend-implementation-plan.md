# 백엔드 구현 계획 — AUTH · SPACE · MEMBER

기획 문서(아키텍처·ERD·기능명세·API 인벤토리)를 기반으로 백엔드를 먼저 구현·검증한 뒤 FE와
연결한다. AUTH·팀 스페이스(SPACE)·멤버(MEMBER) 도메인의 구현 순서, GitLab 이슈, 마일스톤을 정의한다.

- **계약 기준**: [inventory.md](../03_API/rest/inventory.md)(엔드포인트), [README.md](../03_API/rest/README.md) §0(공통 규약), [database-schema.md](database-schema.md)(DDL)
- **스택**: Spring Boot 4.1.0 / Java 21 / Gradle / PostgreSQL / Spring Data JPA / Spring Security / JWT / Redis
  - (당초 계획은 Boot 3.x였으나, 현재 Spring Initializr가 3.x 미지원(>=4.0.0)이라 4.x 채택)
- **패키지**: `com.ssafy.backend.{도메인}.{controller,domain,dto,exception,mapper,service}` ([02_code-convention](../00_Convention/02_code-convention.md))
- **현황**: `Main/Backend`는 비어 있는 그린필드 → 스캐폴딩부터 시작

---

## 1. 마일스톤 (기능 단계별, 의존성 순서)

| 마일스톤 | 범위 | 선행 | 기간(예상) |
| --- | --- | --- | --- |
| **M1 · 공통 기반** | 스캐폴딩·DB·보안·공통 응답 | - | 7/22(수)~7/24(금), 3일 |
| **M2 · 인증 (AUTH)** | 회원/로그인/토큰/프로필/검색 | M1 | 7/25(토)~7/28(화), 4일 |
| **M3 · 팀 스페이스·멤버 (SPACE/MEMBER)** | 스페이스 CRUD·초대·권한 | M1, (일부) M2 | 7/29(수)~8/4(화), 6일 |

각 마일스톤 완료 = 해당 엔드포인트가 inventory·README §0 규약대로 동작함을 검증한 시점.

> **일정 전제.** 시작 **7/22**, 솔로 **하루 6h**, 가능한 빨리 완료 목표. 위는 **연속 작업(주말 포함) 가정
> 약 13일** 추정치다. 목표 완료 **8/4 전후**. 추정엔 불확실성이 있으니 **버퍼(±2~3일)** 를 두고,
> M1 인증 기반(F4)·M3 권한 모듈(P1)에서 지연 가능성이 크다. 주말을 쉬면 그만큼 뒤로 밀린다.

---

## 2. 착수 전 해소할 결정 / 리스크

| # | 항목 | 결정 시점 |
| --- | --- | --- |
| 1 | **초대 상태 = Redis 관리(확정)** — 초대·수락·거절의 대기 상태를 별도 DB 테이블 없이 Redis에 저장(TTL). 수락 시 members 행 생성, 거절/만료 시 Redis에서 제거. "참여 대기" 목록(SPACE-02)도 Redis에서 조회 | ✅ 확정 |
| 2 | JWT 라이브러리 (jjwt 유력) | F4 |
| 3 | **Flyway 미사용(확정)** — 개발 단계는 개인 로컬 DB, 스키마는 DDL 직접 적용 + `ddl-auto: validate`. 배포(서버 공용 DB) 시점에 마이그레이션 도구 도입 검토 | 배포 시 |
| 4 | 권한 매핑 — `members.authority`(Owner/Member/Guest?) ↔ `teams.team_owner_id`(Owner) | P1 |
| 5 | 용어 매핑 — ERD(teams/members) ↔ 명세(SPACE/MEMBER). 도메인 패키지·클래스명 기준 확정 | F1 |

> **FE 연동:** 백엔드 우선. 각 기능 이슈는 "구현+단위검증"까지만 하고, **FE 연결은 별도 후속 이슈**로
> 분리한다(FE 화면 확정 후). 구현 코드엔 springdoc를 달아 살아있는 문서를 제공한다.

---

## 3. 이슈 목록 (기반 + 기능묶음)

브랜치 `feature/{이슈번호}-{내용}`, 커밋 `feat: ... (#N)`, develop로 MR.

### M1 공통 기반
| 이슈 | 범위 | 매핑 |
| --- | --- | --- |
| **F1** 프로젝트 스캐폴딩 & 로컬 환경 | Spring Boot/Gradle, 패키지 구조, `application.yml`(local/prod), Docker Compose(PostgreSQL·Redis) | - |
| **F2** DB 마이그레이션 + DDL 적용 | Flyway 도입, DDL 마이그레이션화, `ddl-auto: validate` | database-schema |
| **F3** 공통 응답·예외 처리 | `ApiResponse` 래퍼, 에러 포맷, `@RestControllerAdvice`, 에러코드 enum | README §0 |
| **F4** 인증 기반(Security/JWT) | SecurityConfig, JWT 발급·검증 필터, BCrypt, Redis Refresh 저장 | README §0 |

### M2 인증 (AUTH)
| 이슈 | 범위 | 매핑 |
| --- | --- | --- |
| **A1** 회원가입 | 이메일/닉네임 중복, BCrypt, users insert | AUTH-01 |
| **A2** 로그인·로그아웃·토큰 갱신 | JWT 발급, Redis refresh 저장·폐기 | AUTH-02/03/04 |
| **A3** 프로필 조회·수정 | `GET/PATCH /users/me` | AUTH-05/06 |
| **A4** 비밀번호 변경·회원 탈퇴 | 본인확인, soft delete | AUTH-07/08 |
| **A5** 사용자 검색 | `GET /users?query=` | AUTH-10 |
| (보류) 비밀번호 재설정 | 이메일 인프라 후순위 | AUTH-09 `SUB` |

### M3 팀 스페이스·멤버 (SPACE/MEMBER)
| 이슈 | 범위 | 매핑 |
| --- | --- | --- |
| **P1** 권한 검사 공통 모듈 | Owner/Member/Guest × 스페이스 접근 검사(선언적) | NFR 권한 |
| **S1** 스페이스 생성·목록·상세 | teams, members(Owner 생성) | SPACE-01/02/05 |
| **S2** 스페이스 나가기·삭제 | soft delete, Owner 권한 | SPACE-07/11 |
| **S3** 초대 링크/코드 공유 | teams.team_invite_link | SPACE-09 |
| **B1** 멤버 조회 | 멤버·권한 목록 | MEMBER-01 |
| **B2** 멤버 초대·수락·거절 | ⚠️ 결정 #1 선행 | MEMBER-02/03/04 |
| **B3** 멤버 강퇴·권한 변경 | Owner | MEMBER-05/06 |
| **B4** 스페이스 프로필 | members.role(직무)·nickname | MEMBER-07 |
| (MVP 이후) 검색·정렬·정보수정·접근제한·채팅 | `Extra`/`SUB` | SPACE-03/04/08/10/12 |

---

## 4. 이슈 드래프트 (GitLab 기능 템플릿 — 붙여넣기용)

> 아래를 GitLab New issue → 기능 템플릿에 붙여넣는다. 제목 형식: `[Feature] {이슈명}`.
> 대표 이슈만 전문 수록하고, 나머지는 동일 형식으로 확장한다.

### F1 · 프로젝트 스캐폴딩 & 로컬 환경
```markdown
## 작업 설명
Spring Boot 백엔드 프로젝트를 초기화하고 팀 컨벤션 패키지 구조와 로컬 개발 환경을 구성한다.

## 작업 목적
모든 도메인 구현의 공통 토대를 마련한다.

## 작업 상세 내용
- [ ] Spring Boot 4.1.0 / Java 21 / Gradle 프로젝트 생성 (Main/Backend)
- [ ] 패키지 구조 `com.ssafy.backend.{도메인}...` 세팅
- [ ] application.yml local/prod 분리, 시크릿 외부화(.env / CI 변수)
- [ ] 로컬 PostgreSQL 설치·구성(port 5432, DB·계정 생성) — Docker는 추후 전환
- [ ] 로컬 Redis 실행(Refresh Token·초대 관리용)
- [ ] 의존성: web, data-jpa, security, validation, redis, postgresql, lombok, springdoc

## 참고 사항
- 컨벤션: Main/Docs/00_Convention/02_code-convention.md
- 스택 근거: Main/Docs/01_Plan/architecture.md
```

### F2 · DB 스키마 구성 (로컬)
```markdown
## 작업 설명
로컬 PostgreSQL에 ERD DDL을 적용해 스키마를 구성하고, JPA가 이를 검증만 하도록 설정한다.
(Flyway 미사용 — 배포 시점에 도입 검토)

## 작업 목적
ERD(database-schema.md)를 스키마의 단일 원천으로 유지하며 로컬 개발 DB를 준비한다.

## 작업 상세 내용
- [ ] database-schema.md DDL을 로컬 PostgreSQL에 실행(1회) — PK 자동생성(IDENTITY/시퀀스) 보완 포함
- [ ] ddl-auto: validate 설정(엔티티↔스키마 검증)
- [ ] 앱 부팅 시 엔티티 매핑이 스키마와 일치하는지 검증

## 참고 사항
- DDL: Main/Docs/01_Plan/database-schema.md (PostgreSQL)
- 스키마 변경 시 DDL 갱신 후 재적용. 배포(공용 DB) 시 Flyway 등 마이그레이션 도구 도입 검토
- F1 선행
```

### F3 · 공통 응답·예외 처리
```markdown
## 작업 설명
공통 응답 래퍼와 전역 예외 처리를 구현한다.

## 작업 목적
모든 API가 README §0 규약(성공/에러 포맷)을 일관되게 따르도록 한다.

## 작업 상세 내용
- [ ] ApiResponse<T> 래퍼({code, message, data})
- [ ] 에러 응답 포맷({code, message, errors[]})
- [ ] @RestControllerAdvice 전역 예외 처리(검증 실패 400 필드 오류 포함)
- [ ] 에러코드 enum(에러 카탈로그)
- [ ] 커스텀 예외 계층(도메인 exception 패키지 정합)

## 참고 사항
- 규약: Main/Docs/03_API/rest/README.md §0
```

### F4 · 인증 기반 (Security/JWT)
```markdown
## 작업 설명
Spring Security와 JWT 기반 인증 토대를 구현한다(발급·검증 필터, Redis Refresh 저장).

## 작업 목적
AUTH 이하 모든 보호 엔드포인트의 인증·인가 토대를 제공한다.

## 작업 상세 내용
- [ ] SecurityConfig(무상태, 경로별 인가 규칙)
- [ ] JWT 발급·검증 필터, Access/Refresh 정책(만료·회전)
- [ ] BCrypt PasswordEncoder
- [ ] Redis에 Refresh Token 저장·폐기
- [ ] Authorization: Bearer 규약, 401/403 처리

## 참고 사항
- 규약: Main/Docs/03_API/rest/README.md §0
- JWT 라이브러리 확정 필요(jjwt 유력)
```

### A1 · 회원가입 (AUTH-01)
```markdown
## 작업 설명
이메일·비밀번호·닉네임으로 계정을 생성하는 회원가입 API를 구현한다.

## 작업 목적
서비스 이용의 진입점인 계정 생성 기능을 제공한다.

## 작업 상세 내용
- [ ] POST /api/v1/auth/signup (RequestSignupDto/ResponseSignupDto)
- [ ] 이메일·닉네임 중복 검사(409)
- [ ] 비밀번호 BCrypt 해싱 후 저장
- [ ] users 저장(가입 시 수집 필드 범위는 팀 확정 반영)
- [ ] 단위/통합 테스트

## 참고 사항
- 계약: Main/Docs/03_API/rest/inventory.md (AUTH-01), README 예시
- ERD: database-schema.md (users)
```

### A2 · 로그인·로그아웃·토큰 갱신 (AUTH-02/03/04)
```markdown
## 작업 설명
로그인 시 토큰을 발급하고, 토큰 갱신·로그아웃(Refresh 폐기)을 구현한다.

## 작업 목적
인증 세션의 발급·유지·종료 흐름을 완성한다.

## 작업 상세 내용
- [ ] POST /auth/login — 검증 후 Access/Refresh 발급(실패 사유 미구분 401)
- [ ] POST /auth/token/refresh — Redis 대조 후 Access 재발급
- [ ] POST /auth/logout — Refresh 폐기(멱등)
- [ ] 단위/통합 테스트

## 참고 사항
- 계약: inventory (AUTH-02/03/04)
- F4(인증 기반) 선행
```

### P1 · 권한 검사 공통 모듈
```markdown
## 작업 설명
스페이스 단위 Owner/Member/Guest 권한 검사를 선언적으로 재사용할 공통 모듈을 구현한다.

## 작업 목적
SPACE·MEMBER·이후 회의/문서 도메인이 공유할 권한 검사를 표준화해 누락·중복을 방지한다.

## 작업 상세 내용
- [ ] 권한 매핑 확정(members.authority ↔ teams.team_owner_id)
- [ ] 스페이스 멤버십·역할 조회 유틸/서비스
- [ ] 선언적 검사(@PreAuthorize + PermissionEvaluator 또는 AOP) 중 택1
- [ ] 401/403 일관 처리(F3 에러 규약 연동)

## 참고 사항
- NFR: 스페이스·회의·문서 단위 접근 검사
- 다른 도메인 담당도 사용 → M3 초반 우선
```

### B2 · 멤버 초대·수락·거절 (MEMBER-02/03/04)
```markdown
## 작업 설명
멤버 초대와 초대 수락·거절 흐름을 구현한다. 대기 상태는 Redis로 관리한다(별도 DB 테이블 없음).

## 작업 목적
팀 스페이스에 멤버를 추가하는 핵심 협업 기능을 제공한다.

## 작업 상세 내용
- [ ] Redis 초대 데이터 구조 설계 — 초대 키(spaceId·대상user·초대자), TTL(만료), 사용자별 대기 목록
- [ ] POST /spaces/{spaceId}/invitations — 초대 생성(Redis 저장), 알림(alarms) 연동 검토
- [ ] POST /invitations/{id}/accept — members 행 생성 + Redis에서 제거
- [ ] POST /invitations/{id}/reject — Redis에서 제거
- [ ] SPACE-02 "참여 대기 목록" 조회가 Redis 대기 목록과 연동되는지 확인
- [ ] 단위/통합 테스트(수락→멤버십 생성, 만료 TTL)

## 참고 사항
- 초대 상태는 Redis 관리(확정, §2 #1). members 테이블은 수락 후 실제 멤버십만 저장
```

> **나머지 이슈**(F2·F3·A3·A4·A5·S1·S2·S3·B1·B3·B4)도 위와 동일한
> `작업 설명 / 목적 / 상세 체크리스트 / 참고` 4단 구조로 작성한다. 각 상세는 §3 매핑과
> inventory의 엔드포인트를 기준으로 채운다.

---

## 5. GitLab 마일스톤 운영 가이드

> **역할 분리(중복 방지).** 마일스톤 description = **목표·완료 기준(DoD)·기간**만 적는다. 이슈 상세·
> 기능명세 매핑은 **각 이슈에만** 둔다. 마일스톤에 배정된 이슈 목록은 GitLab이 자동 표시하므로
> 마일스톤에 이슈 목록·매핑을 다시 나열하지 않는다.

1. **마일스톤 생성** — Plan → Milestones → New: `M1 공통 기반`, `M2 인증`, `M3 팀 스페이스·멤버`. 시작/종료일은 의존성 순서대로.
2. **이슈 연결** — 각 이슈에 마일스톤 지정 → 마일스톤 페이지에서 진척도(%)로 단계 완료 추적.
3. **보드(선택)** — Plan → Boards를 상태 라벨(todo/in-progress/review/done)로 칸반 구성.
4. **작업 흐름** — 이슈 → `feature/{번호}-{내용}` 브랜치 → `feat: ... (#N)` 커밋 → develop MR(리뷰어·라벨, 본인 승인 금지) → 머지 후 브랜치 삭제.
5. **시간 추적(선택)** — 이슈 코멘트에 `/estimate`, `/spend`로 예상·실적 기록.

---

## 6. 테스트 전략 (계획)

목적: 팀원 머지로 기능이 깨지는 회귀를 **조기에 발견**한다.

- **계층**
  - 🔴 통합(API) — `@SpringBootTest` + MockMvc로 엔드포인트 호출, 상태코드·응답이 계약(inventory·README §0)과 일치하는지. **주력**(먹통 탐지 최적).
  - 🟡 단위 — 서비스 엣지 케이스(중복 이메일, 비번 불일치 등).
  - 🟢 슬라이스(`@DataJpaTest`/`@WebMvcTest`) — 선택.
- **⚠️ 테스트 DB** — 스키마가 PostgreSQL 전용 타입(`JSONB`/`INTEGER[]`/`UUID`)을 써서 **H2 부적합**. 실제 PostgreSQL로 테스트한다. 지금은 **로컬 테스트 전용 DB**, Docker 도입 후 **Testcontainers**로 전환(CI에서도 동일).
- **원칙** — "기능 = 구현 + 테스트". 각 기능의 **인수 시나리오**(given/when/then)를 이슈 완료 조건에 적고, 구현 시 통합/단위 테스트로 실현.
- **⏸️ CI 자동 실행(보류)** — GitLab Runner로 MR마다 `./gradlew test` 자동 실행·실패 시 머지 차단하는 방식은 **팀 결정 사항**. **M1 완료 후 결정**한다(현재 보류, 계획으로만 기록).

## 7. 검증 방법

- **M1(F1~F4)**: 앱 부팅·DB 연결·Docker Compose 기동, JWT 발급/검증 단위 확인, 보호 경로 401 확인.
- **각 기능 이슈**: 서버 기동 후 **Swagger UI(springdoc) 또는 curl**로 엔드포인트 호출 → 성공/에러 응답이 README §0 규약과 일치하는지 확인. 핵심 서비스 로직은 단위·통합 테스트.
- **마일스톤 종료**: 해당 도메인의 inventory 엔드포인트가 모두 명세대로 동작하는지 점검.
- **컨벤션 점검**: 커밋/브랜치 규칙, DTO·메서드 네이밍(find/add/modify/remove), 패키지 구조.
