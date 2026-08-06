"""
SQLAlchemy 엔진/세션

로컬 DATABASE_URL과 ECS의 분리 DB 환경변수를 모두 지원한다. SQLAlchemy 모델과
pgvector RAG 코드는 같은 PostgreSQL DB를 사용한다.
"""

from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.core.config import get_settings

settings = get_settings()

engine = create_engine(settings.sqlalchemy_database_url, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
