"""
회의록 히스토리 -> PostgreSQL(pgvector) 저장 -> 다음 회의록 작성 시 관련 과거 회의 참고

실제 회의 "내용"을 참고한다. 파이프라인이 회의록(MeetingMinutes)을 만들 때마다 그 내용을
plain text로 직렬화해(history_text.py) meeting_history 테이블에 쌓고, 다음 회의를
처리할 때 이번 회의 전사와 임베딩
유사도가 높은 과거 회의록 top-k를 LLM에 맥락으로 제공해 "지난 회의에서 논의된
A를 오늘 이어서 논의함" 같은 연속성 있는 서술이 가능하게 한다. 단, 이번 회의
전사에 실제로 언급되지 않은 과거 회의 내용을 새로 지어내는 것은 여전히
금지한다(schema.py의 담당자 null 규칙과 같은 급의 원칙, extract.py 참고).

누적된 회의 히스토리 전체를 대상으로 코사인 거리(<=> 연산자, HNSW 인덱스)로
검색한다 — "가장 최근 회의 1건만" 참고하던 이전 방식은 회의가 쌓여도 벡터 검색이
실질적으로 쓰이지 않는다는 문제가 있어, 실제 유사도 기반 top-k 검색으로 바꿨다.

PostgreSQL 15+ + pgvector 확장을 사용한다. 로컬 DATABASE_URL 또는 ECS에서
Secrets Manager로 주입한 분리 DB 환경변수를 사용한다.
"""

import os
from pathlib import Path

import psycopg2
from openai import OpenAI
from pgvector import Vector
from pgvector.psycopg2 import register_vector

from app.core.config import get_settings

OUTPUT_DIR = Path("output")
TABLE_NAME = "meeting_history"

EMBEDDING_MODEL = os.environ.get("EMBEDDING_MODEL", "text-embedding-3-small")
EMBEDDING_DIM = 1536  # EMBEDDING_MODEL 차원 수. 다른 임베딩 모델로 바꾸면 이 값과 DB 컬럼도 같이 바꿔야 한다

# OpenAI 임베딩(text-embedding-3-small) 입력 한도는 8192 토큰이다. 한국어 문서의
# 토큰/문자 비율은 약 1.0(측정값)이라 6000자면 최악의 밀도(약 1.3)를 가정해도
# 8192 토큰 안에 안전하게 들어온다.
MAX_DOC_CHARS = 6000


def get_connection():
    conn = psycopg2.connect(**get_settings().psycopg2_connect_kwargs)
    # vector 확장과 RAG 테이블은 Backend Flyway 마이그레이션이 관리한다.
    register_vector(conn)
    return conn


def _embed(text: str) -> list[float]:
    client = OpenAI(
        api_key=os.environ.get("OPENAI_API_KEY"),
        base_url="https://gms.ssafy.io/gmsapi/api.openai.com/v1",
    )
    response = client.embeddings.create(model=EMBEDDING_MODEL, input=text[:MAX_DOC_CHARS])
    return response.data[0].embedding


def _strip_transcript_log(document: str) -> str:
    """레거시 meeting_report.md 끝에 붙는 원문 발화 로그(<details> 블록)를 잘라낸다.

    backfill_from_output()이 과거에 파일로 남아 있던 렌더링된 MD를 읽어올 때만
    쓰는 정리 단계다. save_meeting()은 더 이상 MD를 받지 않으므로(history_text.py
    참고) 이 stripping을 하지 않는다.
    """
    return document.split("\n<details>")[0].rstrip()


def save_meeting(meeting_id: int, document: str, created_at=None) -> None:
    """방금 만든 회의록 내용을 히스토리에 저장한다.

    document는 이미 정리된 회의록 내용 텍스트여야 한다(history_text.py의
    build_minutes_history_text 참고). 같은 meeting_id로 다시 호출하면 내용을
    덮어쓴다(같은 회의 재실행 시 최신 결과로 갱신). created_at을 지정하지
    않으면 저장 시각(now())을 쓴다(backfill_from_output()에서 과거 기록 순서를
    보존할 때만 지정해서 쓴다).
    """
    content = document
    embedding = _embed(content)
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                INSERT INTO {TABLE_NAME} (id, document, embedding, created_at)
                VALUES (%s, %s, %s, COALESCE(%s, now()))
                ON CONFLICT (id) DO UPDATE
                SET document = EXCLUDED.document,
                    embedding = EXCLUDED.embedding,
                    created_at = EXCLUDED.created_at
                """,
                (meeting_id, content, Vector(embedding), created_at),
            )
        conn.commit()
    finally:
        conn.close()


DEFAULT_TOP_K = 3


def get_relevant_meetings(
    query_text: str, top_k: int = DEFAULT_TOP_K, exclude_id: int | None = None
) -> list[tuple[str, str]]:
    """query_text와 임베딩 유사도가 높은 과거 회의록을 top_k개 검색한다.

    누적된 meeting_history 전체를 대상으로 코사인 거리(<=>, HNSW 인덱스) 기준
    유사도 검색을 한다. exclude_id를 주면 그 id는 후보에서 제외한다(같은 회의를
    재실행할 때 자기 자신이 검색되는 것을 방지). 히스토리가 없으면 빈 리스트를
    반환한다(첫 회의).

    반환: [(id, document), ...] (유사도 순, 코사인 거리 <=> 오름차순)
    """
    embedding = Vector(_embed(query_text))

    conn = get_connection()
    try:
        with conn.cursor() as cur:
            if exclude_id is not None:
                cur.execute(
                    f"""
                    SELECT id, document FROM {TABLE_NAME}
                    WHERE id != %s
                    ORDER BY embedding <=> %s
                    LIMIT %s
                    """,
                    (exclude_id, embedding, top_k),
                )
            else:
                cur.execute(
                    f"""
                    SELECT id, document FROM {TABLE_NAME}
                    ORDER BY embedding <=> %s
                    LIMIT %s
                    """,
                    (embedding, top_k),
                )
            return cur.fetchall()
    finally:
        conn.close()


def backfill_from_output() -> int:
    """output/*/meeting_report.md를 모두 읽어 meeting_history를 채운다.

    이미 로컬에 쌓여 있는 과거 실행 결과를 히스토리 테이블로 옮길 때 쓰는
    1회성 유틸리티다. created_at은 각 폴더의 파일 수정 시각을 그대로 써서
    실제 생성 순서를 보존한다. 반환값은 저장한 회의 수.
    """
    from datetime import datetime, timezone

    md_paths = sorted(OUTPUT_DIR.glob("*/meeting_report.md"))
    for path in md_paths:
        meeting_id = int(path.parent.name)  # id가 BIGINT라 폴더명이 숫자여야 함
        document = _strip_transcript_log(path.read_text(encoding="utf-8"))
        created_at = datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc)
        save_meeting(meeting_id, document, created_at=created_at)
        print(f"[rag] 저장: {meeting_id} (created_at={created_at.isoformat()})")
    return len(md_paths)


if __name__ == "__main__":
    from dotenv import load_dotenv

    load_dotenv()  # 단독 실행 시 .env에서 DATABASE_URL, OPENAI_API_KEY 로드

    print("[rag] output/의 과거 회의록을 히스토리로 백필합니다...")
    count = backfill_from_output()
    print(f"[rag] 완료: {count}개 회의록 저장됨")
