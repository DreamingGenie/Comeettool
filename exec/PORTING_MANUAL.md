# CoMeetTool 포팅 및 배포 매뉴얼

## 1. 문서 목적

이 문서는 저장소를 처음 클론한 환경에서 CoMeetTool의 Backend, Yjs 협업 서버, AI 서버, Frontend를 빌드하고 실행하거나 기존 AWS 운영 환경에 배포하는 데 필요한 절차와 설정 계약을 설명한다.

- 기준 저장소: `https://lab.ssafy.com/s15-webmobile1-sub1/S15P11A707.git`
- 기준 브랜치: 배포는 보호된 `main` 브랜치, 개발 통합은 `develop` 브랜치
- 프로젝트 루트: `S15P11A707`
- 애플리케이션 소스 루트: `S15P11A707/Main`
- 운영 리전: `ap-northeast-2`
- 운영 데이터베이스 이름: `comeettool`

> 이 문서에 적힌 `<...>` 값은 사용자가 직접 발급하거나 환경에 맞게 입력해야 한다. 비밀번호, 개인키, API 키, AWS 계정 번호, Secret ARN은 저장소와 이 문서에 기록하지 않는다.

## 2. 저장소 클론

```bash
git clone https://lab.ssafy.com/s15-webmobile1-sub1/S15P11A707.git
cd S15P11A707
```

개발 브랜치를 사용할 때는 다음과 같이 전환한다.

```bash
git switch develop
```

운영 배포 대상은 보호된 `main` 브랜치다. 로컬에서 `main`을 직접 배포하는 대신 GitLab CI/CD 파이프라인을 사용한다.

## 3. 프로젝트 구조

```text
S15P11A707/
├── .gitlab-ci.yml
├── .gitlab/ci/scripts/
│   ├── apply-migrations.sh
│   ├── aws-oidc-login.sh
│   ├── package-container-images.sh
│   ├── production-release.sh
│   └── validate-production-inputs.sh
├── exec/
├── Main/
│   ├── Backend/          Spring Boot API 서버 및 Flyway SQL
│   ├── Backend-Yjs/      Hocuspocus/Yjs 협업 서버
│   ├── AI_BE/            FastAPI 기반 STT·회의 분석 서버
│   ├── Frontend/         Vue/Vite SPA
│   └── Docs/
└── out/                  IDE 빌드 산출물; 배포 소스로 사용하지 않음
```

## 4. 사용 기술과 버전

### 4.1 개발 도구 및 런타임

| 구분 | 제품/기술 | 버전 또는 기준 |
| --- | --- | --- |
| IDE | IntelliJ IDEA | 2026.1.4 |
| Project SDK | Java SDK | 21 |
| 로컬 JVM 확인 버전 | Microsoft OpenJDK | 21.0.11 LTS |
| Backend 빌드 JVM | Eclipse Temurin JDK | 21, Jammy 이미지 |
| Backend 실행 JVM | Eclipse Temurin JRE | 21, Noble 이미지 |
| Gradle Wrapper | Gradle | 9.5.1 |
| Backend Framework | Spring Boot | 4.1.0 |
| Web/WAS | Spring MVC + Spring Boot 내장 Tomcat | Spring Boot 의존성 관리 버전 사용 |
| Frontend Runtime | Node.js | CI 빌드 이미지 기준 22 Alpine |
| npm | npm CLI | CI 기준 10.9.4 |
| Frontend Framework | Vue | 3.2.47 |
| Frontend Build Tool | Vite | 2.9.16 |
| Frontend Test | Vitest | 4.1.10 |
| Yjs Runtime | Node.js | 22 Alpine |
| 협업 서버 | Hocuspocus Server | 4.4.x |
| CRDT | Yjs | 13.6.x |
| AI Runtime | Python | 3.11 slim-bookworm |
| AI API/WAS | FastAPI + Uvicorn | FastAPI 0.115+, Uvicorn 0.32+ |
| STT | faster-whisper | 1.1.0 |
| 운영 DB | Amazon RDS for PostgreSQL | 17 |
| CI DB | PostgreSQL + pgvector | PostgreSQL 17, pgvector 0.8.2 |
| 벡터 확장 | pgvector | `vector(1536)`, HNSW 인덱스 사용 |
| DB 마이그레이션 | Flyway | 12.11.0 |
| 캐시/토큰 저장소 | AWS Valkey, Redis 프로토콜 | 운영 엔진 세부 버전은 AWS 콘솔 기준 |
| 컨테이너 빌드 | Buildah | 1.42.2 |
| AWS CLI | AWS CLI v2 | CI 이미지 2.27.49 |
| Secret 검사 | Gitleaks | 8.28.0 |
| 취약점 검사 | Trivy | 0.69.3 |

내장 Tomcat의 세부 패치 버전은 `build.gradle`에 직접 고정하지 않고 Spring Boot 4.1.0의 의존성 관리 결과를 따른다.

### 4.2 서비스 포트

| 서비스 | 기본 포트 | 확인 경로/용도 |
| --- | ---: | --- |
| Frontend 개발 서버 | 5173 | Vite 개발 서버 |
| Backend API | 8080 | `/actuator/health`, `/.well-known/jwks.json` |
| Yjs/Hocuspocus | 3000 | `/health`, `/collaboration` WebSocket |
| AI/FastAPI | 8000 | `/health` |
| PostgreSQL | 5432 | 공용 `comeettool` DB |
| Valkey/Redis | 6379 | Refresh Token 저장 |

운영에서는 Frontend를 S3와 CloudFront로 제공하며 별도의 Frontend 웹 서버 컨테이너를 실행하지 않는다.

## 5. 공통 사전 준비

다음 프로그램이 필요하다.

- Git
- Java 21
- Docker Desktop 또는 호환 컨테이너 런타임
- Node.js 22 및 npm 10.9.4 권장
- Python 3.11
- OpenSSL
- PostgreSQL 클라이언트(`psql`, `pg_isready`)
- 로컬 PostgreSQL과 pgvector 확장
- Redis 프로토콜과 호환되는 Valkey 또는 Redis

현재 저장소에는 Docker Compose 파일이 없다. 따라서 PostgreSQL과 Valkey는 별도로 준비해야 한다. PostgreSQL에는 `vector` 확장을 설치할 수 있어야 하며 데이터베이스 이름은 `comeettool`을 사용한다.

## 6. 데이터베이스와 런타임 계정

### 6.1 데이터베이스 계약

| 항목 | 값 |
| --- | --- |
| 운영 DBMS | PostgreSQL 17 |
| 데이터베이스 | `comeettool` |
| 스키마 | `public` |
| 필수 확장 | `vector` |
| 기본 포트 | `5432` |
| TLS | 운영에서 `verify-full` |
| 운영 CA 경로 | `/app/certs/rds-ca-bundle.pem` |

`Main/Backend/.env.example`과 `application.yml`의 일부 로컬 기본값에는 과거 이름인 `commonpjt`가 남아 있다. 통합 실행과 운영에서는 반드시 `comeettool`을 사용한다.

### 6.2 주요 DB 계정

| 계정 | 용도 | 권한 원칙 |
| --- | --- | --- |
| Migration 계정 | Flyway 스키마 생성·변경 및 권한 부여 | 런타임 계정과 분리, 운영 애플리케이션에서 사용 금지 |
| `a707_backend_app` | Spring Backend 런타임 | 서비스 테이블에 필요한 CRUD만 허용 |
| `a707_yjs_app` | Yjs 문서 상태/버전 런타임 | 문서 관련 테이블에 필요한 최소 권한 |
| `a707_ai_app` | AI 처리 런타임 | 회의 조회 및 AI 결과·벡터 히스토리 쓰기 권한 |

계정 비밀번호는 `.env`, SQL 덤프 또는 문서에 저장하지 않는다. 운영에서는 ECS Task Definition이 AWS Secrets Manager 값을 `valueFrom`으로 주입한다.

### 6.3 스키마 적용

Flyway SQL 위치:

```text
Main/Backend/src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_schedules.sql
├── V3__add_remaining_schema.sql
└── V4__add_ai_runtime_schema.sql
```

운영에서는 `Main/Backend/Dockerfile.migration`으로 생성한 Migration 이미지를 ECS 단발성 Task로 실행한다. 다음 placeholder를 반드시 실제 런타임 DB Role 이름으로 주입한다.

```text
FLYWAY_PLACEHOLDERS_BACKENDRUNTIMEROLE=a707_backend_app
FLYWAY_PLACEHOLDERS_YJSRUNTIMEROLE=a707_yjs_app
FLYWAY_PLACEHOLDERS_AIRUNTIMEROLE=a707_ai_app
```

Migration 컨테이너 필수 변수:

```text
DB_HOST=<RDS endpoint>
DB_PORT=5432
DB_NAME=comeettool
FLYWAY_USER=<migration 계정>
FLYWAY_PASSWORD=<Secrets Manager에서 주입>
FLYWAY_PLACEHOLDERS_BACKENDRUNTIMEROLE=a707_backend_app
FLYWAY_PLACEHOLDERS_YJSRUNTIMEROLE=a707_yjs_app
FLYWAY_PLACEHOLDERS_AIRUNTIMEROLE=a707_ai_app
```

운영 Migration 이미지는 TLS `verify-full`과 `/flyway/certs/rds-ca-bundle.pem`을 강제한다. `vector` 확장을 생성할 수 있는 Migration 권한도 필요하다.

### 6.4 제출·로컬 구축용 스키마 덤프

Flyway V1~V4를 PostgreSQL 17과 pgvector 0.8.2 환경에 적용해 생성한 임시 스키마 덤프는 다음 위치에 있다.

```text
exec/database/comeettool_dump.sql
exec/database/comeettool_dump.sha256
exec/database/README.md
```

이 파일에는 운영 DB 데이터와 Secret이 없으며 Flyway 적용 이력 4건만 포함된다. 신규 전용 DB에 복원하는 방법과 검증 기준은 `exec/database/README.md`를 따른다. 운영 배포에서는 덤프 대신 Migration ECS Task와 Flyway SQL을 사용한다.

## 7. JWT 키 준비

Backend는 RS256 키 페어를 사용하고 Yjs는 Backend의 JWKS endpoint를 통해 공개키를 가져온다.

Windows PowerShell에서 키를 준비하는 예시는 다음과 같다.

```powershell
Set-Location Main/Backend
New-Item -ItemType Directory -Force src/main/resources/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out src/main/resources/keys/jwt_private.pem
openssl pkey -in src/main/resources/keys/jwt_private.pem -pubout -out src/main/resources/keys/jwt_public.pem
```

로컬 기본 경로:

```text
JWT_PRIVATE_KEY_PATH=classpath:keys/jwt_private.pem
JWT_PUBLIC_KEY_PATH=classpath:keys/jwt_public.pem
```

운영에서는 PEM 파일을 이미지에 포함하지 않고 Secrets Manager에서 Base64 문자열로 주입한다.

```text
JWT_PRIVATE_KEY_BASE64=<개인키 PEM의 Base64 값>
JWT_PUBLIC_KEY_BASE64=<공개키 PEM의 Base64 값>
```

- 개인키는 Backend에만 제공한다.
- Yjs에는 개인키를 제공하지 않는다.
- `src/main/resources/keys/`, 실제 `.env`, PEM 파일은 Git에 커밋하지 않는다.

## 8. Backend 설정 및 실행

### 8.1 설정 파일

```text
Main/Backend/.env.example
Main/Backend/src/main/resources/application.yml
Main/Backend/build.gradle
Main/Backend/Dockerfile
Main/Backend/Dockerfile.migration
```

`Main/Backend/.env.example`을 `Main/Backend/.env`로 복사한 뒤 값을 채운다.

```powershell
Copy-Item Main/Backend/.env.example Main/Backend/.env
```

### 8.2 Backend 환경변수

| 변수 | 필수 환경 | 설명 | 로컬 예시/기본값 |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 운영 | 활성 Spring Profile | 로컬 `local`, 운영 `prod` |
| `DB_URL` | 로컬 | JDBC 전체 URL | `jdbc:postgresql://localhost:5432/comeettool` |
| `DB_USERNAME` | 전체 | Backend DB 계정 | 로컬 계정 또는 `a707_backend_app` |
| `DB_PASSWORD` | 전체 | Backend DB 비밀번호 | Secret 값 |
| `DB_HOST` | 운영 | RDS endpoint | `<RDS endpoint>` |
| `DB_PORT` | 운영 | PostgreSQL 포트 | `5432` |
| `DB_NAME` | 운영 | DB 이름 | `comeettool` |
| `DB_SSL_ROOT_CERT` | 운영 | RDS CA 파일 | `/app/certs/rds-ca-bundle.pem` |
| `REDIS_HOST` | 전체 | Valkey/Redis 주소 | `localhost` |
| `REDIS_PORT` | 전체 | Valkey/Redis 포트 | `6379` |
| `REDIS_USERNAME` | 운영 | Valkey 사용자 | Secret 값 |
| `REDIS_PASSWORD` | 운영 | Valkey 비밀번호 | Secret 값 |
| `JWT_PRIVATE_KEY_PATH` | 로컬 | RSA 개인키 경로 | `classpath:keys/jwt_private.pem` |
| `JWT_PUBLIC_KEY_PATH` | 로컬 | RSA 공개키 경로 | `classpath:keys/jwt_public.pem` |
| `JWT_PRIVATE_KEY_BASE64` | 운영 | 개인키 Base64 | Secrets Manager |
| `JWT_PUBLIC_KEY_BASE64` | 운영 | 공개키 Base64 | Secrets Manager |
| `JWT_ISSUER` | 선택 | JWT 발급자 | `a707-api` |
| `JWT_API_AUDIENCE` | 선택 | Backend audience | `a707-api` |
| `JWT_YJS_AUDIENCE` | 선택 | Yjs audience | `a707-yjs` |
| `YJS_INTERNAL_BASE_URL` | 전체 | Backend에서 Yjs 내부 API 호출 주소 | `http://localhost:3000` |
| `YJS_INTERNAL_TOKEN` | 전체 | Backend↔Yjs 내부 인증 토큰 | 두 서버에 동일 값 주입 |
| `AI_INTERNAL_BASE_URL` | 전체 | Backend에서 AI 내부 API 호출 주소 | `http://localhost:8000` |
| `AI_INTERNAL_TOKEN` | 전체 | Backend↔AI 내부 인증 토큰 | 두 서버에 동일 값 주입 |
| `AI_CONNECT_TIMEOUT` | 선택 | AI 연결 제한 시간 | `3s` |
| `AI_READ_TIMEOUT` | 선택 | AI 응답 제한 시간 | `5s` |
| `AI_TRANSCRIPTION_MAX_ATTEMPTS` | 선택 | AI 처리 요청 최대 시도 | `3` |
| `LIVEKIT_URL` | 회의 기능 | LiveKit WebSocket URL | `wss://<project>.livekit.cloud` |
| `LIVEKIT_API_KEY` | 회의 기능 | LiveKit API Key | Secret 값 |
| `LIVEKIT_API_SECRET` | 회의 기능 | LiveKit API Secret | Secret 값 |
| `LIVEKIT_TOKEN_TTL` | 선택 | LiveKit 입장 토큰 수명 | `1h` |
| `STORAGE_PROVIDER` | 전체 | `FILE` 또는 `S3` | 로컬 `FILE`, 운영 `S3` |
| `PROFILE_IMG` | 전체 | 프로필 이미지 저장 방식 | `FILE` 또는 `S3` |
| `FILE_UPLOAD_DIR` | FILE 모드 | 로컬 업로드 경로 | `/uploads` |
| `FILE_BASE_URL` | 전체 | Backend 공개 기본 URL | `http://localhost:8080` |
| `AWS_S3_BUCKET` | S3 모드 | 공용 assets 버킷 이름 | `<bucket name>` |
| `AWS_S3_REGION` | S3 모드 | S3 리전 | `ap-northeast-2` |
| `MEETING_VAD_DRAIN_TIMEOUT` | 선택 | VAD 종료 대기 시간 | `20s` |
| `MEETING_VAD_QUIET_PERIOD` | 선택 | VAD 무음 판정 시간 | `300ms` |
| `MEETING_VAD_MAX_AUDIO_BYTES` | 선택 | VAD 단일 업로드 제한 | `5242880` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 필수 | 허용 Frontend Origin 목록 | 운영에서는 실제 HTTPS Origin만 허용 |

`STORAGE_PROVIDER=FILE`에서는 일반 Object Storage 기능이 비활성화되므로 회의 VAD 녹음, AI 입력 파일 및 S3 Presigned URL 기능을 완전하게 검증할 수 없다. 통합 시연과 운영은 `S3`를 사용한다.

### 8.3 Backend 빌드와 테스트

```powershell
Set-Location Main/Backend
.\gradlew.bat clean test bootJar
```

Linux/macOS:

```bash
cd Main/Backend
./gradlew clean test bootJar
```

JAR 결과물:

```text
Main/Backend/build/libs/backend-0.0.1-SNAPSHOT.jar
```

### 8.4 Backend 실행

IntelliJ에서는 `BackendApplication`을 실행하며 Working directory를 반드시 다음으로 지정한다.

```text
<저장소>/Main/Backend
```

터미널 실행:

```powershell
Set-Location Main/Backend
.\gradlew.bat bootRun
```

확인:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8080/.well-known/jwks.json
```

## 9. Yjs 협업 서버 설정 및 실행

### 9.1 설정 파일

```text
Main/Backend-Yjs/.env.example
Main/Backend-Yjs/package.json
Main/Backend-Yjs/Dockerfile
Main/Backend-Yjs/CrdtServer.js
```

`.env.example`을 `.env`로 복사하고 통합 DB인 `comeettool` 기준으로 작성한다. 테스트 전용 DB 변수는 운영 및 포팅 설정에 사용하지 않는다.

### 9.2 Yjs 환경변수

| 변수 | 필수 | 설명 | 예시 |
| --- | --- | --- | --- |
| `NODE_ENV` | 운영 | Node 실행 환경 및 기본 TLS 판단 | `production` |
| `PORT` | 선택 | Yjs 서버 포트 | `3000` |
| `DATABASE_URL` | 로컬 선택 | PostgreSQL 전체 URL | `postgresql://<user>:<password>@localhost:5432/comeettool` |
| `DB_HOST` | 운영 | RDS endpoint | `<RDS endpoint>` |
| `DB_PORT` | 운영 | PostgreSQL 포트 | `5432` |
| `DB_NAME` | 운영 | DB 이름 | `comeettool` |
| `DB_USERNAME` | 운영 | Yjs DB 계정 | `a707_yjs_app` |
| `DB_PASSWORD` | 운영 | Yjs DB 비밀번호 | Secrets Manager |
| `DB_SSL_ENABLED` | 전체 | PostgreSQL TLS | 로컬 `false`, 운영 `true` |
| `DB_SSL_ROOT_CERT` | 운영 | RDS CA 경로 | `/app/certs/rds-ca-bundle.pem` |
| `SPRING_JWKS_URL` | 전체 | Backend JWKS URL | `http://localhost:8080/.well-known/jwks.json` |
| `JWT_ISSUER` | 전체 | Backend JWT issuer와 일치 | `a707-api` |
| `JWT_YJS_AUDIENCE` | 전체 | Yjs audience와 일치 | `a707-yjs` |
| `YJS_INTERNAL_BASE_URL` | 전체 | Yjs 내부 기본 URL | `http://localhost:3000` |
| `YJS_INTERNAL_TOKEN` | 전체 | Backend와 공유하는 내부 토큰 | Secret 값 |

### 9.3 설치, 검증 및 실행

```powershell
Set-Location Main/Backend-Yjs
npm ci
node --check CrdtServer.js
node --check collaboration-auth.js
npm run test:auth
npm start
```

확인:

```text
GET http://localhost:3000/health
WebSocket ws://localhost:3000/collaboration
```

Yjs는 시작 및 JWT 검증 과정에서 Backend JWKS endpoint와 DB에 접근할 수 있어야 한다.

## 10. AI 서버 설정 및 실행

### 10.1 설정 파일

```text
Main/AI_BE/.env.example
Main/AI_BE/requirements.txt
Main/AI_BE/Dockerfile
Main/AI_BE/app/core/config.py
```

### 10.2 AI 환경변수

| 변수 | 필수 환경 | 설명 | 예시/기본값 |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | 전체 | SSAFY GMS에서 발급한 API Key | Secret 값 |
| `DATABASE_URL` | 로컬 | PostgreSQL 전체 URL | `postgresql://<user>:<password>@localhost:5432/comeettool` |
| `DB_HOST` | 운영 | RDS endpoint | `<RDS endpoint>` |
| `DB_PORT` | 운영 | PostgreSQL 포트 | `5432` |
| `DB_NAME` | 운영 | DB 이름 | `comeettool` |
| `DB_USER` | 운영 | AI 런타임 DB 계정 | `a707_ai_app` |
| `DB_PASSWORD` | 운영 | AI DB 비밀번호 | Secrets Manager |
| `DB_SSL_MODE` | 운영 | PostgreSQL TLS 모드 | `verify-full` |
| `DB_SSL_ROOT_CERT` | 운영 | RDS CA 경로 | `/app/certs/rds-ca-bundle.pem` |
| `S3_BUCKET_NAME` | 전체 | 회의 녹음 및 AI 결과 버킷 | `<bucket name>` |
| `AWS_REGION` | 전체 | AWS 리전 | `ap-northeast-2` |
| `AWS_ACCESS_KEY_ID` | 로컬 선택 | 로컬 정적 AWS 자격 증명 | IAM Role/SSO 사용 시 생략 |
| `AWS_SECRET_ACCESS_KEY` | 로컬 선택 | 로컬 정적 AWS 자격 증명 | IAM Role/SSO 사용 시 생략 |
| `AI_INTERNAL_TOKEN` | 전체 | Backend와 공유하는 내부 API 토큰 | Secret 값 |
| `STT_MODEL_SIZE` | 선택 | Whisper 모델 | `large-v3-turbo` |
| `DEVICE` | 선택 | 추론 장치 | 운영 CPU `cpu` |
| `COMPUTE_TYPE` | 선택 | Whisper 연산 형식 | 운영 CPU `int8` |
| `LLM_MODEL` | 선택 | 회의 분석 모델 | `gpt-5` |
| `EMBEDDING_MODEL` | 선택 | RAG 임베딩 모델 | `text-embedding-3-small` |

AI 코드는 OpenAI SDK를 사용하지만 요청 endpoint는 SSAFY GMS OpenAI 호환 gateway로 설정되어 있다. 임베딩 모델을 변경해 벡터 차원이 달라지면 `vector(1536)` DB 스키마와 코드의 차원도 함께 변경해야 한다.

### 10.3 설치와 검증

```powershell
Set-Location Main/AI_BE
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m pip install "pytest>=8.3,<9"
python -m pip check
python -m compileall -q .
python -m pytest -q pytest2.py
```

첫 STT 요청에서는 Whisper 모델을 내려받기 때문에 시작 시간이 길어질 수 있다.

### 10.4 실행

```powershell
Set-Location Main/AI_BE
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

확인:

```text
GET http://localhost:8000/health
```

처리 endpoint는 `X-Internal-Token` 헤더로 보호되며, Backend의 `AI_INTERNAL_TOKEN`과 AI의 값이 같아야 한다.

## 11. Frontend 설정 및 실행

### 11.1 설정 파일

```text
Main/Frontend/.env.example
Main/Frontend/package.json
Main/Frontend/package-lock.json
Main/Frontend/vite.config.js
```

### 11.2 Frontend 환경변수

| 변수 | 설명 | 로컬 통합값 |
| --- | --- | --- |
| `VITE_USE_MOCK_API` | 전체 Mock 사용 여부 | `false` |
| `VITE_USE_MOCK_AUTH_API` | 인증 Mock | `false` |
| `VITE_USE_MOCK_USER_API` | 사용자 Mock | `false` |
| `VITE_USE_MOCK_BOARD_API` | Board Mock | `false` |
| `VITE_USE_MOCK_SPACE_API` | 팀 스페이스 Mock | `false` |
| `VITE_USE_MOCK_DOCUMENT_API` | 문서 Mock | `false` |
| `VITE_USE_MOCK_MEETING_API` | 회의 Mock | `false` |
| `VITE_USE_MOCK_SCHEDULE_API` | 일정 Mock | `false` |
| `VITE_USE_MOCK_NOTIFICATION_API` | 알림 Mock | `false` |
| `VITE_USE_MOCK_REPORT_API` | AI 결과 Mock | `false` |
| `VITE_USE_MOCK_PASSWORD_RESET_API` | 비밀번호 재설정 Mock | 실제 API 지원 여부에 맞게 설정 |
| `VITE_USE_MOCK_USER_WITHDRAW_API` | 회원 탈퇴 Mock | `false` |
| `VITE_USE_MOCK_USER_SEARCH_API` | 사용자 검색 Mock | `false` |
| `VITE_API_BASE_URL` | Backend URL, 끝 `/` 금지 | `http://localhost:8080` |
| `VITE_YJS_WEBSOCKET_URL` | Yjs URL, `/collaboration` 포함 | `ws://localhost:3000/collaboration` |

`VITE_USE_MOCK_API`를 지정하지 않으면 코드상 Mock 모드로 판단한다. Backend 연동 빌드에서는 반드시 `false`로 지정한다.

### 11.3 설치, 테스트 및 빌드

```powershell
Set-Location Main/Frontend
npm ci
npm run format:check
npm run test
npm run build
```

빌드 결과:

```text
Main/Frontend/dist/
```

### 11.4 개발 서버 실행

```powershell
Set-Location Main/Frontend
npm run dev
```

접속 주소:

```text
http://localhost:5173/
```

## 12. 로컬 통합 실행 순서

1. PostgreSQL과 Valkey를 시작한다.
2. `comeettool` DB를 생성하고 V1~V4 마이그레이션을 적용한다.
3. JWT RSA 키 페어를 생성한다.
4. Backend `.env`를 작성하고 Backend를 8080에서 실행한다.
5. Backend의 `/actuator/health`와 JWKS endpoint를 확인한다.
6. Yjs `.env`를 작성하고 3000에서 실행한다.
7. AI `.env`를 작성하고 8000에서 실행한다.
8. Frontend `.env`에서 모든 실제 연동 대상 Mock을 끄고 5173에서 실행한다.
9. 브라우저에서 회원가입·로그인 후 팀 스페이스, 문서, 회의 기능을 확인한다.

통합 환경에서는 다음 값 쌍이 반드시 일치해야 한다.

| Producer | Consumer | 일치해야 하는 값 |
| --- | --- | --- |
| Backend | Yjs | `JWT_ISSUER`, `JWT_YJS_AUDIENCE` |
| Backend | Yjs | `YJS_INTERNAL_TOKEN` |
| Backend | AI | `AI_INTERNAL_TOKEN` |
| Backend | Frontend | API URL 및 CORS Origin |
| Yjs | Frontend | `/collaboration` WebSocket URL |
| Backend/Yjs/AI | PostgreSQL | DB 이름 `comeettool` 및 각 계정 권한 |

## 13. Docker 이미지 빌드

저장소 루트에서 실행한다.

```bash
docker build -t a707-backend:local -f Main/Backend/Dockerfile Main/Backend
docker build -t a707-yjs:local -f Main/Backend-Yjs/Dockerfile Main/Backend-Yjs
docker build -t a707-migration:local -f Main/Backend/Dockerfile.migration Main/Backend
docker build -t a707-ai:local -f Main/AI_BE/Dockerfile Main/AI_BE
```

Frontend는 컨테이너 이미지가 아니라 `npm run build`로 만들어진 `dist/`를 S3에 게시한다.

모든 운영 이미지는 `linux/amd64` 단일 이미지로 빌드한다. GitLab CI는 Buildah를 사용하며 이미지 태그는 다음 형식이다.

```text
git-<40자리 CI_COMMIT_SHA>
```

## 14. GitLab CI/CD

### 14.1 파이프라인 단계

```text
preflight → test → build → security → oidc-verify → package → release
```

- 파이프라인은 `develop`에서 `main`으로 보내는 Merge Request와 `main` 브랜치 push 두 경우에만 생성된다.
- 실제 운영 배포는 보호된 `main` 브랜치 파이프라인만 실행한다.
- 컨테이너 이미지는 ECR의 immutable tag와 scan-on-push 설정을 검사한다.
- AWS 장기 Access Key는 사용하지 않고 GitLab OIDC 토큰으로 STS Role을 Assume한다.
- 운영 release job은 동시 실행을 막기 위해 `resource_group: production`을 사용한다.

### 14.2 GitLab CI/CD 필수 변수

| 변수 | 형식 | 설명 |
| --- | --- | --- |
| `AWS_OIDC_ROLE_ARN` | `arn:aws:iam::<account-id>:role/<role-name>` | GitLab OIDC 배포 Role |
| `DEPLOY_FRONTEND_BUCKET` | S3 버킷 이름만 | Frontend 정적 파일 버킷 |
| `DEPLOY_CLOUDFRONT_DISTRIBUTION_ID` | CloudFront Distribution ID | 배포 후 invalidation 대상 |
| `DEPLOY_FRONTEND_URL` | `https://...`, 끝 `/` 없음 | Frontend smoke test URL |
| `DEPLOY_API_BASE_URL` | `https://...`, 끝 `/` 없음 | Backend 공개 URL |
| `DEPLOY_YJS_WEBSOCKET_URL` | `wss://.../collaboration` | Yjs 공개 WebSocket URL |

현재 서비스 기준 URL 예시:

```text
DEPLOY_FRONTEND_URL=https://app.comeettool.cloud
DEPLOY_API_BASE_URL=https://api.comeettool.cloud
DEPLOY_YJS_WEBSOCKET_URL=wss://api.comeettool.cloud/collaboration
```

GitLab Free 플랜 제약으로 Environment scope를 별도로 사용하지 않는 경우 변수 scope는 전체(`*`)로 두되, `main` 보호 브랜치에서 release job이 값을 읽을 수 있도록 변수의 Protected 설정과 브랜치 보호 설정을 함께 확인한다.

### 14.3 기존 AWS 리소스 계약

CI 스크립트는 다음 물리 리소스 이름을 고정적으로 사용한다.

| 종류 | 이름 |
| --- | --- |
| ECS Cluster | `a707-dev-cluster` |
| Backend Service/Task Family | `a707-dev-backend` |
| Yjs Service/Task Family | `a707-dev-yjs` |
| AI Service/Task Family | `a707-dev-ai` |
| Migration Task Family | `a707-dev-migration` |
| Backend ECR | `a707-dev-backend` |
| Yjs ECR | `a707-dev-yjs` |
| AI ECR | `a707-dev-ai` |
| Migration ECR | `a707-dev-migration` |

이름을 변경하려면 `.gitlab/ci/scripts/package-container-images.sh`와 `production-release.sh`의 상수를 함께 변경해야 한다.

### 14.4 배포 순서

`main` 배포 파이프라인은 다음 순서로 동작한다.

1. 현재 Commit이 최신 `main` Commit인지 검증한다.
2. GitLab OIDC로 AWS STS 임시 자격 증명을 발급받는다.
3. ECS 서비스, ECR 저장소, Frontend S3 버킷, CloudFront 상태를 검사한다.
4. Backend, Yjs, Migration, AI 이미지를 `linux/amd64`로 빌드하고 ECR에 Push한다.
5. 이미지 digest를 확인하고 태그 대신 digest 기반 Task Definition revision을 등록한다.
6. Migration Task를 실행하고 exit code 0을 확인한다.
7. Backend, Yjs, AI ECS 서비스를 새 revision으로 갱신한다.
8. ECS deployment circuit breaker와 rollback 설정 하에서 서비스 안정화를 기다린다.
9. Frontend `dist/`를 S3에 게시한다.
10. CloudFront invalidation을 생성하고 완료될 때까지 기다린다.
11. Backend health/JWKS, Frontend, CORS, Yjs WebSocket을 smoke test한다.

## 15. 운영 ECS 설정 주의사항

- Backend에는 `SPRING_PROFILES_ACTIVE=prod`, Yjs에는 `NODE_ENV=production`과 `DB_SSL_ENABLED=true`를 명시한다.
- Backend, Yjs, AI는 Fargate와 `awsvpc` 네트워크 모드를 사용한다.
- Public IP는 비활성화하고 private subnet에서 실행한다.
- ECS deployment circuit breaker와 자동 rollback을 활성화한다.
- ECS Exec은 현재 배포 계약상 비활성화한다.
- Cloud Map 내부 DNS로 서비스 간 통신한다.
- Backend, Yjs, AI Task Role은 각각 필요한 S3 prefix와 Secret만 접근하도록 분리한다.
- Backend와 Yjs가 서로 통신할 수 있도록 Security Group egress/ingress를 모두 확인한다.
- RDS와 Valkey는 애플리케이션 Security Group에서만 접근하도록 제한한다.
- CloudWatch Log Group은 Task 실행 전에 존재해야 한다. `awslogs-create-group=false`는 넣지 말고 옵션 자체를 생략한다.
- RDS CA 인증서를 Backend, Yjs, AI, Migration 이미지의 약정 경로에 포함한다.
- 운영 S3 객체는 비공개로 유지하고 Backend 또는 AI Task Role 및 Presigned URL을 사용한다.

## 16. 주요 계정·프로퍼티 정의 파일 목록

| 파일 | 정의 내용 |
| --- | --- |
| `Main/Backend/.env.example` | Backend DB, Valkey, JWT, Yjs, AI, LiveKit, S3, CORS 변수 |
| `Main/Backend/src/main/resources/application.yml` | Spring local/prod profile, 기본값, TLS, JWT audience/issuer |
| `Main/Backend/build.gradle` | Java/Spring Boot/SDK 의존성 버전 |
| `Main/Backend/src/main/resources/db/migration/*.sql` | ERD 실체인 테이블·인덱스·제약·DB Role 권한 |
| `Main/Backend/docker/migration-entrypoint.sh` | 운영 Flyway DB 연결 및 placeholder 계약 |
| `Main/Backend-Yjs/.env.example` | Yjs DB, JWKS, JWT, 내부 인증 변수 |
| `Main/Backend-Yjs/package.json` | Yjs 런타임 의존성과 실행 명령 |
| `Main/AI_BE/.env.example` | AI DB, S3, GMS, 내부 인증 변수 |
| `Main/AI_BE/app/core/config.py` | AI 환경변수 유효성 검사와 DB URL 조립 규칙 |
| `Main/AI_BE/requirements.txt` | Python 패키지 버전 |
| `Main/Frontend/.env.example` | API/Yjs URL과 Mock 전환 변수 |
| `Main/Frontend/package.json` | Frontend 의존성·빌드·테스트 명령 |
| `.gitlab-ci.yml` | CI 이미지 버전, Job 규칙, 빌드 환경변수 |
| `.gitlab/ci/scripts/validate-production-inputs.sh` | 운영 배포 입력값 형식 |
| `.gitlab/ci/scripts/package-container-images.sh` | ECR 저장소와 이미지 빌드 계약 |
| `.gitlab/ci/scripts/production-release.sh` | ECS/S3/CloudFront 운영 배포 계약 |

## 17. Secret 관리 원칙

다음 파일과 값은 Git에 커밋하지 않는다.

- 모든 실제 `.env`
- JWT private/public PEM 파일
- AWS Access Key 및 Session Token
- RDS, Valkey, 애플리케이션 DB 비밀번호
- LiveKit API Secret
- GMS/OpenAI API Key
- Backend↔Yjs, Backend↔AI 내부 인증 토큰
- Presigned URL
- 운영 DB 데이터가 포함된 비정제 덤프

운영 Secret은 AWS Secrets Manager에 저장하고 ECS Task Definition의 `secrets.valueFrom`으로 주입한다. GitLab에는 AWS 장기 키 대신 OIDC Role ARN과 배포에 필요한 비민감 식별자만 등록한다.

## 18. 배포 후 확인

다음 순서로 점검한다.

```text
1. https://api.comeettool.cloud/actuator/health → HTTP 200
2. https://api.comeettool.cloud/.well-known/jwks.json → HTTP 200, RSA JWKS
3. https://app.comeettool.cloud/ → HTTP 200
4. SPA 하위 경로 직접 접근 → HTTP 200 및 index.html 반환
5. 허용 Origin CORS 요청 → 성공
6. 허용되지 않은 Origin → 차단
7. wss://api.comeettool.cloud/collaboration → HTTP 101 Upgrade
8. 회원가입/로그인 및 Refresh Token → Valkey 정상 저장
9. 팀 스페이스·일정·공유 문서 CRUD
10. LiveKit 입장·퇴장·웹훅
11. VAD 녹음 S3 업로드 및 AI 처리
12. 회의록·전사본·퍼실리테이터 결과 조회 및 MD/PDF 다운로드
```

배포 실패 시 우선순위는 다음과 같다.

1. GitLab Job 로그의 최초 오류
2. ECS Service Event와 rollout 상태
3. 중지된 Task의 `stoppedReason` 및 컨테이너 exit code
4. CloudWatch Log Group의 Application startup 오류
5. Task Definition의 환경변수·Secret selector·컨테이너 포트
6. Security Group, Route Table, DNS, Cloud Map
7. ALB Target Group health와 WAF terminating rule

## 19. 알려진 주의사항

- Backend 로컬 DB 기본값 `commonpjt`를 그대로 사용하지 말고 `comeettool`로 변경한다.
- Frontend 환경변수는 빌드 시점에 번들에 삽입된다. 배포 후 ECS 변수만 바꿔서는 Frontend 설정이 바뀌지 않으므로 다시 빌드하고 S3에 게시해야 한다.
- `VITE_USE_MOCK_API`를 누락하면 Mock 모드가 활성화될 수 있다.
- Yjs WebSocket 공개 URL은 반드시 `/collaboration`으로 끝나야 한다.
- LiveKit URL은 `wss://`를 사용한다.
- 운영 Backend CORS에는 실제 Frontend HTTPS Origin을 포함해야 한다.
- WAF Managed Rule이 multipart 이미지 또는 오디오 본문을 XSS로 오탐할 수 있다. 허용 예외는 URI와 HTTP Method를 함께 제한한다.
- S3와 RDS는 TLS 연결을 사용한다.
- AI Fargate CPU 환경은 `DEVICE=cpu`, `COMPUTE_TYPE=int8`을 사용한다.
- AI의 첫 Whisper 실행은 모델 다운로드로 인해 오래 걸릴 수 있으므로 Task의 네트워크 egress와 ephemeral storage를 확인한다.
- DB Migration 완료 전에 새 Backend/AI Task를 배포하지 않는다.
- `out/`은 IntelliJ 산출물이므로 배포 입력 및 인수인계 파일로 사용하지 않는다.
