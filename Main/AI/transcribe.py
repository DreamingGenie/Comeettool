"""
LiveKit egress 회의 폴더 -> 텍스트 + 화자 + 타임스탬프

- STT: Whisper large-v3-turbo, 로컬 실행, API 키 불필요
- 화자 구분: LiveKit egress가 참가자(트랙)별로 오디오 파일을 이미 분리해서 내보내므로
  별도 화자분리 모델 없이 participants/<participant_id>/ 폴더명을 화자 라벨로 사용한다.

DEVICE="cpu", COMPUTE_TYPE="int8"을 기본값으로 둡니다.
GPU가 있으면 환경변수로 DEVICE="cuda", COMPUTE_TYPE="float16"으로 바꾸세요.
"""

import json
import os
from dataclasses import dataclass
from pathlib import Path

from faster_whisper import WhisperModel

from schema import TranscriptSegment

STT_MODEL_SIZE = os.environ.get("STT_MODEL_SIZE", "large-v3-turbo")
DEVICE = os.environ.get("DEVICE", "cpu")  # GPU가 있으면 "cuda"로 변경
COMPUTE_TYPE = os.environ.get("COMPUTE_TYPE", "int8")  # GPU면 "float16" 권장


@dataclass
class RawSTTSegment:
    start: float
    end: float
    text: str


def run_stt(audio_path: str) -> list[RawSTTSegment]:
    """faster-whisper로 음성 -> 텍스트 (화자 구분 없음, 시간 구간만 있음)"""
    print(f"[STT] 모델 로딩 중... (size={STT_MODEL_SIZE}, device={DEVICE})")
    model = WhisperModel(STT_MODEL_SIZE, device=DEVICE, compute_type=COMPUTE_TYPE)

    print(f"[STT] 전사 시작: {audio_path}")
    segments, info = model.transcribe(audio_path, language="ko", vad_filter=True)

    result = []
    for seg in segments:
        result.append(RawSTTSegment(start=seg.start, end=seg.end, text=seg.text.strip()))
        print(f"  [{seg.start:6.1f}s -> {seg.end:6.1f}s] {seg.text.strip()}")

    print(f"[STT] 완료. 총 {len(result)}개 세그먼트, 감지 언어: {info.language}")
    return result


@dataclass
class _EgressTrack:
    participant_id: str
    started_at: int  # unix epoch, 나노초
    ogg_path: Path


def _load_egress_tracks(meeting_dir: str) -> list[_EgressTrack]:
    """<meeting_dir>/participants/<participant_id>/EG_*.json을 모두 읽어 트랙 목록을 만든다.

    각 EG_*.json은 같은 폴더의 TR_<track_id>.ogg 하나에 대응하는 메타데이터다.
    """
    participants_dir = Path(meeting_dir) / "participants"
    egress_paths = sorted(participants_dir.glob("*/EG_*.json"))
    if not egress_paths:
        raise RuntimeError(f"{participants_dir}에서 EG_*.json을 찾을 수 없습니다.")

    tracks = []
    for eg_path in egress_paths:
        meta = json.loads(eg_path.read_text(encoding="utf-8"))
        tracks.append(
            _EgressTrack(
                participant_id=eg_path.parent.name,
                started_at=meta["started_at"],
                ogg_path=eg_path.parent / f"{meta['track_id']}.ogg",
            )
        )
    return tracks


def transcribe_meeting(meeting_dir: str) -> list[TranscriptSegment]:
    """회의 폴더의 모든 참가자 트랙에 STT를 돌리고, 절대 시각 기준으로 합친 transcript를 반환.

    화자 라벨은 participant_id(폴더명)를 그대로 쓴다.
    """
    tracks = _load_egress_tracks(meeting_dir)
    meeting_start = min(track.started_at for track in tracks)

    merged = []
    for track in tracks:
        offset = (track.started_at - meeting_start) / 1_000_000_000
        for seg in run_stt(str(track.ogg_path)):
            merged.append(
                TranscriptSegment(
                    speaker=track.participant_id,
                    start=offset + seg.start,
                    end=offset + seg.end,
                    text=seg.text,
                )
            )

    merged.sort(key=lambda seg: seg.start)
    return merged
