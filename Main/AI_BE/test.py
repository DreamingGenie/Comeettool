"""X-Internal-Token 인증 스모크 테스트.

BE -> AI 내부 호출 인증(POST /meetings/{meeting_id}/process, X-Internal-Token 헤더)이
의도대로 동작하는지 확인하는 수동 실행 스크립트.

사전 조건:
  - AI_BE 서버가 떠 있어야 함 (uvicorn app.main:app --reload)
  - .env에 AI_INTERNAL_TOKEN이 설정돼 있어야 함 (서버와 이 스크립트가 같은 값을 읽음)

실행:
  python test.py
  python test.py --base-url http://127.0.0.1:8000 --meeting-id 999999

참고:
  - meeting_id는 존재하지 않아도 된다. 이 스크립트는 인증 레이어만 검증하며,
    실제 S3/STT/LLM 파이프라인(백그라운드 처리)은 검증 대상이 아니다.
  - "정답 토큰" 케이스는 401/422가 아니면 통과로 본다. meeting_id가 DB에 없으면
    FK 제약 위반으로 500이 날 수 있는데, 이는 인증을 통과했다는 뜻이라 정상이다.
"""

from __future__ import annotations

import argparse
import os
import sys

import requests
from dotenv import load_dotenv

load_dotenv()


def check(name: str, condition: bool, detail: str) -> bool:
    status = "PASS" if condition else "FAIL"
    print(f"[{status}] {name} - {detail}")
    return condition


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=os.environ.get("AI_BASE_URL", "http://127.0.0.1:8000"))
    parser.add_argument("--meeting-id", default="999999")
    args = parser.parse_args()

    token = os.environ.get("AI_INTERNAL_TOKEN")
    if not token:
        print("AI_INTERNAL_TOKEN이 환경변수/.env에 없습니다. 서버와 동일한 값을 설정하세요.")
        return 1

    url = f"{args.base_url}/meetings/{args.meeting_id}/process"
    results = []

    # 1) 서버 헬스체크
    try:
        r = requests.get(f"{args.base_url}/health", timeout=5)
        results.append(check("health check", r.status_code == 200, f"status={r.status_code}"))
    except requests.exceptions.RequestException as e:
        print(f"[FAIL] health check - 서버에 연결할 수 없습니다: {e}")
        return 1

    # 2) 헤더 누락 -> 422 (FastAPI가 필수 헤더 부재로 거부)
    r = requests.post(url, timeout=10)
    results.append(check("no header -> 422", r.status_code == 422, f"status={r.status_code}"))

    # 3) 잘못된 토큰 -> 401
    r = requests.post(url, headers={"X-Internal-Token": "wrong-token"}, timeout=10)
    results.append(check("wrong token -> 401", r.status_code == 401, f"status={r.status_code}"))

    # 4) 올바른 토큰 -> 인증은 통과해야 함 (401/422가 아니면 OK)
    r = requests.post(url, headers={"X-Internal-Token": token}, timeout=30)
    results.append(
        check(
            "correct token -> auth passes",
            r.status_code not in (401, 422),
            f"status={r.status_code}, body={r.text[:200]}",
        )
    )

    ok = all(results)
    print("\n=== 전체 결과:", "PASS" if ok else "FAIL", "===")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
