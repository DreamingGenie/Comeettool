# AI 회의록 데모 파이프라인

음성 파일 → STT+화자분리 → LLM 구조화 추출 → 고정 양식 MD 회의록

## 구조

```
transcribe.py   STT(faster-whisper) + 화자분리(pyannote.audio)
schema.py       회의록 구조화 데이터 스키마 (Pydantic)
extract.py      전사 결과 -> LLM 구조화 추출 (OpenAI API, Structured Outputs로 스키마 강제)
render.py       구조화 데이터 -> MD 렌더링 (Jinja2)
template.md.j2  회의록 양식 (여기가 "양식 통일"의 실체)
main.py         전체 파이프라인 실행
```

## 설치

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

pyannote.audio 4.0은 오디오 디코딩에 ffmpeg(shared 빌드)이 필요합니다. Windows에서는:

```bash
winget install --id Gyan.FFmpeg.Shared -e
```

(`Gyan.FFmpeg`처럼 정적 빌드만 설치하면 DLL이 없어서 pyannote가 오디오를 못 읽습니다. 반드시 `.Shared` 버전을 설치하세요.)

## 필요한 키 2개

프로젝트 루트에 `.env` 파일을 만들고 아래 두 개를 채우세요.

```
HF_TOKEN=hf_xxxxxxxxxxxx
OPENAI_API_KEY=sk-xxxxxxxxxxxx
```

1. **HF_TOKEN**: https://huggingface.co/settings/tokens 에서 발급 (무료).
   추가로 https://huggingface.co/pyannote/speaker-diarization-community-1 페이지에서
   라이선스 동의(Agree) 버튼을 눌러야 실제로 다운로드가 됩니다.
2. **OPENAI_API_KEY**: OpenAI Platform(https://platform.openai.com/api-keys)에서 발급.

## 실행

```bash
python main.py path/to/your_demo_audio.wav
```

- 첫 실행 시 Whisper 모델과 pyannote 모델을 다운로드하므로 몇 분 걸릴 수 있습니다.
- 결과물은 `output/` 폴더에 3개 파일로 생성됩니다:
  - `transcript.json`: STT+화자분리 원본 (화자 라벨, 타임스탬프, 텍스트)
  - `minutes.json`: LLM이 추출한 구조화 회의록 데이터
  - `minutes.md`: 최종 회의록 문서 (사람이 읽는 결과물)

## GPU로 돌리고 싶다면

기본값은 `STT_MODEL_SIZE=large-v3-turbo`, `DEVICE=cpu`, `COMPUTE_TYPE=int8`입니다.
GPU를 쓰려면 `.env`에 아래 항목을 추가하세요.

```
DEVICE=cuda
COMPUTE_TYPE=float16
```

다만 Windows에서 `pip install -r requirements.txt`로 설치되는 `torch`는 CPU 전용 빌드이므로,
GPU를 실제로 쓰려면 CUDA 지원 빌드로 다시 설치해야 합니다.

```bash
pip install torch==2.13.0+cu130 torchaudio==2.11.0+cu130 --index-url https://download.pytorch.org/whl/cu130
```

설치 후 아래 명령으로 GPU 인식을 확인할 수 있습니다.

```bash
python -c "import torch; print(torch.cuda.is_available())"
```

## 다음 실험으로 넘어갈 때

이 데모는 이전에 정리한 로드맵의 **0단계(RAG 없는 베이스라인)**에 해당합니다.
- 화자 라벨(SPEAKER_00 등)을 실명으로 매핑하는 기능은 아직 없습니다
  (회의 참석자 명단과 매칭하는 UI/로직 추가 필요).
- 30분 단위 청킹 + 롤링 요약, 이전 회의록 컨텍스트 주입, 벡터DB 연동은
  이 데모에 아직 없습니다. 이 파이프라인이 잘 도는 것을 먼저 확인한 뒤
  단계적으로 추가하시면 됩니다.
