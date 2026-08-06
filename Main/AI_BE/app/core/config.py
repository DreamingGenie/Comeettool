"""
환경변수 설정

로컬에서는 DATABASE_URL을 사용할 수 있고, ECS에서는 Secrets Manager JSON의
DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD를 각각 주입받는다.
"""

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy.engine import URL

# app/core/config.py -> 프로젝트 루트(.env 위치)
_ROOT_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=_ROOT_ENV_FILE, extra="ignore")

    # 루트 파이프라인과 공유
    OPENAI_API_KEY: str

    # 로컬 호환용 단일 URL 또는 ECS용 분리 필드 중 하나를 사용한다.
    DATABASE_URL: str | None = None
    DB_HOST: str | None = None
    DB_PORT: int = 5432
    DB_NAME: str | None = None
    DB_USER: str | None = None
    DB_PASSWORD: SecretStr | None = None
    DB_SSL_MODE: Literal[
        "disable", "allow", "prefer", "require", "verify-ca", "verify-full"
    ] = "verify-full"
    DB_SSL_ROOT_CERT: str | None = "/app/certs/rds-ca-bundle.pem"

    # S3 (녹음 세그먼트가 conferences/{meetingId}/... 형태로 저장되는 버킷)
    S3_BUCKET_NAME: str
    AWS_REGION: str = "ap-northeast-2"
    # EC2 IAM 역할로 인증하는 게 기본. 로컬 개발 등에서만 아래 두 개를 채운다.
    AWS_ACCESS_KEY_ID: str | None = None
    AWS_SECRET_ACCESS_KEY: str | None = None

    # BE -> AI 내부 호출 인증용 (X-Internal-Token 헤더와 대조)
    AI_INTERNAL_TOKEN: str

    @model_validator(mode="after")
    def validate_database_configuration(self) -> "Settings":
        if self.DATABASE_URL:
            return self

        required = {
            "DB_HOST": self.DB_HOST,
            "DB_NAME": self.DB_NAME,
            "DB_USER": self.DB_USER,
            "DB_PASSWORD": self.DB_PASSWORD,
        }
        missing = [name for name, value in required.items() if value is None or value == ""]
        if missing:
            raise ValueError(
                "DATABASE_URL or all component database settings are required: "
                + ", ".join(missing)
            )
        return self

    @property
    def sqlalchemy_database_url(self) -> str | URL:
        if self.DATABASE_URL:
            return self.DATABASE_URL

        query = {"sslmode": self.DB_SSL_MODE}
        if self.DB_SSL_ROOT_CERT:
            query["sslrootcert"] = self.DB_SSL_ROOT_CERT

        return URL.create(
            drivername="postgresql+psycopg2",
            username=self.DB_USER,
            password=self.DB_PASSWORD.get_secret_value() if self.DB_PASSWORD else None,
            host=self.DB_HOST,
            port=self.DB_PORT,
            database=self.DB_NAME,
            query=query,
        )

    @property
    def psycopg2_connect_kwargs(self) -> dict[str, object]:
        if self.DATABASE_URL:
            return {"dsn": self.DATABASE_URL}

        kwargs: dict[str, object] = {
            "host": self.DB_HOST,
            "port": self.DB_PORT,
            "dbname": self.DB_NAME,
            "user": self.DB_USER,
            "password": self.DB_PASSWORD.get_secret_value() if self.DB_PASSWORD else None,
            "sslmode": self.DB_SSL_MODE,
        }
        if self.DB_SSL_ROOT_CERT:
            kwargs["sslrootcert"] = self.DB_SSL_ROOT_CERT
        return kwargs


@lru_cache
def get_settings() -> Settings:
    return Settings()
