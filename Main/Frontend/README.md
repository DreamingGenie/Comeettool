# CoMeetTool Frontend

Vue 3 · Vite SPA. 백엔드(Spring Boot, 기본 `http://127.0.0.1:8080`)와 Yjs 협업 서버(기본 `ws://127.0.0.1:3000/collaboration`)에 붙는다. 서버를 띄우는 순서는 저장소 루트 [README](../../README.md) "실행 방법".

## 개발 실행

```bash
cp .env.example .env
npm install
npm run dev
```

개발 서버는 `http://localhost:5173`에서 뜬다.

## 환경 변수 (`.env`)

| 변수 | 뜻 |
|---|---|
| `VITE_API_BASE_URL` | API 서버 주소(끝 슬래시 없이). 비워 두면 상대 경로(`/api`)로 부르고 아래 개발 서버 프록시를 탄다 |
| `VITE_DEV_PROXY_TARGET` | 개발 서버 프록시(`/api`·`/uploads`) 대상. 기본 `http://127.0.0.1:8080` |
| `VITE_YJS_WEBSOCKET_URL` | 협업 편집 WebSocket 주소(`/collaboration` 포함) |
| `VITE_USE_MOCK_API` | 도메인별 값을 주지 않은 API를 mock으로 쓸지. `false`가 아니면 mock이라, `.env` 없이 띄우면 전부 mock 데이터로 뜬다 |
| `VITE_USE_MOCK_<도메인>_API` | 도메인별 mock 전환(`AUTH`·`SPACE`·`MEETING` 등 — 목록은 `.env.example`) |

백엔드 local 프로필의 CORS 기본값은 `http://localhost:[*]`·`http://127.0.0.1:[*]`이라, `VITE_API_BASE_URL`로 직접 불러도 로컬에서는 막히지 않는다.

## 테스트·빌드

```bash
npm test                          # vitest
npm run build -- --outDir dist    # Main/Frontend/dist (CI·배포와 같은 위치)
```

옵션 없이 `npm run build`만 하면 `vite.config.js`의 `outDir: '../dist'`대로 `Main/dist`에 생긴다.
