"""
S3에서 회의 녹음 세그먼트 다운로드

저장 구조 (conferences/{meetingId}/participants/{participantId}/sessions/{sessionId}/...):
    audio/segment-000001.ogg
    metadata/segment-000001.json

meeting_id 하나에 해당하는 모든 오디오/메타데이터를 로컬 임시 디렉토리로 그대로
복제해온다. participant/session 단위로 여러 segment 파일이 나뉘어 있으므로,
이걸 기존 transcribe.py가 기대하는 입력(참가자 폴더당 EG_*.json + TR_*.ogg 1개)
형태로 합치는 작업은 pipeline_service.py에서 처리한다.
"""

import boto3

from app.core.config import get_settings

settings = get_settings()


def get_s3_client():
    kwargs = {"region_name": settings.AWS_REGION}
    # 정적 키가 없으면 boto3 기본 자격증명 체인(EC2 인스턴스 IAM 역할 등)을 그대로 쓴다.
    if settings.AWS_ACCESS_KEY_ID and settings.AWS_SECRET_ACCESS_KEY:
        kwargs["aws_access_key_id"] = settings.AWS_ACCESS_KEY_ID
        kwargs["aws_secret_access_key"] = settings.AWS_SECRET_ACCESS_KEY
    return boto3.client("s3", **kwargs)


def download_meeting_recordings(meeting_id: int, dest_dir: str) -> str:
    """conferences/{meeting_id}/ 아래 모든 객체를 dest_dir에 같은 상대 경로로 내려받는다.

    반환값은 conferences/{meeting_id}에 대응하는 로컬 디렉토리 경로다.
    """
    from pathlib import Path

    s3 = get_s3_client()
    prefix = f"conferences/{meeting_id}/"
    dest_root = Path(dest_dir) / str(meeting_id)
    dest_root.mkdir(parents=True, exist_ok=True)

    paginator = s3.get_paginator("list_objects_v2")
    found = False
    for page in paginator.paginate(Bucket=settings.S3_BUCKET_NAME, Prefix=prefix):
        for obj in page.get("Contents", []):
            found = True
            key = obj["Key"]
            relative_path = key[len(prefix):]
            local_path = dest_root / relative_path
            local_path.parent.mkdir(parents=True, exist_ok=True)
            s3.download_file(settings.S3_BUCKET_NAME, key, str(local_path))

    if not found:
        raise FileNotFoundError(f"S3에 {prefix} 아래 객체가 없습니다.")

    return str(dest_root)
