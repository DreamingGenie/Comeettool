# AI 회의 분석 파이프라인

LiveKit egress 회의 폴더를 입력으로 받아 두 가지 산출물을 만드는 배치 파이프라인이다.

1. **MVP: 회의록** — `main.py` → STT → RAG(과거 회의록 참고) → LLM 구조화 추출 → 고정 양식 회의록 MD
2. **Sub: Facilitator 보고서** — `facilitator_report.py` → (전사 재사용) → 참여 균형 집계 → RAG(과거 보고서 참고) → LLM 구조화 추출 → 회의 진행 품질 평가 MD

두 파이프라인은 서로 독립적으로 실행되며, 같은 `output/<회의ID>/` 폴더에 결과물을 함께 저장한다(파일명으로 구분).

## 구조

### 공통

```
transcribe.py   STT(faster-whisper). 화자 구분은 LiveKit egress가 참가자별로 트랙을
                이미 분리해서 내보내므로 별도 화자분리 모델 없이 폴더명을 화자 라벨로 사용
schema.py       회의록 구조화 데이터 스키마 (Pydantic, TranscriptSegment 등 공용 타입 포함)
```

### MVP: 회의록 (`main.py`)

```
rag.py      과거 회의록(meeting_history 테이블)을 PostgreSQL(pgvector)에 저장/검색
extract.py  전사 + 관련 과거 회의록 맥락 -> LLM 구조화 추출 (OpenAI Structured Outputs)
render.py   구조화 데이터 -> MD 렌더링 (Jinja2)
template.md.j2  회의록 양식
```

### Sub: Facilitator 보고서 (`facilitator_report.py`)

```
participation.py       참여 균형(발화 횟수/시간/비율) 코드 계산 (LLM 의존 없음)
rag_facilitator.py      과거 Facilitator 보고서(facilitator_report_history 테이블)를
                        PostgreSQL(pgvector)에 저장/검색 (rag.py와 별도 테이블)
schema_facilitator.py   FacilitatorReport 구조화 스키마 (Pydantic)
extract_facilitator.py  전사 + 참여 균형 통계 + 과거 보고서 맥락 -> LLM 구조화 추출
render_facilitator.py   구조화 데이터 -> MD 렌더링 (Jinja2)
facilitator_report.md.j2  보고서 양식
```

## 입력 폴더 형식

`meeting_dir`은 LiveKit egress 출력 폴더로, 아래 구조를 따라야 한다.

```
<meeting_dir>/
  participants/
    <participant_id>/
      EG_*.json   트랙 메타데이터 (started_at, track_id)
      TR_*.ogg    해당 트랙 오디오
    ...
```

화자 라벨은 참가자 폴더명(`participant_id`)을 그대로 사용하므로 실명이 아니다.

## 설치

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## 환경변수 (`.env`)

프로젝트 루트에 `.env` 파일을 만들고 아래 두 개를 채운다.

```
OPENAI_API_KEY=sk-xxxxxxxxxxxx
DATABASE_URL=postgresql://user:pass@host:5432/dbname
```

- **OPENAI_API_KEY**: LLM 구조화 추출과 임베딩(RAG)에 공용으로 쓰인다. 코드에서
  OpenAI 엔드포인트가 아니라 SSAFY GMS 게이트웨이(`https://gms.ssafy.io/gmsapi/api.openai.com/v1`)를
  `base_url`로 사용하므로, 여기 넣는 키는 GMS에서 발급받은 키여야 한다.
- **DATABASE_URL**: libpq 연결 문자열. PostgreSQL 15+ + pgvector 확장이 필요하다
  (`rag.py`/`rag_facilitator.py`가 `CREATE EXTENSION IF NOT EXISTS vector`와 테이블/HNSW
  인덱스를 처음 연결 시 자동으로 만든다).

로컬에서 DB를 띄우려면 저장소에 포함된 `docker-compose.yml`을 사용하면 된다.

```bash
docker compose up -d
# DATABASE_URL=postgresql://postgres:postgres@localhost:5432/meetings
```

## 실행 — MVP: 회의록

```bash
python main.py path/to/meeting_dir
```

- 첫 실행 시 Whisper 모델을 다운로드하므로 몇 분 걸릴 수 있다.
- 결과물은 `output/<meeting_dir 이름>/`에 3개 파일로 생성된다.
  - `meeting_report_transcript.json`: STT 원본 결과 (화자 라벨, 타임스탬프, 텍스트)
  - `meeting_report.json`: LLM이 추출한 구조화 회의록 데이터
  - `meeting_report.md`: 최종 회의록 문서 (사람이 읽는 결과물)
- 처리 순서: STT → 임베딩 유사도 기반 관련 과거 회의록 top-k 검색(RAG) → LLM 추출 →
  MD 렌더링 → 이번 회의록을 히스토리(`meeting_history` 테이블)에 저장.

## 실행 — Sub: Facilitator 보고서

```bash
python facilitator_report.py path/to/meeting_dir
```

- `output/<meeting_dir 이름>/meeting_report_transcript.json`이 이미 있으면(=`main.py`를
  먼저 돌렸다면) STT를 다시 하지 않고 그 전사를 재사용한다. 없으면 새로 STT를 수행하고
  같은 경로에 저장한다.
- 결과물은 `output/<meeting_dir 이름>/`에 아래 2개 파일이 추가로 생성된다(전사 파일은 공용).
  - `facilitator_report.json`: LLM이 추출한 구조화 회의 품질 평가 데이터
  - `facilitator_report.md`: 최종 회의 품질 평가 보고서
- 참여 균형(발화 횟수/시간/비율)은 `participation.py`가 전사로부터 코드로 직접 계산하며
  LLM이 이 수치를 추정하지 않는다. 진행 품질 평가(아젠다 명확성/시간 관리/발언 기회
  분배/의사결정 프로세스/논의 집중도)와 잘된 점/개선 필요 사항/결정 프로세스 점검에는
  근거 타임스탬프가 항상 포함된다.
- RAG 히스토리는 `rag.py`의 `meeting_history`와 별도인 `facilitator_report_history`
  테이블을 사용한다.

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

## 과거 산출물 백필

`output/*/meeting_report.md`가 이미 쌓여 있는데 `meeting_history` 테이블이 비어 있다면
(예: DB를 새로 띄운 경우), `rag.py`를 단독 실행해 백필할 수 있다.

```bash
python rag.py
```

각 폴더의 파일 수정 시각을 `created_at`으로 써서 실제 생성 순서를 보존한다.

## 더 알아보기

- MVP 파이프라인: `docs/PRD(MVP).md`, `docs/SPEC(MVP).md`
- Sub(Facilitator) 파이프라인: `docs/PRD(Sub).md`, `docs/SPEC(Sub).md`, `docs/PHASES(Sub)/`

문서가 최신 구현을 반영 못 할 수 있으니, 작업 전 위 코드로 재확인할 것.
