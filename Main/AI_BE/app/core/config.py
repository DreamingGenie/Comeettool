"""
환경변수 설정

프로젝트 루트의 .env를 그대로 쓴다(OPENAI_API_KEY, DATABASE_URL + S3 관련 값).
"""

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# app/core/config.py -> 프로젝트 루트(.env 위치)
_ROOT_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=_ROOT_ENV_FILE, extra="ignore")

    # 루트 파이프라인과 공유
    OPENAI_API_KEY: str
    DATABASE_URL: str

    # S3 (녹음 세그먼트가 conferences/{meetingId}/... 형태로 저장되는 버킷)
    S3_BUCKET_NAME: str
    AWS_REGION: str = "ap-northeast-2"
    # EC2 IAM 역할로 인증하는 게 기본. 로컬 개발 등에서만 아래 두 개를 채운다.
    AWS_ACCESS_KEY_ID: str | None = None
    AWS_SECRET_ACCESS_KEY: str | None = None

    # BE -> AI 내부 호출 인증용 (X-Internal-Token 헤더와 대조)
    AI_INTERNAL_TOKEN: str


@lru_cache
def get_settings() -> Settings:
    return Settings()
