"""추가 단위 테스트 (test.py의 인증 스모크 테스트를 보완).

test.py는 서버를 띄운 뒤 인증 레이어(X-Internal-Token)만 검증한다. 이 파일은
서버/외부 의존(S3·OpenAI·DB) 없이 파이프라인의 순수 로직을 pytest로 검증한다.

  - SegmentMeta: 미디어/BE가 올리는 세그먼트 메타데이터 JSON(camelCase) 파싱 —
    BE(VadSegmentMetadataDto) ↔ AI(SegmentMeta) 계약 검증
  - participation.py: 화자별 참여 통계 계산(LLM 의존 없는 순수 계산)
  - common/text.py: 전사 포맷 유틸
  - _build_legacy_meeting_dir: S3 다운로드 구조 -> transcribe.py 입력 구조 변환
    (ffmpeg 병합은 monkeypatch로 대체하고, 정렬·간격·EG json 생성 로직만 검증)

실행:
  pytest pytest2.py -v
"""

from __future__ import annotations

import json
import os

import pytest

# app.services.* 는 import 시점에 get_settings()가 필수 환경변수를 요구하므로,
# 외부 자원에 실제로 연결하지 않는 더미 값을 import 전에 채워둔다.
os.environ.setdefault("OPENAI_API_KEY", "sk-dummy-for-tests")
os.environ.setdefault("DATABASE_URL", "postgresql://user:pass@localhost:5432/dummy")
os.environ.setdefault("S3_BUCKET_NAME", "dummy-bucket")
os.environ.setdefault("AI_INTERNAL_TOKEN", "dummy-token")

from app.pipeline.common.schema import TranscriptSegment
from app.pipeline.common.text import format_transcript, seconds_to_hhmmss
from app.pipeline.facilitator.participation import (
    compute_participation_stats,
    get_meeting_duration,
    get_participants,
)
from app.schemas.meeting import SegmentMeta


# --- SegmentMeta: BE ↔ AI 세그먼트 메타데이터 계약 ---

# VadSegmentMetadataDto가 직렬화하는 실제 필드 형태(camelCase). BE에만 있는 username은
# SegmentMeta에 없지만 Pydantic 기본 동작(extra 무시)으로 파싱이 깨지지 않아야 한다.
_BE_SEGMENT_JSON = {
    "meetingRoomId": 42,
    "participantId": 7,
    "username": "홍길동",
    "sequence": 3,
    "startedAt": 1_700_000_000_000,
    "endedAt": 1_700_000_005_000,
    "durationMs": 5000,
    "audioSha256": "abc123",
    "audioObjectKey": "conferences/42/participants/7/segment-000003.ogg",
    "uploadedAt": "2026-08-06T04:00:00+00:00",
}


def test_segment_meta_parses_be_json():
    meta = SegmentMeta.model_validate(_BE_SEGMENT_JSON)
    assert meta.meeting_room_id == 42
    assert meta.participant_id == 7
    assert meta.sequence == 3
    assert meta.started_at == 1_700_000_000_000
    assert meta.ended_at == 1_700_000_005_000
    assert meta.duration_ms == 5000
    assert meta.audio_object_key.endswith("segment-000003.ogg")


def test_segment_meta_ignores_extra_be_only_field():
    # BE의 username 필드가 있어도 파싱이 실패하지 않아야 한다(계약 안정성).
    meta = SegmentMeta.model_validate(_BE_SEGMENT_JSON)
    assert not hasattr(meta, "username")


def test_segment_meta_populate_by_name():
    # 내부 코드가 snake_case 이름으로 만들어도 동일하게 파싱된다(populate_by_name=True).
    data = {
        "meeting_room_id": 1,
        "participant_id": 2,
        "sequence": 1,
        "started_at": 10,
        "ended_at": 20,
        "duration_ms": 10,
        "audio_sha256": "x",
        "audio_object_key": "k",
        "uploaded_at": "2026-08-06T00:00:00+00:00",
    }
    meta = SegmentMeta.model_validate(data)
    assert meta.participant_id == 2


def test_segment_meta_missing_required_field_raises():
    broken = dict(_BE_SEGMENT_JSON)
    del broken["startedAt"]
    with pytest.raises(Exception):
        SegmentMeta.model_validate(broken)


# --- common/text.py ---


@pytest.mark.parametrize(
    "seconds,expected",
    [
        (0, "00:00:00"),
        (5, "00:00:05"),
        (65, "00:01:05"),
        (3661, "01:01:01"),
        (3599.9, "00:59:59"),
    ],
)
def test_seconds_to_hhmmss(seconds, expected):
    assert seconds_to_hhmmss(seconds) == expected


def test_format_transcript():
    segs = [
        TranscriptSegment(speaker="p1", start=0.0, end=2.0, text="안녕하세요"),
        TranscriptSegment(speaker="p2", start=65.0, end=70.0, text="반갑습니다"),
    ]
    out = format_transcript(segs)
    assert out == "[00:00:00] p1: 안녕하세요\n[00:01:05] p2: 반갑습니다"


# --- participation.py ---


def _sample_segments() -> list[TranscriptSegment]:
    return [
        TranscriptSegment(speaker="p1", start=0.0, end=6.0, text="a"),   # 6s
        TranscriptSegment(speaker="p2", start=6.0, end=9.0, text="b"),   # 3s
        TranscriptSegment(speaker="p1", start=9.0, end=10.0, text="c"),  # 1s
        TranscriptSegment(speaker="p2", start=10.0, end=10.0, text="d"), # 0s
    ]


def test_compute_participation_stats_values():
    stats = compute_participation_stats(_sample_segments())
    by_speaker = {s.speaker: s for s in stats}

    assert by_speaker["p1"].utterance_count == 2
    assert by_speaker["p1"].speaking_seconds == pytest.approx(7.0)
    assert by_speaker["p2"].utterance_count == 2
    assert by_speaker["p2"].speaking_seconds == pytest.approx(3.0)

    # 전체 발화시간 10s 대비 비율
    assert by_speaker["p1"].ratio == pytest.approx(0.7)
    assert by_speaker["p2"].ratio == pytest.approx(0.3)
    # 비율 합은 1
    assert sum(s.ratio for s in stats) == pytest.approx(1.0)


def test_compute_participation_stats_sorted_desc():
    stats = compute_participation_stats(_sample_segments())
    ratios = [s.ratio for s in stats]
    assert ratios == sorted(ratios, reverse=True)


def test_compute_participation_stats_empty_no_division_error():
    stats = compute_participation_stats([])
    assert stats == []


def test_compute_participation_stats_zero_total_ratio_is_zero():
    # 모든 세그먼트 길이가 0이면 total=0 -> ratio는 0.0 (ZeroDivision 방지)
    segs = [TranscriptSegment(speaker="p1", start=5.0, end=5.0, text="x")]
    stats = compute_participation_stats(segs)
    assert stats[0].ratio == 0.0
    assert stats[0].utterance_count == 1


def test_get_participants_first_seen_order():
    segs = [
        TranscriptSegment(speaker="p2", start=0, end=1, text="a"),
        TranscriptSegment(speaker="p1", start=1, end=2, text="b"),
        TranscriptSegment(speaker="p2", start=2, end=3, text="c"),
    ]
    assert get_participants(segs) == ["p2", "p1"]


def test_get_meeting_duration():
    segs = [
        TranscriptSegment(speaker="p1", start=3.0, end=8.0, text="a"),
        TranscriptSegment(speaker="p2", start=1.0, end=5.0, text="b"),
    ]
    assert get_meeting_duration(segs) == (1.0, 8.0)


def test_get_meeting_duration_empty():
    assert get_meeting_duration([]) == (0.0, 0.0)


# --- _build_legacy_meeting_dir: S3 구조 -> transcribe.py 입력 구조 변환 ---
# ffmpeg 실행부(_concat_segments_with_silence)는 monkeypatch로 대체하고,
# 참가자별 세그먼트 정렬/구조 생성/EG json(ns 변환) 로직만 검증한다.


def _write_segment(participant_dir, seq: int, participant_id: int, started_ms: int, ended_ms: int):
    """실제 S3 구조(conferences/{id}/participants/{pid}/segment-NNNNNN.json|ogg)를 흉내낸다."""
    (participant_dir / f"segment-{seq:06d}.ogg").write_bytes(b"fake-ogg")
    meta = {
        "meetingRoomId": 100,
        "participantId": participant_id,
        "sequence": seq,
        "startedAt": started_ms,
        "endedAt": ended_ms,
        "durationMs": ended_ms - started_ms,
        "audioSha256": "sha",
        "audioObjectKey": f"conferences/100/participants/{participant_id}/segment-{seq:06d}.ogg",
        "uploadedAt": "2026-08-06T00:00:00+00:00",
    }
    (participant_dir / f"segment-{seq:06d}.json").write_text(
        json.dumps(meta), encoding="utf-8"
    )


def test_build_legacy_meeting_dir_structure(tmp_path, monkeypatch):
    pipeline_service = pytest.importorskip("app.services.pipeline_service")

    # ffmpeg 병합은 실행하지 않고, 어떤 세그먼트가 어떤 순서로 넘어왔는지만 기록한다.
    recorded: dict[str, list] = {}

    def fake_concat(segments, output_path):
        # segments: list[(SegmentMeta, ogg_path)] — 시작 시각순 정렬되어 넘어와야 한다
        recorded[str(output_path)] = [meta.sequence for meta, _ in segments]
        output_path.write_bytes(b"merged")

    monkeypatch.setattr(pipeline_service, "_concat_segments_with_silence", fake_concat)

    # session_root = 다운로드된 conferences/{meeting_id} 대응 로컬 디렉토리
    session_root = tmp_path / "100"
    p7 = session_root / "participants" / "7"
    p9 = session_root / "participants" / "9"
    p7.mkdir(parents=True)
    p9.mkdir(parents=True)

    # 참가자 7: 일부러 순서를 뒤섞어 넣는다(seq 2 먼저, seq 1 나중) -> 정렬 검증
    _write_segment(p7, seq=2, participant_id=7, started_ms=5000, ended_ms=8000)
    _write_segment(p7, seq=1, participant_id=7, started_ms=0, ended_ms=3000)
    # 참가자 9: 세그먼트 1개
    _write_segment(p9, seq=1, participant_id=9, started_ms=0, ended_ms=4000)

    legacy_dir = pipeline_service._build_legacy_meeting_dir(str(session_root), "100")

    from pathlib import Path

    legacy = Path(legacy_dir)
    # 참가자별 병합 오디오 + EG json이 생성되어야 한다
    for pid in ("7", "9"):
        assert (legacy / "participants" / pid / "TR_merged.ogg").exists()
        eg = legacy / "participants" / pid / "EG_merged.json"
        assert eg.exists()
        eg_data = json.loads(eg.read_text(encoding="utf-8"))
        assert eg_data["track_id"] == "TR_merged"

    # 참가자 7은 startedAt 오름차순(seq 1 -> seq 2)으로 병합에 넘겨져야 한다
    p7_merged = str(legacy / "participants" / "7" / "TR_merged.ogg")
    assert recorded[p7_merged] == [1, 2]

    # EG json의 started_at은 가장 이른 세그먼트 startedAt(ms) * 1_000_000 (ns)여야 한다
    eg7 = json.loads(
        (legacy / "participants" / "7" / "EG_merged.json").read_text(encoding="utf-8")
    )
    assert eg7["started_at"] == 0 * 1_000_000


def test_build_legacy_meeting_dir_skips_participant_without_segments(tmp_path, monkeypatch):
    pipeline_service = pytest.importorskip("app.services.pipeline_service")
    monkeypatch.setattr(
        pipeline_service,
        "_concat_segments_with_silence",
        lambda segments, output_path: output_path.write_bytes(b"merged"),
    )

    session_root = tmp_path / "200"
    empty_p = session_root / "participants" / "1"  # 세그먼트 없는 참가자
    empty_p.mkdir(parents=True)

    from pathlib import Path

    legacy_dir = Path(pipeline_service._build_legacy_meeting_dir(str(session_root), "200"))
    # 세그먼트가 없으면 해당 참가자 디렉토리는 생성되지 않아야 한다
    assert not (legacy_dir / "participants" / "1").exists()
