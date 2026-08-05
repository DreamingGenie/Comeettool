# 서비스 아키텍처 (Service Architecture)

본 문서는 [기능 명세서](functional-specification.md)를 기반으로 팀이 결정한 기술 스택과 시스템 구성을 정리한다. 시스템 구성 다이어그램은 별도로 제작·관리한다.

## 상태 범례
| 표기 | 의미 |
| --- | --- |
| 확정 | 사용 기술이 결정됨 |
| 후보 | 복수 후보 중 선정 예정 (아래 비고 참조) |

---

## 1. 아키텍처 개요

시스템은 다음 5개 계층으로 구성된다.

| 계층 | 역할 |
| --- | --- |
| 프론트엔드 | 사용자 웹 클라이언트 |
| 백엔드 | 핵심 도메인 API, 실시간 통신, 미디어 중계 |
| AI | 음성 전사·회의록 생성·요약 파이프라인 |
| 인프라/협업 | 저장소, 클라우드, 배포, 버전관리 |
| 모니터링 | 메트릭·로그 수집 및 관측 |

> 진입점(API Gateway)은 Nginx 리버스 프록시로 구성한다. 외부 트래픽의 TLS 종료와 경로 기반 라우팅(`/api`, `/ws`, `/ai`, 정적 파일)을 담당하며, **WebRTC 미디어 트래픽은 Nginx를 우회해 SFU 서버와 직접 연결**된다.

---

## 2. 프론트엔드

| 구분 | 기술 | 상태 | 비고 |
| --- | --- | --- | --- |
| 웹 클라이언트 | Vue 3.5.40 | 확정 | |
| 문서 에디터 | TipTap | 확정 | Markdown 문서 편집 UI (DOC-01·02) |
| CRDT 라이브러리 | Yjs | 확정 | 동시 편집 델타 병합 (DOC-02) |
| 실시간 동기화 | Hocuspocus | 확정 | Yjs 문서 동기화 프로토콜 (DOC-02·03·04) |
| CRDT 협업 서버 | Express | 확정 | Hocuspocus 백엔드(WebSocket)를 구동하는 Node 서버 |

---

## 3. 백엔드

| 구분 | 기술 | 상태 | 비고 |
| --- | --- | --- | --- |
| API 서버 | Spring Boot 4.1.0 | 확정 | AUTH·SPACE·MEET·DOC 등 핵심 도메인 |
| 언어 / 런타임 | Java 21 | 확정 | Gradle 빌드. (Spring Initializr가 Boot 3.x 미지원(>=4.0.0)이라 4.x 채택) |
| API Gateway | Nginx (리버스 프록시) | 확정 | TLS 종료·경로 라우팅. 인증은 Spring(JWT 필터)에서 처리 |
| SFU 미디어 서버 | LiveKit Cloud | 확정 | 실시간 영상·음성 중계, 참여자별 트랙 분리 (RTC). 관리형(Cloud) 사용으로 확정 |
| 관계형 DB | PostgreSQL 17 | 확정 | 핵심 도메인 저장. JSONB·배열·UUID 등 활용 (DDL은 [database-schema.md](database-schema.md)) |
| 캐시 / 세션 | Redis | 확정 | 토큰·세션, 실시간 상태, WebSocket pub/sub |
| 오브젝트 스토리지 | AWS S3 | 확정 | 오디오·이미지·녹화·첨부 (DB엔 메타/URL만) |
| 메시지 큐 | AWS SQS | 후보 | 회의록 생성 등 비동기 작업 처리 (AI-11 상태 연동). 메시지 큐 도입 여부·방식 미결(§7 참조) |

> **DB 선택.** 핵심 도메인 DB로 **PostgreSQL**을 사용한다(초기 MySQL 후 마이그레이션하려던 계획은 폐기, 처음부터 PostgreSQL). ERD·DDL은 [database-schema.md](database-schema.md) 참조.

### 인증·권한 흐름 (AUTH / 권한 관리)

**Spring API 서버가 인증·인가의 단일 진실원천(Single Source of Truth)**이다. Nginx는 TLS 종료·라우팅만 담당하고 인증은 하지 않는다.

- **일반 API** — 클라이언트가 앱 JWT를 제시하면 Spring Security JWT 필터가 검증한다. Access/Refresh Token과 세션 상태는 Redis에 보관한다(AUTH-02·03·04).
- **미디어 서버(LiveKit)** — 클라이언트가 회의 입장을 요청하면 Spring이 앱 JWT를 검증하고 참여 권한(`meeting_participants`)을 확인한 뒤, **권한(grant)이 담긴 LiveKit 전용 접속 토큰(JWT, API Key/Secret 서명)을 발급**한다. 클라이언트는 이 토큰으로 SFU에 입장하며, LiveKit은 서명만 검증한다.
- **협업 서버(Hocuspocus)** — 클라이언트가 WebSocket 연결 시 앱 JWT를 전달하고, 협업 서버가 `onAuthenticate`에서 검증해 문서 단위 열람/편집 권한(DOC-06)을 부여한다.

**즉시 권한 박탈(강퇴·권한 변경 시).** stateless JWT는 만료 전까지 유효하므로 접속 중 세션을 끊으려면 **능동적 축출**이 필요하다. 권한 변경이 발생하면 Spring이 이를 원천에서 무효화하고 **Redis Pub/Sub로 축출 이벤트를 발행**한다.
- LiveKit — Spring이 서버 API `RemoveParticipant`(강퇴) / `UpdateParticipant`(권한 회수)를 호출해 즉시 방에서 제거한다.
- 협업 서버 — Redis 이벤트를 구독한 협업 서버가 해당 WebSocket 연결을 종료하거나 read-only로 강등한다.
- 토큰 TTL은 짧게 두고 Refresh로 갱신하여, 축출 이벤트를 놓친 경우에도 짧은 만료로 방어하는 다층 구조로 설계한다.

---

## 4. AI

| 구분 | 기술 | 상태 | 비고 |
| --- | --- | --- | --- |
| 생성형 AI | GPT-4 / Gemma / Sonnet5 | 후보 | GMS-key 사용. 주력 모델 선정 예정 |
| 음성 → 텍스트 (STT) | Google Cloud STT / pyannoteAI / OpenAI Whisper API | 후보 | 유료. 화자 분리·타임스탬프 지원. 아래 비고 참조 |
| Vector DB | ChromaDB / Milvus / FAISS | 후보 | 임베딩 저장·검색. 하나로 확정 예정 |
| 임베딩 모델 | text-embedding-ada-002 (OpenAI) | 후보 | 신규 모델(text-embedding-3-small/large) 검토 권장 |
| 파이프라인 오케스트레이션 | LangGraph | 확정 | STT→요약→품질검증(최대 3회 재작성, 결정 9) 흐름 설계 |

### STT 비고
- 유료이며, 가격이 맞지 않을 경우 **GMS-key 이중 사용 구조**를 설계할 예정.
- **대안: 자체 GPU 서버 호스팅** — 오픈소스 모델(Whisper large-v3, pyannote.audio)을 직접 구동해 무료 추출하는 방식도 검토(성능·비용 비교 후 결정).
- 화자 분리(diarization) 및 타임스탬프 분리 모두 가능.
- 회의 종료 후 일괄(배치) 처리 방식 (결정 3).

### 회의록 파이프라인 트리거 (킥오프)

미디어 서버(LiveKit)는 회의 종료를 **알리고 녹음을 산출**할 뿐, STT·회의록 파이프라인을 직접 실행하지 않는다. 파이프라인 **킥오프 주체는 Spring**이다.

- **종료 감지** — `MEET-05 회의 종료`(호스트 종료) 또는 **LiveKit Webhook `room_finished`**(클라이언트 이탈에도 견고, 권장).
- **순서 주의** — STT는 녹음이 확정된 뒤에만 실행 가능하므로, 실제 킥오프 시점은 "회의 종료 즉시"가 아니라 **녹음 파일 저장 완료 시점**(LiveKit Egress 완료)이 정확하다.
- **Spring이 할 일** — 회의 상태 변경(`진행중→처리중`), `processing_jobs` 생성, `meeting_participants` 매핑 준비 후 LangGraph 파이프라인 호출.
- **큐(SQS) 유무** — SQS는 트리거가 아니라 **비동기 버퍼(pull 기반)**다. 도입 시 `Spring→SQS→AI 워커`, 미도입 시 `Spring→LangGraph 직접 호출`이며 재시도·상태 추적은 `processing_jobs`로 처리한다(§7 미결 항목).

### STT 출력 데이터 예시 (JSON 저장)
```json
{
  "speaker": "화자1",
  "start": "00:01:23",
  "end": "00:01:29",
  "text": "그럼 다음 스프린트는 언제 시작하죠?"
}
```

---

## 5. 인프라 / 협업

| 구분 | 기술 | 상태 | 비고 |
| --- | --- | --- | --- |
| 버전 관리 | GitLab | 확정 | |
| CI/CD | GitLab Runner + Docker | 확정 | |
| 클라우드 | AWS | 확정 | |

---

## 6. 모니터링 / 관측성

§9 비기능 요구사항(접속 실패·미디어 품질·AI 처리 실패 로깅)을 충족하기 위한 관측 구성.

| 구분 | 기술 | 상태 | 비고 |
| --- | --- | --- | --- |
| 메트릭 수집 | Prometheus | 확정 | 요청 수·응답 시간·리소스 등 시계열 지표 (Spring Actuator/Micrometer 연동) |
| 시각화 / 대시보드 | Grafana | 확정 | 메트릭·로그 통합 대시보드 |
| 로그 수집 | Grafana Loki | 확정 | Prometheus는 메트릭 전용이므로, 텍스트 로그는 Loki 등 별도 저장소 필요 |


---

## 7. 남은 결정 사항

문서화 시점 기준, 확정이 필요한 항목:

1. AI 관련 DB (Vector DB) — ChromaDB / Milvus / FAISS 중 택1 (FAISS는 라이브러리 성격이라 별도 검토)
2. 생성형 AI 주력 모델 선정 (GPT-4 / Gemma / Sonnet5)
3. STT 서비스 선정 및 GMS-key 이중화 구조 확정
4. 임베딩 모델 버전 (ada-002 → text-embedding-3 검토)
5. 로그 저장소 (Loki 등) 추가 여부
6. **SFU 미디어 서버 최종 확정** — Kurento 미채택, LiveKit 유력. mediasoup/Janus 대비 검토 후 확정
7. LiveKit 기술 검증(PoC) — 참여자별 오디오 트랙 분리 실증 (AI 회의록 전제, 기능명세 §7)
