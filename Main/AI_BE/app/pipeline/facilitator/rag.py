"""
Facilitator 보고서 히스토리 -> PostgreSQL(pgvector) 저장 -> 다음 Facilitator 보고서 작성 시
관련 과거 보고서 참고

rag.py의 meeting_history와 동일한 패턴을, 회의록이 아니라 Facilitator 보고서
(FacilitatorReport) 코퍼스에 대해 그대로 적용한다. 파이프라인이 Facilitator
보고서를 만들 때마다 그 내용을 plain text로 직렬화해(history_text.py)
facilitator_report_history 테이블에 쌓고, 다음 회의를 처리할 때 이번 회의 전사와
임베딩 유사도가 높은 과거 보고서 top-k를 LLM에 맥락으로 제공한다. 단, 이번 회의
전사에 실제로 언급되지 않은 과거 보고서 내용을 새로 지어내는 것은 여전히
금지한다(schema.py의 담당자 null 규칙과 같은 급의 원칙).

누적된 히스토리 전체를 대상으로 코사인 거리(<=> 연산자, HNSW 인덱스)로 검색한다.

PostgreSQL 15+ + pgvector 확장을 사용한다. 로컬 DATABASE_URL 또는 ECS에서
Secrets Manager로 주입한 분리 DB 환경변수를 사용한다.
"""

import os

import psycopg2
from openai import OpenAI
from pgvector import Vector
from pgvector.psycopg2 import register_vector

from app.core.config import get_settings

TABLE_NAME = "facilitator_report_history"

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


def save_facilitator_report(meeting_id: int, document: str, created_at=None) -> None:
    """방금 만든 Facilitator 보고서 내용을 히스토리에 저장한다.

    document는 이미 정리된 보고서 내용 텍스트여야 한다(history_text.py의
    build_facilitator_history_text 참고). 같은 meeting_id로 다시 호출하면 내용을
    덮어쓴다(같은 회의 재실행 시 최신 결과로 갱신). created_at을 지정하지
    않으면 저장 시각(now())을 쓴다.
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


def get_relevant_facilitator_reports(
    query_text: str, top_k: int = DEFAULT_TOP_K, exclude_id: int | None = None
) -> list[tuple[str, str]]:
    """query_text와 임베딩 유사도가 높은 과거 Facilitator 보고서를 top_k개 검색한다.

    누적된 facilitator_report_history 전체를 대상으로 코사인 거리(<=>, HNSW 인덱스)
    기준 유사도 검색을 한다. exclude_id를 주면 그 id는 후보에서 제외한다(같은 회의를
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
