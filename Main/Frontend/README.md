# CommitTool Vue Frontend

## 개발 실행

```bash
npm install
npm run dev
```

개발 서버는 `http://127.0.0.1:5173`에서 실행되며 `/api` 요청은 Flask `http://127.0.0.1:5000`으로 전달됩니다.

## 프로덕션 빌드

```bash
npm run build
```

빌드 결과는 프로젝트 루트의 `dist/`에 생성됩니다. Flask는 `dist/index.html`이 존재하면 Vue 빌드를 우선 제공합니다.

## API 연결

API 함수는 `src/services/api.js`에 모여 있습니다. 별도 서버 주소가 필요하면 `.env`에 아래 값을 설정하세요.

```env
VITE_API_BASE_URL=http://127.0.0.1:5000
```
# 데이터 소스 전환

화면은 기본적으로 `features/*/mock` 데이터를 사용합니다. 백엔드 API가
준비되면 `.env`에 아래 값을 설정하면 같은 화면 코드가 실제 API 모듈을
사용합니다.

```env
VITE_USE_MOCK_API=false
VITE_API_BASE_URL=http://localhost:8080
```
