# CoMeetTool 외부 서비스 설정 매뉴얼

## 1. 문서 목적

이 문서는 CoMeetTool이 빌드 또는 실행 중 실제로 사용하는 외부 서비스의 계정 준비, 프로젝트 생성, 키 발급, 권한 연결, 환경변수, 운영 주의사항을 설명한다.

민감정보는 문서와 Git 저장소에 기록하지 않는다. 아래 `<...>` 표기는 서비스 콘솔 또는 Secret 저장소에서 발급받아 입력해야 하는 값이다.

## 2. 외부 서비스 요약

| 서비스 | 프로젝트 내 용도 | 가입 필요 | 주요 인증 정보 |
| --- | --- | --- | --- |
| AWS | 애플리케이션 운영 인프라와 CI/CD 배포 | 필요 | IAM Identity Center, OIDC Role, ECS Task Role |
| GitLab | 소스 저장소, CI/CD, Runner, AWS OIDC 토큰 발급 | 필요 | GitLab 계정, Runner Token, OIDC ID Token |
| LiveKit Cloud | 화상회의, 화면 공유, 실시간 미디어, Webhook | 필요 | Project URL, API Key, API Secret |
| SSAFY GMS | 회의록·퍼실리테이터 분석 및 임베딩 | SSAFY 권한 필요 | GMS API Key |
| jsDelivr | 브라우저 VAD 모델과 ONNX Runtime WASM 제공 | 불필요 | 없음 |
| Hugging Face Hub | faster-whisper 모델 최초 다운로드 | 공개 모델은 불필요 | 현재 별도 Token 미사용 |
| 도메인 등록기관 | `comeettool.cloud` 소유권과 Route 53 위임 | 필요 | 등록기관 계정, 도메인 갱신 권한 |

## 3. AWS

### 3.1 사용 목적

| AWS 서비스 | 용도 |
| --- | --- |
| IAM / IAM Identity Center | 관리자 로그인, 로컬 CLI SSO, 서비스별 최소권한 Role |
| IAM OIDC Provider / STS | GitLab CI에서 임시 AWS 자격 증명 발급 |
| VPC / Subnet / Route Table / NAT Gateway | ECS·RDS·Valkey의 사설 네트워크와 외부 egress |
| ALB | Backend HTTPS 및 Yjs WebSocket 라우팅 |
| ECS Fargate | Backend, Yjs, AI, Migration 컨테이너 실행 |
| ECR | 배포 이미지 저장 |
| RDS PostgreSQL | 공용 `comeettool` 데이터베이스 |
| ElastiCache Valkey | Refresh Token 저장 |
| S3 | 프로필 이미지, 회의 음성, AI 결과, Frontend 정적 파일 |
| Secrets Manager | DB·JWT·LiveKit·GMS·내부 토큰 저장 |
| Cloud Map | ECS 서비스 간 내부 DNS |
| CloudWatch Logs | ECS 애플리케이션 로그 |
| Route 53 / ACM | DNS와 TLS 인증서 |
| CloudFront | Frontend CDN과 SPA 배포 |
| WAF | ALB 및 CloudFront 요청 보호 |

운영 리전은 `ap-northeast-2`다. CloudFront용 ACM 인증서는 CloudFront 요구사항에 따라 `us-east-1`에서 관리하고, ALB용 인증서는 `ap-northeast-2`에서 관리한다.

### 3.2 계정과 관리자 접근 준비

1. 프로젝트 AWS 계정을 준비한다.
2. 루트 사용자는 초기 설정과 복구에만 사용하고 일상 작업에는 사용하지 않는다.
3. IAM Identity Center에 운영 담당자를 등록한다.
4. 담당자에게 필요한 Permission Set만 연결한다.
5. MFA를 활성화한다.
6. 기본 작업 리전을 `ap-northeast-2`로 사용한다.

로컬 AWS CLI는 장기 Access Key보다 IAM Identity Center SSO를 사용한다.

```bash
aws configure sso --profile a707-admin
aws sso login --profile a707-admin
aws sts get-caller-identity --profile a707-admin
```

입력 항목:

| 질문 | 입력 원칙 |
| --- | --- |
| SSO session name | 팀에서 합의한 식별자, 예: `a707` |
| SSO start URL | 팀 AWS Access Portal URL |
| SSO region | IAM Identity Center를 활성화한 리전 |
| Registration scopes | `sso:account:access` |
| AWS account | A707 프로젝트 AWS 계정 선택 |
| Role | 담당자에게 할당된 Permission Set |
| Default client Region | `ap-northeast-2` |
| Output format | `json` |
| Profile name | 예: `a707-admin` |

브라우저 자동 로그인이 어려운 환경에서는 다음 옵션을 사용할 수 있다.

```bash
aws configure sso --profile a707-admin --use-device-code
```

참고: [AWS CLI IAM Identity Center 설정](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sso.html)

### 3.3 ECS Role 구분

ECS에서는 Execution Role과 Task Role을 구분한다.

| Role | 사용하는 주체 | 필요한 대표 권한 |
| --- | --- | --- |
| Task Execution Role | ECS/Fargate Agent | ECR 이미지 Pull, CloudWatch Log Stream, Task Definition의 Secrets Manager 값 조회 |
| Task Role | 컨테이너 내부 애플리케이션 | S3 등 애플리케이션 코드가 직접 호출하는 AWS API |

Secret을 Task Definition의 `secrets.valueFrom`으로 주입한다면 `secretsmanager:GetSecretValue`는 Task Execution Role에 부여한다. 애플리케이션이 S3 SDK를 직접 호출하므로 S3 권한은 해당 애플리케이션 Task Role에 부여한다.

참고: [ECS Task Execution Role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html), [ECS Task Role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html)

### 3.4 서비스별 S3 권한 범위

공용 Assets 버킷은 비공개로 유지하며 SSE-S3(AES-256)를 사용한다. 버킷 정책에는 최소한 비암호화 전송 거부 정책을 유지한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DenyInsecureTransport",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": [
        "arn:aws:s3:::<assets-bucket>",
        "arn:aws:s3:::<assets-bucket>/*"
      ],
      "Condition": {
        "Bool": {
          "aws:SecureTransport": "false"
        }
      }
    }
  ]
}
```

Task Role의 권한은 Prefix 단위로 제한한다.

| 주체 | Prefix | 필요한 작업 |
| --- | --- | --- |
| Backend | `profile-images/users/*` | `GetObject`, `PutObject`, `DeleteObject` |
| Backend | `profile-images/teams/*` | `GetObject`, `PutObject`, `DeleteObject` |
| Backend | `conferences/*` | VAD 세그먼트 업로드·조회·정리에 필요한 Object 작업 |
| Backend | `ai-results/*` | 결과 업로드, 존재 확인, 다운로드 서명에 필요한 `GetObject` |
| AI | `conferences/*` | 회의별 Prefix 목록 조회와 `GetObject` |

현재 AI 코드는 분석 결과를 PostgreSQL에 저장하고 S3에서는 `conferences/*` 녹음만 읽는다. 따라서 현재 코드 기준 AI Task Role에 `ai-results/*` 쓰기 권한은 필수가 아니다. 향후 AI가 결과 파일을 S3에 직접 기록하도록 구현할 때만 해당 Prefix 권한을 추가한다.

`ListBucket`은 버킷 ARN에 부여하고 `s3:prefix` 조건으로 위 Prefix만 허용한다. Object 작업은 `arn:aws:s3:::<assets-bucket>/<prefix>*`에 부여한다.

Backend가 발급하는 MD/PDF Presigned URL은 Task Role의 `GetObject` 권한으로 서명된다. URL은 현재 5분 동안 유효하며 DB나 로그에 저장하지 않는다. Presigned URL은 비공개 버킷 정책을 공개로 바꾸지 않고도 제한 시간 동안 다운로드를 허용한다.

참고: [S3 Presigned URL](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)

### 3.5 Secrets Manager 구성

Secrets Manager에는 최소한 다음 종류의 값을 저장한다.

| 대상 | Secret 내용 |
| --- | --- |
| Backend DB | host, port, dbname, username, password |
| Yjs DB | host, port, dbname, username, password |
| AI DB | host, port, dbname, username, password |
| Migration DB | host, port, dbname, username, password |
| Backend JWT | private key Base64, public key Base64 |
| Valkey | host, port, username, password |
| Backend↔Yjs | `YJS_INTERNAL_TOKEN` |
| Backend↔AI | `AI_INTERNAL_TOKEN` |
| LiveKit | URL, API Key, API Secret |
| SSAFY GMS | API Key |

Task Definition에서는 Secret 전체를 일반 환경변수에 복사하지 않고 필요한 JSON key만 `valueFrom`으로 선택한다.

Secret 값을 변경하거나 Rotation한 뒤에는 실행 중인 Task에 자동 반영되지 않는다. ECS 서비스에서 새 Task를 시작하도록 Force new deployment 또는 새 Task Definition 배포가 필요하다.

참고: [ECS에 Secrets Manager 값 주입](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html)

### 3.6 AWS 네트워크 요구사항

- Backend, Yjs, AI, Migration Task는 private subnet에 배치한다.
- ECS Task의 Public IP는 비활성화한다.
- NAT Gateway 또는 필요한 VPC Endpoint를 통해 외부 HTTPS egress를 제공한다.
- AI Task는 SSAFY GMS와 Hugging Face 모델 저장소에 HTTPS로 접근할 수 있어야 한다.
- Backend는 LiveKit Cloud API에 HTTPS로 접근할 수 있어야 한다.
- Frontend 사용자의 브라우저는 LiveKit Cloud WebSocket과 jsDelivr CDN에 접근할 수 있어야 한다.
- Yjs는 Backend JWKS endpoint에 접근할 수 있어야 한다.
- Backend는 Yjs 및 AI의 Cloud Map 내부 주소에 접근할 수 있어야 한다.
- RDS와 Valkey는 애플리케이션 Security Group에서만 접근하도록 제한한다.

### 3.7 AWS 비용 주의사항

특히 다음 항목은 사용하지 않아도 시간 단위 또는 저장량 기준 비용이 발생할 수 있다.

- NAT Gateway 및 데이터 처리량
- ALB
- RDS 인스턴스와 스토리지/백업
- Valkey 노드
- ECS Fargate CPU·메모리 실행 시간
- WAF 요청 및 관리형 규칙
- CloudWatch 로그 저장량
- S3 저장·요청·전송량
- CloudFront 전송량과 요청
- Secrets Manager Secret 수

사용량과 예산 알림은 AWS Budgets 또는 Billing Alarm으로 별도 관리한다.

## 4. GitLab 및 GitLab Runner

### 4.1 사용 목적

- Git 저장소 호스팅
- Merge Request와 브랜치 보호
- Backend/Yjs/AI/Frontend 검증
- Secret 및 취약점 검사
- 컨테이너 이미지 패키징
- GitLab OIDC 기반 AWS 배포

프로젝트 주소:

```text
https://lab.ssafy.com/s15-webmobile1-sub1/S15P11A707
```

### 4.2 계정과 프로젝트 권한

1. SSAFY GitLab 계정을 준비한다.
2. 프로젝트 멤버로 초대받는다.
3. 일반 개발자는 Developer, 배포 설정 담당자는 Maintainer 이상 권한을 사용한다.
4. `main`은 보호 브랜치로 설정한다.
5. 배포용 CI/CD 변수는 Maintainer만 관리한다.

현재 운영 배포 Job은 다음 조건에서만 실행된다.

```text
CI_COMMIT_BRANCH == main
CI_COMMIT_REF_PROTECTED == true
```

### 4.3 Runner 준비

Runner는 다음 기능을 제공해야 한다.

- Docker 기반 Job 실행
- Linux/amd64 이미지 빌드
- Buildah 실행에 필요한 컨테이너 권한
- npm, Gradle, Python 패키지 저장소 접근
- GitLab API, AWS STS, ECR 접근
- Job Artifact 업로드

Runner 등록 Token은 등록 시에만 사용하고 문서나 저장소에 기록하지 않는다. Docker Socket을 Runner에 마운트하면 호스트 제어 권한이 생기므로 전용 호스트에서만 사용하고 일반 사용자 작업 환경과 분리한다.

### 4.4 GitLab–AWS OIDC 연결

현재 CI는 AWS Access Key를 GitLab 변수에 저장하지 않는다. Job의 `id_tokens`에서 audience가 `sts.amazonaws.com`인 ID Token을 발급받고 `AssumeRoleWithWebIdentity`로 임시 자격 증명을 얻는다.

구성 절차:

1. AWS IAM에서 GitLab 인스턴스를 OIDC Identity Provider로 등록한다.
2. Provider URL은 실제 GitLab 인스턴스 URL을 사용한다.
3. Audience/Client ID는 `sts.amazonaws.com`을 사용한다.
4. GitLab 프로젝트 전용 배포 Role을 생성한다.
5. Role Trust Policy에서 GitLab issuer, audience, 프로젝트, 브랜치를 제한한다.
6. 배포 Role ARN을 GitLab 변수 `AWS_OIDC_ROLE_ARN`에 등록한다.
7. `oidc:verify-aws` Job에서 STS 호출 성공을 확인한다.

Trust Policy는 가능한 경우 경로 문자열뿐 아니라 GitLab `project_id` 또는 namespace처럼 변경에 강한 claim도 제한한다. `main` 외 브랜치가 운영 Role을 Assume하지 못하도록 조건을 둔다.

참고: [GitLab AWS OIDC 구성](https://docs.gitlab.com/ci/cloud_services/aws/), [AWS IAM OIDC Provider 생성](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_create_oidc.html)

### 4.5 GitLab CI/CD 변수

| 변수 | 내용 | 민감 여부 |
| --- | --- | --- |
| `AWS_OIDC_ROLE_ARN` | AWS 배포 Role ARN | 제한 정보 |
| `DEPLOY_FRONTEND_BUCKET` | Frontend S3 버킷 이름 | 비밀 아님 |
| `DEPLOY_CLOUDFRONT_DISTRIBUTION_ID` | CloudFront Distribution ID | 비밀 아님 |
| `DEPLOY_FRONTEND_URL` | Frontend HTTPS URL | 비밀 아님 |
| `DEPLOY_API_BASE_URL` | Backend HTTPS URL | 비밀 아님 |
| `DEPLOY_YJS_WEBSOCKET_URL` | `/collaboration`으로 끝나는 WSS URL | 비밀 아님 |

GitLab Free 플랜에서 Environment scope를 사용하지 않는 경우 scope는 전체(`*`)로 두되, 보호된 `main` Pipeline에서 읽을 수 있도록 Protected 변수 여부를 확인한다.

## 5. LiveKit Cloud

### 5.1 사용 목적

- 회의방 연결
- 카메라와 마이크 스트림
- 화면 공유
- 실시간 채팅 및 참가자 상태
- Backend가 회의방과 참가자를 관리하기 위한 Server API
- 참가자 입장·퇴장과 회의 종료 상태를 동기화하기 위한 Webhook

Frontend는 Backend가 발급한 참가자 Token과 LiveKit URL만 받는다. API Secret은 Frontend에 전달하지 않는다.

### 5.2 계정과 프로젝트 생성

1. [LiveKit Cloud](https://cloud.livekit.io/) 계정을 생성하고 로그인한다.
2. 조직 또는 Workspace를 준비한다.
3. CoMeetTool용 Cloud Project를 생성한다.
4. Project 설정에서 secure WebSocket URL을 확인한다.
5. API Keys 화면에서 Backend용 Key/Secret을 생성한다.
6. 사용량과 프로젝트 규모에 맞는 요금제를 선택하고 Billing/Quota를 확인한다.

필요한 값:

```text
LIVEKIT_URL=wss://<project-subdomain>.livekit.cloud
LIVEKIT_API_KEY=<project API key>
LIVEKIT_API_SECRET=<project API secret>
LIVEKIT_TOKEN_TTL=1h
```

Project URL은 반드시 `wss://`를 사용한다. API Secret은 Backend의 Secrets Manager에만 저장한다.

참고: [LiveKit CLI와 Project URL 확인](https://docs.livekit.io/reference/developer-tools/livekit-cli/)

### 5.3 Webhook 설정

현재 Backend endpoint:

```text
POST https://api.comeettool.cloud/api/v1/webhooks/livekit
Content-Type: application/webhook+json
Authorization: <LiveKit signed JWT>
```

LiveKit Cloud에서 다음과 같이 등록한다.

1. 대상 Project를 선택한다.
2. `Settings → Webhooks`로 이동한다.
3. 새 Webhook을 생성한다.
4. URL에 위 Backend endpoint를 입력한다.
5. Signing API Key로 Backend의 `LIVEKIT_API_KEY`와 대응되는 Key를 선택한다.
6. 저장 후 Test Event를 전송한다.

Backend가 처리하는 이벤트:

```text
participant_joined
participant_left
room_finished
```

Webhook은 사용자 JWT 필터에서는 제외되지만 LiveKit Server SDK의 `WebhookReceiver`가 원문 Body와 Authorization 헤더를 검증한다. ALB와 WAF가 `application/webhook+json` POST 요청을 Backend Target Group으로 전달해야 한다.

LiveKit은 일시적 실패에 대해 재시도하지만 전달을 완전히 보장하지 않는다. Backend 처리는 중복 이벤트에 안전하도록 유지하고 CloudWatch에서 401/403/5xx 응답을 감시한다.

참고: [LiveKit Webhook 설정과 검증](https://docs.livekit.io/intro/basics/rooms-participants-tracks/webhooks-events/)

### 5.4 검증

1. LiveKit Dashboard에서 Test Event를 전송한다.
2. Backend 응답이 2xx인지 확인한다.
3. CloudWatch에 Webhook 서명 오류가 없는지 확인한다.
4. 두 브라우저에서 같은 회의에 입장한다.
5. 카메라, 마이크, 화면 공유, 참가자 목록을 확인한다.
6. 한 참가자가 퇴장할 때 DB 상태가 갱신되는지 확인한다.
7. 방 종료 시 `room_finished` 처리와 AI 요청 흐름을 확인한다.

### 5.5 비용 및 운영 주의사항

- LiveKit Cloud는 사용량·동시 접속·전송량·기능에 따라 과금될 수 있다.
- API Key 교체 시 Backend Secret과 Webhook Signing Key를 함께 갱신한다.
- Secret 변경 후 ECS Backend를 새로 배포한다.
- 브라우저와 기업망에서 LiveKit WebSocket 및 WebRTC 연결을 허용해야 한다.
- 사용자별 Identity는 중복되지 않도록 Backend가 발급한다.

## 6. SSAFY GMS

### 6.1 사용 목적

AI 서버는 OpenAI Python SDK를 사용하지만 다음 SSAFY GMS OpenAI 호환 endpoint로 요청한다.

```text
https://gms.ssafy.io/gmsapi/api.openai.com/v1
```

사용 기능:

| 기능 | 기본 모델 |
| --- | --- |
| 회의록 구조화 | `gpt-5` |
| 퍼실리테이터 보고서 구조화 | `gpt-5` |
| 회의록 RAG 임베딩 | `text-embedding-3-small` |
| 퍼실리테이터 RAG 임베딩 | `text-embedding-3-small` |

### 6.2 계정과 키 준비

SSAFY 내부 GMS 서비스는 공개 가입 절차가 아니라 SSAFY 계정과 프로젝트 사용 권한이 필요하다.

1. SSAFY 계정으로 GMS 서비스에 접근한다.
2. 팀 또는 프로젝트에 사용할 API Key를 발급받는다.
3. 발급 Key가 `gpt-5`와 `text-embedding-3-small` 사용 권한·쿼터를 가지는지 확인한다.
4. Key를 AWS Secrets Manager에 저장한다.
5. AI ECS Task에 `OPENAI_API_KEY`로 주입한다.

```text
OPENAI_API_KEY=<GMS에서 발급받은 Key>
LLM_MODEL=gpt-5
EMBEDDING_MODEL=text-embedding-3-small
```

API Key는 `.env.example`, GitLab 변수, 이미지 Layer, CloudWatch 로그에 남기지 않는다.

### 6.3 네트워크와 장애 처리

- AI Task Security Group에서 외부 HTTPS 443 egress가 가능해야 한다.
- private subnet의 Route Table이 NAT Gateway 또는 허용된 egress 경로를 가져야 한다.
- DNS가 `gms.ssafy.io`를 해석할 수 있어야 한다.
- 모델 권한 부족, 쿼터 초과, 429, 5xx를 AI 로그에서 구분한다.
- 회의 원문이 외부 모델 endpoint로 전달되므로 개인정보·민감 발언 처리 정책을 팀 정책과 맞춘다.

현재 GMS base URL은 여러 AI Python 파일에 문자열로 고정되어 있다. 다른 OpenAI 호환 공급자로 포팅하려면 해당 코드들을 변경해야 하므로, 향후 `OPENAI_BASE_URL` 환경변수로 분리하는 것이 안전하다.

### 6.4 임베딩 모델 변경 주의

현재 DB와 코드는 1536차원 벡터를 전제로 한다.

```text
public.vector(1536)
EMBEDDING_DIM=1536
```

임베딩 모델을 변경할 때는 다음을 함께 변경해야 한다.

- `EMBEDDING_MODEL`
- Python의 `EMBEDDING_DIM`
- Flyway의 `vector(1536)` 컬럼
- 기존 벡터 데이터 재생성 계획
- HNSW 인덱스 재생성

## 7. jsDelivr CDN

### 7.1 사용 목적

회의 중 브라우저 VAD가 다음 외부 자산을 jsDelivr에서 직접 내려받는다.

```text
https://cdn.jsdelivr.net/npm/@ricky0123/vad-web@0.0.30/dist/
https://cdn.jsdelivr.net/npm/onnxruntime-web@1.27.0/dist/
```

사용 파일은 Silero VAD 모델, AudioWorklet 관련 자산 및 ONNX Runtime WASM이다. 서비스 가입이나 API Key는 필요하지 않다.

### 7.2 브라우저·보안 요구사항

- 사용자의 브라우저가 `https://cdn.jsdelivr.net`에 접근할 수 있어야 한다.
- 기업망, 광고 차단기, CSP가 해당 CDN을 막으면 VAD 녹음이 시작되지 않을 수 있다.
- CSP를 추가할 경우 실제 자산 로딩 방식에 맞게 `script-src`, `connect-src`, `worker-src`를 점검한다.
- 현재 코드는 `latest`가 아니라 정확한 패키지 버전을 지정해 갑작스러운 변경 가능성을 줄인다.
- CDN 장애 시 화상회의 연결은 가능해도 VAD 기반 음성 업로드는 실패할 수 있다.

참고: [jsDelivr npm CDN](https://www.jsdelivr.com/)

### 7.3 운영 개선 선택지

외부 CDN 의존성을 제거해야 한다면 필요한 VAD/ONNX 자산을 Frontend 빌드 또는 전용 S3 Prefix에 포함하고 `baseAssetPath`, `onnxWASMBasePath`를 자사 CloudFront URL로 변경한다. 이 작업은 현재 구현에는 적용되지 않은 선택적 개선이다.

## 8. Hugging Face Hub 및 faster-whisper 모델

### 8.1 사용 목적

AI 서버는 다음 코드로 STT 모델을 로드한다.

```python
WhisperModel("large-v3-turbo", device="cpu", compute_type="int8")
```

faster-whisper는 모델 크기 이름을 전달하면 해당 CTranslate2 모델을 Hugging Face Hub에서 자동으로 내려받는다. 공개 모델 다운로드에는 현재 별도 계정이나 Token을 사용하지 않는다.

참고: [faster-whisper 모델 자동 다운로드](https://github.com/SYSTRAN/faster-whisper/blob/master/README.md#model-conversion)

### 8.2 ECS 요구사항

- AI Task에서 Hugging Face 도메인으로 HTTPS egress가 가능해야 한다.
- 모델을 저장할 충분한 ephemeral storage가 필요하다.
- 모델 캐시 경로에 쓰기 권한이 있어야 한다.
- 첫 처리 요청은 모델 다운로드와 로딩 때문에 오래 걸릴 수 있다.
- Task가 교체되고 캐시가 보존되지 않으면 모델을 다시 내려받을 수 있다.
- 공개 다운로드 rate limit 또는 Hugging Face 장애가 최초 STT 처리 실패로 이어질 수 있다.

### 8.3 운영 개선 선택지

안정적인 운영이 필요하면 다음 중 하나를 적용한다.

1. 모델을 이미지 빌드 시 내려받아 AI 이미지에 포함한다.
2. EFS 또는 영속 캐시에 모델을 보관한다.
3. 사내 S3에 승인된 모델 파일을 보관하고 시작 시 내려받는다.
4. 코드에서 모델 이름 대신 로컬 모델 디렉터리를 사용한다.

모델을 이미지에 포함하면 이미지 크기와 ECR 저장·배포 시간이 증가하므로 배포 시간과 최초 처리 지연을 비교해 결정한다.

## 9. 도메인 등록기관과 DNS 위임

### 9.1 사용 목적

현재 공개 도메인:

```text
comeettool.cloud
app.comeettool.cloud
api.comeettool.cloud
```

도메인은 외부 등록기관에서 구매하고 DNS 운영은 Route 53 Public Hosted Zone에 위임한다.

### 9.2 설정 절차

1. 도메인 등록기관 계정을 준비한다.
2. `comeettool.cloud` 도메인의 활성 상태와 만료일을 확인한다.
3. Route 53에서 동일 도메인의 Public Hosted Zone을 생성한다.
4. Hosted Zone이 발급한 NS 4개를 확인한다.
5. 등록기관의 기존 기본 Name Server를 Route 53 NS 4개로 교체한다.
6. TTL과 전파 시간을 기다린 뒤 외부 DNS에서 NS가 일치하는지 확인한다.
7. ACM 인증서 DNS 검증 레코드를 Route 53에 유지한다.

Cloud Map의 `a707-dev.local`은 VPC 내부 서비스 검색용 Private Namespace다. 외부 도메인 `comeettool.cloud`의 Public Hosted Zone과 혼동하지 않는다.

### 9.3 운영 주의사항

- 자동 갱신과 결제 수단을 확인한다.
- 도메인 등록기관 계정에 MFA를 설정한다.
- Route 53 NS 레코드를 임의로 삭제하지 않는다.
- ACM DNS 검증 CNAME을 삭제하면 향후 인증서 자동 갱신이 실패할 수 있다.
- 담당자 변경 시 등록기관 계정과 복구 수단을 인수인계한다.

## 10. Secret 저장 위치 요약

| 값 | 로컬 | 운영 | Frontend 노출 가능 여부 |
| --- | --- | --- | --- |
| AWS 관리자 인증 | AWS SSO Profile | 사용하지 않음 | 불가 |
| GitLab AWS 인증 | 사용하지 않음 | OIDC 임시 자격 증명 | 불가 |
| LiveKit URL | Backend `.env` | Secrets Manager 또는 일반 환경변수 | 입장 응답으로 전달 가능 |
| LiveKit API Key | Backend `.env` | Secrets Manager | 불가 |
| LiveKit API Secret | Backend `.env` | Secrets Manager | 절대 불가 |
| GMS API Key | AI `.env` | Secrets Manager | 절대 불가 |
| S3 Bucket 이름 | 각 서비스 `.env` | 일반 환경변수 | 필요하지 않음 |
| AWS S3 인증 | SSO/로컬 Role 권장 | ECS Task Role | 불가 |
| DB 비밀번호 | 각 서비스 `.env` | Secrets Manager | 절대 불가 |
| JWT 개인키 | Backend 로컬 key 파일 | Secrets Manager Base64 | 절대 불가 |
| Yjs 내부 Token | Backend/Yjs `.env` | Secrets Manager | 불가 |
| AI 내부 Token | Backend/AI `.env` | Secrets Manager | 불가 |

## 11. 외부 서비스 최종 검증 체크리스트

### AWS

- [ ] IAM Identity Center로 AWS CLI 로그인이 된다.
- [ ] Backend, Yjs, AI ECS 서비스가 `RUNNING`과 안정화 완료 상태다.
- [ ] Migration Task가 exit code 0으로 완료된다.
- [ ] RDS `comeettool` DB와 pgvector 확장이 정상이다.
- [ ] Backend와 AI Task Role이 필요한 S3 Prefix만 접근한다.
- [ ] Secrets Manager 값이 Task Definition에 올바른 JSON key로 연결되어 있다.
- [ ] Secret 변경 후 새 Task가 배포되었다.
- [ ] CloudWatch Log Group이 존재한다.

### GitLab

- [ ] `main`이 보호 브랜치다.
- [ ] Runner가 online 상태다.
- [ ] OIDC 검증 Job이 AWS STS 호출에 성공한다.
- [ ] AWS 장기 Access Key가 GitLab 변수에 없다.
- [ ] Production Pipeline이 `main`에서만 실행된다.

### LiveKit

- [ ] Project URL이 `wss://` 형식이다.
- [ ] API Key와 Secret이 Backend에만 주입된다.
- [ ] Webhook URL과 Signing API Key가 등록되어 있다.
- [ ] Test Event가 Backend 2xx 응답을 받는다.
- [ ] 두 사용자 간 영상·음성·화면 공유가 동작한다.

### SSAFY GMS와 AI 모델

- [ ] GMS Key가 필요한 모델과 임베딩 권한을 가진다.
- [ ] AI Task에서 GMS endpoint HTTPS 연결이 된다.
- [ ] `gpt-5` 구조화 결과를 받을 수 있다.
- [ ] `text-embedding-3-small` 결과가 1536차원이다.
- [ ] Whisper 모델 최초 다운로드와 로딩이 완료된다.
- [ ] AI Task storage와 메모리가 모델 실행에 충분하다.

### 브라우저 외부 자산과 도메인

- [ ] 브라우저에서 jsDelivr VAD/ONNX 자산을 내려받는다.
- [ ] 마이크 발화 시 VAD 세그먼트가 생성된다.
- [ ] 외부 DNS의 NS가 Route 53 Hosted Zone과 일치한다.
- [ ] `app.comeettool.cloud`, `api.comeettool.cloud` TLS 인증서가 유효하다.
- [ ] 도메인 자동 갱신과 결제 수단이 설정되어 있다.

## 12. 공식 참고자료

- [AWS CLI IAM Identity Center 인증](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sso.html)
- [AWS ECS Task Execution Role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html)
- [AWS ECS Task Role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html)
- [AWS ECS Secrets Manager 환경변수 주입](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html)
- [AWS S3 Presigned URL](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)
- [GitLab AWS OIDC](https://docs.gitlab.com/ci/cloud_services/aws/)
- [LiveKit Webhooks](https://docs.livekit.io/intro/basics/rooms-participants-tracks/webhooks-events/)
- [LiveKit CLI와 Cloud Project](https://docs.livekit.io/reference/developer-tools/livekit-cli/)
- [jsDelivr](https://www.jsdelivr.com/)
- [faster-whisper](https://github.com/SYSTRAN/faster-whisper)
