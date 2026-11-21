"""LLM·임베딩 호출이 향하는 OpenAI 호환 엔드포인트.

기본값은 프로젝트 기간에 쓴 SSAFY GMS 게이트웨이다. 교육 과정 밖에서 돌릴 때는
OPENAI_BASE_URL 환경변수로 다른 OpenAI 호환 엔드포인트를 지정한다.
"""

from __future__ import annotations

import os

DEFAULT_OPENAI_BASE_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1"


def openai_base_url() -> str:
    """OPENAI_BASE_URL이 비어 있으면 기본 게이트웨이 주소를 돌려준다."""
    return os.environ.get("OPENAI_BASE_URL", "").strip() or DEFAULT_OPENAI_BASE_URL
