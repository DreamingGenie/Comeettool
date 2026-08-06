"""FastAPI 앱 진입점

실행: uvicorn app.main:app --reload
"""

from dotenv import load_dotenv

load_dotenv()  # app.pipeline 모듈들이 import 시점에 os.environ["OPENAI_API_KEY"] 등을 바로 읽으므로 다른 app.* import보다 먼저 실행되어야 한다

from fastapi import FastAPI

from app.api.routes import meetings

app = FastAPI(title="AI Meeting Analysis Server")

app.include_router(meetings.router)


@app.get("/health")
def health():
    return {"status": "ok"}
