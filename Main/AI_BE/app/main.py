"""FastAPI 앱 진입점

실행: uvicorn app.main:app --reload
"""

from dotenv import load_dotenv

load_dotenv()  # app.pipeline 모듈들이 import 시점에 os.environ["OPENAI_API_KEY"] 등을 바로 읽으므로 다른 app.* import보다 먼저 실행되어야 한다

from fastapi import FastAPI

from app.api.routes import meetings
from app.db.models import Base  # noqa: F401 (모델을 import해야 create_all에 등록됨)
from app.db.session import engine

app = FastAPI(title="AI Meeting Analysis Server")

app.include_router(meetings.router)


@app.on_event("startup")
def on_startup() -> None:
    # rag.py가 pgvector 테이블/인덱스를 자동 생성하는 것과 같은 방식.
    # 운영 규모가 커지면 alembic 마이그레이션으로 교체.
    Base.metadata.create_all(bind=engine)


@app.get("/health")
def health():
    return {"status": "ok"}
