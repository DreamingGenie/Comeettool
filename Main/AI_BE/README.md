# AI 회의 분석 서버

회의가 끝나면 녹음이 S3에 올라가고, 그 직후 이 서버가 API로 호출되어 두 가지
산출물을 만들어 PostgreSQL에 저장하는 FastAPI 서비스다.

1. **회의록** — STT → RAG(과거 회의록 참고) → LLM 구조화 추출 → 고정 양식 회의록
2. **Facilitator 보고서** — (전사 재사용) → 참여 균형 집계 → RAG(과거 보고서 참고) → LLM 구조화 추출 → 회의 진행 품질 평가

한 번의 처리 요청으로 두 산출물을 모두 만들어 각각의 테이블에 저장한다
(`meeting_minutes`, `facilitator_reports`).

## 구조

```
app/
  main.py                     FastAPI 앱 진입점
  core/config.py               환경변수 설정 (.env)
  api/routes/meetings.py       처리 트리거 + 결과 조회 API
  db/
    session.py                 SQLAlchemy 엔진/세션
    models.py                  ProcessingJob, MeetingMinutesRecord, FacilitatorReportRecord
  schemas/meeting.py           API 요청/응답 스키마
  services/
    s3_client.py                S3에서 회의 녹음 다운로드
    pipeline_service.py         파이프라인 오케스트레이션 (아래 pipeline/ 재사용) -> DB 저장
  workers/processor.py         BackgroundTasks로 실행되는 실제 처리 (job 상태 갱신)
  pipeline/                    STT/RAG/LLM 추출/렌더링 로직
    common/                      회의록/Facilitator 보고서 공용
      schema.py                   TranscriptSegment
      text.py                     전사 포맷 유틸 (format_transcript, seconds_to_hhmmss)
      transcribe.py                STT(faster-whisper). 화자 구분은 참가자별로 이미 분리된
                                   트랙을 그대로 화자 라벨로 사용
    minutes/                     회의록 전용
      schema.py                   MeetingMinutes 등 구조화 스키마 (Pydantic)
      rag.py                      과거 회의록을 PostgreSQL(pgvector)에 저장/검색
      extract.py                  전사 + RAG 맥락 -> LLM 구조화 추출 (OpenAI Structured Outputs)
      render.py                   구조화 데이터 -> MD 렌더링 (Jinja2, 히스토리 저장용)
      template.md.j2               MD 양식
    facilitator/                 Facilitator 보고서 전용
      schema.py                   FacilitatorReport 등 구조화 스키마 (Pydantic)
      rag.py                      과거 보고서를 PostgreSQL(pgvector)에 저장/검색 (minutes와 별도 테이블)
      participation.py            참여 균형(발화 횟수/시간/비율) 코드 계산 (LLM 의존 없음)
      extract.py                  전사 + 참여 통계 + RAG 맥락 -> LLM 구조화 추출
      render.py                   구조화 데이터 -> MD 렌더링 (Jinja2)
      facilitator_report.md.j2     MD 양식
    prompts/                     LLM 시스템 프롬프트 (코드와 분리, .md로 읽어들임)
      minutes_system_prompt.md
      facilitator_system_prompt.md
```

## 처리 흐름

1. 클라이언트가 `POST /meetings/{meeting_id}/process` 호출 (회의 종료 + S3 업로드 완료 후).
2. 서버는 `ProcessingJob` 레코드를 만들고 즉시 202로 `job_id`를 반환한다(비동기 처리).
3. 백그라운드에서: S3에서 `conferences/{meeting_id}/` 전체 다운로드 → STT → RAG →
   LLM 구조화 추출(회의록 + Facilitator 보고서) → 결과를 각 테이블에 저장 → job 상태를
   `done`/`failed`로 갱신.
4. `GET /meetings/jobs/{job_id}`로 처리 상태, `GET /meetings/{meeting_id}/minutes` /
   `GET /meetings/{meeting_id}/facilitator-report`로 결과를 조회한다.

> **미완성**: `app/services/pipeline_service.py`의 `_build_legacy_meeting_dir`가
> 아직 스텁이다. S3의 `conferences/{meetingId}/participants/{pid}/sessions/{sid}/audio/segment-*.ogg`
> 세그먼트들을 `transcribe.py`가 기대하는 형식(참가자당 오디오 1개)으로 합치는 로직을
> `metadata/segment-*.json` 스키마 확정 후 구현해야 한다.

## 설치

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## 환경변수 (`.env`)

```
OPENAI_API_KEY=sk-xxxxxxxxxxxx
DATABASE_URL=postgresql://user:pass@host:5432/dbname

S3_BUCKET_NAME=my-bucket
AWS_REGION=ap-northeast-2
# EC2 IAM 역할로 인증 가능하면 아래 두 개는 비워둔다
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
```

- **OPENAI_API_KEY**: LLM 구조화 추출과 임베딩(RAG)에 공용으로 쓰인다. 코드에서
  OpenAI 엔드포인트가 아니라 SSAFY GMS 게이트웨이(`https://gms.ssafy.io/gmsapi/api.openai.com/v1`)를
  `base_url`로 사용하므로, 여기 넣는 키는 GMS에서 발급받은 키여야 한다.
- **DATABASE_URL**: libpq 연결 문자열. PostgreSQL 15+ + pgvector 확장이 필요하다
  (`rag.py`/`rag_facilitator.py`가 `CREATE EXTENSION IF NOT EXISTS vector`와 RAG용
  테이블/HNSW 인덱스를, `app.main`이 시작 시 서비스용 테이블(`processing_jobs`,
  `meeting_minutes`, `facilitator_reports`)을 자동으로 만든다). 같은 DB 안에 pgvector
  테이블과 일반 테이블이 함께 존재한다 - 별도 벡터 DB가 아니다.
- **S3_BUCKET_NAME**: 회의 녹음이 `conferences/{meetingId}/participants/{participantId}/sessions/{sessionId}/...`
  형태로 저장되는 버킷.

로컬에서 DB를 띄우려면 저장소에 포함된 `docker-compose.yml`을 사용하면 된다.

```bash
docker compose up -d
# DATABASE_URL=postgresql://postgres:postgres@localhost:5432/meetings
```

## 실행

```bash
uvicorn app.main:app --reload
```

- 첫 처리 요청 시 Whisper 모델을 다운로드하므로 몇 분 걸릴 수 있다.
- `POST /meetings/{meeting_id}/process` 호출 예시:
  ```bash
  curl -X POST http://localhost:8000/meetings/{meeting_id}/process
  ```

## GPU로 돌리고 싶다면

기본값은 `STT_MODEL_SIZE=large-v3-turbo`, `DEVICE=cpu`, `COMPUTE_TYPE=int8`이다.
GPU를 쓰려면 `.env`에 아래 항목을 추가한다.

```
DEVICE=cuda
COMPUTE_TYPE=float16
```

다만 `pip install -r requirements.txt`로 설치되는 `torch`/`faster-whisper` 의존성은
플랫폼에 따라 CPU 전용 빌드일 수 있으므로, GPU를 실제로 쓰려면 CUDA 지원 빌드로
다시 설치해야 할 수 있다.

## 더 알아보기

- MVP 파이프라인: `docs/PRD(MVP).md`, `docs/SPEC(MVP).md`
- Sub(Facilitator) 파이프라인: `docs/PRD(Sub).md`, `docs/SPEC(Sub).md`, `docs/PHASES(Sub)/`

문서가 최신 구현을 반영 못 할 수 있으니, 작업 전 위 코드로 재확인할 것.
