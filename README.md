# CoMeetTool

팀 스페이스 안에서 화상회의를 하고, 회의 중에 문서를 함께 고치고, 회의가 끝나면 AI가 전사·요약·진행 피드백을 만들어 주는 회의 협업 도구입니다.

- **기간**: 2026.07.14 – 08.10 (27일)
- **팀**: 7명 · SSAFY 교육 과정 공통 프로젝트
- **상태**: 개발 종료, 운영하지 않음 (아래 [운영 상태](#운영-상태))

## 주요 기능

| 영역 | 내용 |
| --- | --- |
| 계정 | 회원가입·로그인(RS256 JWT, Access/Refresh Token), 최초 온보딩 |
| 팀 스페이스 | 생성·멤버 초대와 수락, OWNER·MEMBER·GUEST 권한, 팀 일정 캘린더 |
| 화상회의 | LiveKit 기반 영상·음성·채팅·화면 공유. 회의를 유지한 채 PiP로 문서 화면으로 이동 |
| 공유 문서 | Tiptap + Yjs(CRDT) + Hocuspocus 실시간 공동 편집, 사용자별 커서 표시 |
| 발화 녹음 | 브라우저에서 VAD로 발화 구간만 잘라 업로드 → S3 저장 |
| AI 산출물 | 회의 종료 후 비동기 처리. 회의 전문(STT) · AI 요약(검토·수정·최종 확정) · AI 피드백(참여도·의사결정·다음 회의 제안) |
| 내보내기 | 요약·피드백을 MD/PDF로 내보내기(Presigned URL) |

시연 흐름은 [exec/DEMONSTRATION_SCENARIO.md](exec/DEMONSTRATION_SCENARIO.md)에 화면 단위로 적혀 있습니다.

## 구성

```mermaid
flowchart LR
    FE["Frontend<br/>Vue 3 SPA"] -->|REST| BE["Backend<br/>Spring Boot"]
    FE -->|WebSocket| YJS["Yjs 협업 서버<br/>Hocuspocus"]
    FE -->|영상·음성| LK["LiveKit Cloud"]
    YJS -->|JWKS 검증| BE
    LK -->|Webhook| BE
    BE -->|VAD 녹음| S3[("S3")]
    BE -->|처리 요청| AI["AI 서버<br/>FastAPI"]
    AI -->|녹음 조회| S3
    BE --> DB[("PostgreSQL<br/>+ pgvector")]
    YJS --> DB
    AI --> DB
    BE --> VK[("Valkey<br/>Refresh Token")]
```

AI 서버는 faster-whisper로 전사한 뒤, 과거 회의록을 pgvector로 찾아 맥락에 넣고 LLM으로 회의록과 퍼실리테이터 보고서를 구조화해 저장합니다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Backend | Java 21 · Spring Boot 4.1 · Spring Data JPA · Spring Security · PostgreSQL 17 · Valkey(Redis) |
| 실시간 협업 | Node.js 22 · Hocuspocus 4 · Yjs |
| AI | Python 3.11 · FastAPI · faster-whisper · OpenAI 호환 API(SSAFY GMS) · pgvector |
| Frontend | Vue 3 · Vite · Tiptap · livekit-client · Vitest |
| 미디어 | LiveKit Cloud |
| 인프라 | AWS ECS Fargate · RDS · ElastiCache · S3 · CloudFront · GitLab CI/CD(OIDC) |

버전 상세는 [exec/PORTING_MANUAL.md §4](exec/PORTING_MANUAL.md#4-사용-기술과-버전)에 있습니다.

## 폴더 구조

```text
.
├── Main/
│   ├── Backend/        Spring Boot API 서버 (auth·space·schedule·meeting·document·report…) + Flyway SQL
│   ├── Backend-Yjs/    Hocuspocus/Yjs 협업 서버
│   ├── AI_BE/          FastAPI 전사·회의 분석 서버
│   ├── Frontend/       Vue/Vite SPA
│   └── Docs/
│       ├── 00_Convention/  Git·코드·API 컨벤션
│       ├── 01_Plan/        기능 명세·아키텍처·DB 스키마·구현 계획
│       ├── 02_Dailylog/
│       └── 03_API/         인증 계약·REST 명세
├── exec/               포팅 매뉴얼·외부 서비스 설정·시연 시나리오·DB 스키마 덤프
├── .github/workflows/  GitHub Actions(테스트 실행)
├── .gitlab/            CI 스크립트·이슈/MR 템플릿
└── .gitlab-ci.yml
```

## 실행 방법

서버 네 개(Backend · Yjs · AI · Frontend)와 PostgreSQL(pgvector)·Valkey가 필요합니다. PostgreSQL과 Redis는 저장소 루트의 [compose.yaml](compose.yaml)로 띄울 수 있습니다(애플리케이션 서버는 포함하지 않습니다).

백엔드를 로컬에서 띄우는 최소 순서입니다.

```bash
docker compose up -d                          # PostgreSQL 17(pgvector) + Redis, 첫 실행 때 스키마 자동 적용
cp Main/Backend/.env.example Main/Backend/.env
cd Main/Backend && ./gradlew check            # 단위 + 통합 테스트 (JWT 키는 테스트용으로 자동 생성)
```

서버를 실제로 띄우려면(`./gradlew bootRun`) JWT 키 쌍을 직접 만들어야 합니다([exec/PORTING_MANUAL.md](exec/PORTING_MANUAL.md) §7).

| 문서 | 내용 |
| --- | --- |
| [exec/PORTING_MANUAL.md](exec/PORTING_MANUAL.md) | 로컬 통합 실행 순서(§12), 서비스별 환경변수, Docker 이미지 빌드, 배포 절차 |
| [exec/EXTERNAL_SERVICES.md](exec/EXTERNAL_SERVICES.md) | AWS·LiveKit·SSAFY GMS 등 외부 서비스 계정과 키 준비 |
| [exec/database/README.md](exec/database/README.md) | 스키마 덤프로 로컬 DB 만들기 |

각 서버의 `.env.example`을 `.env`로 복사해 값을 채웁니다. 기본 포트는 Frontend 5173 · Backend 8080 · Yjs 3000 · AI 8000입니다.

매뉴얼은 GitLab에서 쓰던 그대로라 몇 가지를 바꿔 읽어야 합니다.

- 클론 주소와 폴더 이름은 `https://github.com/DreamingGenie/Comeettool.git` · `Comeettool`로 바꿉니다.
- AI 분석의 기본 엔드포인트는 SSAFY GMS입니다. 교육 과정 밖에서 AI 산출물까지 재현하려면 `Main/AI_BE/.env`의 `OPENAI_BASE_URL`에 다른 OpenAI 호환 엔드포인트를 지정합니다.
- 배포 파이프라인(`.gitlab-ci.yml`)은 GitLab Runner와 AWS 계정을 전제로 하며 GitHub에서는 동작하지 않습니다. GitHub에서는 테스트만 돌립니다(아래 [테스트](#테스트)).

## 테스트

| 모듈 | 명령 | 필요한 것 |
| --- | --- | --- |
| Backend | `./gradlew check` | PostgreSQL(pgvector) · Redis — 실제 DB에 붙는 통합 테스트가 있습니다. DB 없이 도는 단위 테스트만은 `./gradlew test` |
| Frontend | `npm test` | 없음 |
| AI 서버 | `python -m pytest -q pytest2.py` | 없음 |

PR과 `develop`·`main` push마다 GitHub Actions([.github/workflows/ci.yml](.github/workflows/ci.yml))가 세 모듈의 테스트를 돌립니다.

## 내 역할 — 전진 (DreamingGenie)

팀 합의로 PM 겸 통합 담당을 맡아 MR 리뷰와 develop 통합을 담당했습니다.

- develop에 합친 머지 95건 중 83건을 처리했습니다. 제 브랜치 9건은 팀원이 머지했습니다.
- main 머지 10건은 모두 제가 했고, 그중 배포가 9건입니다.
- 기본 브랜치를 main에서 develop으로 바꾸고, 팀이 합의한 규칙을 컨벤션 문서로 정리했습니다([Main/Docs/00_Convention](Main/Docs/00_Convention)).
- 공통 응답·예외 처리 틀(`f66a04f`)을 만들었고, 팀원 4명이 각자 기능의 에러 코드를 여기에 더해 썼습니다.
- 팀 스페이스·일정 백엔드 API 13개와 그 테스트를 만들었습니다. 두 기능의 API 중 대표 이미지 변경 1개는 팀원이 만들었고, 저는 거기에 검증과 테스트를 보탰습니다.

화상회의, 실시간 문서 편집, VAD, AI 분석, CI/CD는 팀원들이 만들었습니다. 저는 그 코드를 리뷰하고 통합했고, 구현에 깊이 관여하지는 않았습니다.

> 머지 건수는 프로젝트 마지막 배포 커밋 `f17f3f4`(2026-08-10)까지 `main`에 반영된 `into 'develop'` 머지 커밋 기준입니다.

## 프로젝트 종료 뒤 고친 것

2026-08-10에 프로젝트가 끝난 뒤, 저장소를 옮긴 제가 혼자 점검하며 고친 내용입니다. 팀 프로젝트 기간의 작업과 구분하려고 따로 적습니다.

- 로컬 `DATABASE_URL`로 AI 서버가 뜨지 않던 문제 (#1)
- 컨벤션·JWT 규약 문서를 실제 구현에 맞춤 (#3)
- 테스트를 단위·통합 두 층으로 나누고, 팀 스페이스 잠금을 실제 DB로 확인하는 테스트 추가. 초대를 동시에 수락하면 정원 10명을 넘길 수 있던 경합 수정 (#4)
- 로컬(FILE 모드)에서 Object Storage가 동작하지 않던 것을 디스크 저장으로 구현 (#5)
- Jackson 2·3 혼용을 Jackson 3 하나로 정리. 응답 날짜가 규약대로 UTC로 나가게 함 (#6)
- AI 피드백의 빈 값이 화면에 잘못 표시되던 문제 (#7)
- GitHub Actions로 테스트 실행 (#8)
- 저장소를 받아 바로 테스트할 수 있게 정리: Docker Compose, 테스트용 JWT 키 자동 생성, AI 엔드포인트 환경변수 (#14)

## 저장소 이력

- SSAFY GitLab에서 개발했고, 2026-10-06 GitHub로 옮겨 공개했습니다.
- 공개하면서 서비스와 관계없는 교육 과정 개인 실습 폴더를 이력에서 지웠습니다. 그래서 커밋 해시가 GitLab 원본과 다릅니다.
- 커밋 메시지와 문서의 `S15P11A707-123` 같은 키는 Jira 이슈, `!83` 같은 번호는 GitLab MR입니다. 둘 다 교육 과정 계정으로만 열리는 곳이라 링크가 열리지 않습니다.
- GitHub에는 `main`(배포)과 `develop`(통합) 두 브랜치만 있습니다.

## 운영 상태

교육 과정이 끝나 서비스를 운영하지 않습니다. 2026-10-18 확인 기준 API 도메인이 응답하지 않아, 배포 주소로는 기능을 쓸 수 없습니다.
