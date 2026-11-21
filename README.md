# CoMeetTool

음성 회의를 기록·요약하고, 팀별 공간(스페이스)과 일정·문서를 함께 관리하는 협업 도구입니다.

- 기간: 2026-07-14 ~ 08-10 (27일), 7명 팀 프로젝트
- 교육기관 GitLab에서 개발한 뒤 이 저장소로 옮겼습니다. 옮기면서 개인 실습 폴더를 이력에서 빼, 커밋 해시는 원본 저장소와 다릅니다.
- 프로젝트가 끝난 뒤 고친 것은 [아래](#프로젝트-종료-뒤-고친-것)에 따로 적었습니다.

## 구성

```text
Main/
├── Backend/       Spring Boot 4.1 · Java 21 API 서버 (Flyway SQL 포함)
├── Backend-Yjs/   Hocuspocus · Yjs 문서 협업 서버 (Node 22)
├── AI_BE/         FastAPI STT·회의 분석 서버 (Python 3.11)
├── Frontend/      Vue 3 · Vite SPA
└── Docs/          컨벤션 · 설계 문서 · API 명세
exec/              포팅 매뉴얼 · 시연 시나리오 · 스키마 덤프
```

| 서비스 | 하는 일 | 기본 포트 |
| --- | --- | ---: |
| Backend | 인증(JWT RS256), 팀 스페이스·멤버·일정·문서·회의·리포트 API | 8080 |
| Backend-Yjs | 문서 실시간 공동 편집. Backend가 발급한 문서 전용 토큰을 JWKS로 검증 | 3000 |
| AI_BE | 회의 음성 전사, 회의록·피드백 생성 | 8000 |
| Frontend | 화면 | 5173 |

저장소는 PostgreSQL 17(pgvector)과 Redis(리프레시 토큰)를 씁니다. 화상 회의는 LiveKit, 파일은 S3를 씁니다.
운영 배포는 AWS ECS Fargate였고 파이프라인은 [`.gitlab-ci.yml`](.gitlab-ci.yml)에 남아 있습니다.

## 실행

전체 절차는 [`exec/PORTING_MANUAL.md`](exec/PORTING_MANUAL.md)에 있습니다. 로컬에서 백엔드만 띄우는 최소 순서는 다음과 같습니다.

1. PostgreSQL 17(pgvector)과 Redis를 띄웁니다.
2. 스키마를 넣습니다. [`exec/database/comeettool_dump.sql`](exec/database/comeettool_dump.sql)은 Flyway V1~V4를 적용한 빈 스키마입니다.
3. `Main/Backend/.env.example`을 `.env`로 복사해 DB 접속 정보를 채우고, JWT 키 쌍을 `Main/Backend/src/main/resources/keys/`에 만듭니다(매뉴얼 7절).
4. `Main/Backend`에서 `./gradlew bootRun`을 실행합니다.

## 테스트

| 모듈 | 명령 | 필요한 것 |
| --- | --- | --- |
| Backend | `./gradlew check` | PostgreSQL · Redis (실제 DB에 붙는 테스트가 있습니다) |
| Frontend | `npm test` | 없음 |
| AI_BE | `python -m pytest -q pytest2.py` | 없음 |

PR과 `develop`·`main` push마다 GitHub Actions([`.github/workflows/ci.yml`](.github/workflows/ci.yml))가 세 모듈의 테스트를 돌립니다.

## 문서

- [`Main/Docs/00_Convention/`](Main/Docs/00_Convention/) — 브랜치·커밋·코드·API 컨벤션
- [`Main/Docs/01_Plan/`](Main/Docs/01_Plan/) — 기능 명세, 아키텍처, DB 스키마, 구현 계획
- [`Main/Docs/03_API/`](Main/Docs/03_API/) — REST 명세, JWT 인증 규약
- [`exec/`](exec/) — 포팅 매뉴얼, 외부 서비스, 시연 시나리오

## 프로젝트 종료 뒤 고친 것

2026-08-10에 프로젝트가 끝난 뒤, 저장소를 옮긴 사람(DreamingGenie)이 혼자 점검하며 고친 내용입니다. 팀 프로젝트 기간의 작업과 구분하려고 따로 적습니다.

- 로컬 `DATABASE_URL`로 AI 서버가 뜨지 않던 문제 (#1)
- 컨벤션·JWT 규약 문서를 실제 구현에 맞춤 (#3)
- 테스트를 단위·통합 두 층으로 나누고, 팀 스페이스 잠금을 실제 DB로 확인하는 테스트 추가. 초대를 동시에 수락하면 정원 10명을 넘길 수 있던 경합 수정 (#4)
- 로컬(FILE 모드)에서 Object Storage가 동작하지 않던 것을 디스크 저장으로 구현 (#5)
- Jackson 2·3 혼용을 Jackson 3 하나로 정리. 응답 날짜가 규약대로 UTC로 나가게 함 (#6)
- AI 피드백의 빈 값이 화면에 잘못 표시되던 문제 (#7)
- GitHub Actions로 테스트 실행 (#8)
