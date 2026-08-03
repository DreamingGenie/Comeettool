"""
SQLAlchemy 엔진/세션

DATABASE_URL은 루트 파이프라인(rag.py)이 pgvector로 쓰는 것과 같은 PostgreSQL을
가리킨다. 여기서 만드는 테이블(models.py)은 pgvector 테이블과 같은 DB 안에서
공존한다 - 별도 DB가 필요한 게 아니다.
"""

from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.core.config import get_settings

settings = get_settings()

engine = create_engine(settings.DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
