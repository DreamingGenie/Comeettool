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
| 웹 클라이언트 | Vue | 확정 | |
| 실시간 문서 편집 | CRDT / Yjs | 확정 | 동시 편집 델타 병합 (DOC-02) |

---

## 3. 백엔드

| 구분 | 기술 | 상태 | 비고 |
| --- | --- | --- | --- |
| API 서버 | Spring Boot | 확정 | AUTH·SPACE·MEET·DOC 등 핵심 도메인 |
| API Gateway | Nginx (리버스 프록시) | 확정 | TLS 종료·경로 라우팅. 인증은 Spring(JWT 필터)에서 처리 |
| SFU 미디어 서버 | LiveKit | 후보 | 실시간 영상·음성 중계, 참여자별 트랙 분리 (RTC). Kurento 미채택, LiveKit 유력(미확정) — 아래 비고 참조 |
| 관계형 DB | PostgreSQL | 확정 | 핵심 도메인 저장. JSONB·배열·UUID 등 활용 (DDL은 [database-schema.md](database-schema.md)) |
| 캐시 / 세션 | Redis | 확정 | 토큰·세션, 실시간 상태, WebSocket pub/sub |
| 오브젝트 스토리지 | AWS S3 | 확정 | 오디오·이미지·녹화·첨부 (DB엔 메타/URL만) |
| 메시지 큐 | AWS SQS | 확정 | 회의록 생성 등 비동기 작업 처리 (AI-10 상태 연동) |

> **DB 선택.** 핵심 도메인 DB로 **PostgreSQL**을 사용한다(초기 MySQL 후 마이그레이션하려던 계획은 폐기, 처음부터 PostgreSQL). ERD·DDL은 [database-schema.md](database-schema.md) 참조.

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
- 화자 분리(diarization) 및 타임스탬프 분리 모두 가능.
- 회의 종료 후 일괄(배치) 처리 방식 (결정 3).

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
