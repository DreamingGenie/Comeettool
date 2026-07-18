# Git 컨벤션

## 브랜치 전략

| 브랜치 | 설명 |
|--------|------|
| `main` | 실제 배포 가능한 안정 버전의 브랜치 |
| `develop` | 개발 기능들이 통합되는 브랜치 (main에서 분기하여 main으로 merge) |
| `feature/*` | 기능 개발을 위한 브랜치 (develop에서 분기하여 develop으로 merge) |
| `fix/*` | 버그 수정을 위한 브랜치 (develop에서 분기하여 develop으로 merge) |
| `hotfix/*` | 배포 환경 긴급 수정 브랜치 (main에서 생성하여 main과 develop으로 merge) |
| `refactor/*` | 기능 변경 없이 코드 리팩토링을 위한 브랜치 (develop에서 분기하여 develop으로 merge) |
| `docs/*` | 문서 작업을 위한 브랜치 (develop에서 분기하여 develop으로 merge) |

### 주의 사항

1. 모든 기능 개발은 `develop` 브랜치에서 새로운 브랜치를 생성하여 진행한다.
2. `feature` 기능 개발이 완료되면 `develop` 브랜치로 Pull Request(MR)를 생성한다.
3. PR은 팀원 리뷰 후 merge한다. (절대 자신이 올린 PR을 자신이 승인하지 않는다)
4. `main` 브랜치와 `develop` 브랜치에는 직접 push하지 않는다.
5. `main` 브랜치는 배포 가능한 상태만 유지한다.
6. 운영 환경에서 긴급 수정이 필요한 경우 `hotfix/*` 브랜치를 사용한다.
7. `hotfix/*` 브랜치의 수정 사항은 `main`과 `develop`에 모두 반영한다.

## 브랜치 네이밍 규칙

### 기본 형식

```
{브랜치 타입}/{이슈번호}-{작업내용}
```

- `feature`: 새로운 기능 개발
- `fix`: 개발 중 버그 수정
- `hotfix`: 운영 환경 긴급 수정
- `refactor`: 기능 변경 없는 코드 구조 개선
- `docs`: 문서 수정

### 작성 규칙

1. 브랜치명은 영어 소문자로 작성한다.
2. 단어 구분은 하이픈 `-`을 사용한다.
3. 브랜치명에는 공백을 사용하지 않는다.
4. 이슈 번호가 있는 경우 반드시 포함한다.
5. 작업 내용은 간결하고 명확하게 작성한다.

### 예시

- `feature/12-login`
- `feature/15-smoking-area-search`
- `fix/23-token-expired-error`
- `hotfix/31-server-error`
- `refactor/42-user-service`
- `docs/1-api-spec`

## 커밋 메시지 규칙

### 형식

```
<type>: <작업 내용> (#이슈 번호)
```

### Type 종류

| Type | 설명 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `docs` | 문서 수정 |
| `chore` | 오타 수정, 빌드 설정, 패키지 관리, 환경 설정 등 기타 작업 |

### 작성 규칙

1. 커밋 메시지는 한글로 작성한다.
2. 커밋 메시지는 작업 내용을 명확하게 작성한다.
3. 한 커밋에는 하나의 작업 단위만 포함한다.
4. 커밋 메시지 끝에는 마침표를 찍지 않는다.
5. 작업 내용은 너무 길지 않게 작성한다.
6. 단순히 "수정", "작업", "변경"처럼 모호하게 작성하지 않는다.

### 예시

- `feat: 회원가입 기능 구현 (#11)`
- `feat: 흡연구역 검색 기능 구현 (#11)`
- `fix: 로그인 토큰 만료 오류 수정 (#31)`
- `refactor: UserService 책임 분리 (#15)`
- `docs: API 명세 작성 (#1)`
- `chore: GitLab Actions 설정 추가`

## Merge / Review 규칙

### Merge 규칙

- `main`, `develop` 브랜치에는 직접 push하지 않는다.
- 모든 작업은 PR(MR)을 통해 merge한다.
- 기능 개발 브랜치는 `develop` 브랜치로 PR을 생성한다.
- 배포 시점에만 `develop` 브랜치를 `main` 브랜치로 merge한다.
- `hotfix/*` 브랜치는 `main`에서 생성하고, 수정 완료 후 `main`과 `develop`에 모두 반영한다.
- merge 완료 후 작업 브랜치는 삭제한다.

### Review 규칙

- PR은 최소 1명 이상의 리뷰어 승인을 받은 뒤 merge한다.
- 리뷰어는 코드 동작, 가독성, 컨벤션 준수 여부를 확인한다.
- PR 작성자는 리뷰 의견을 반영하거나 반영하지 않은 이유를 댓글로 남긴다.
- 리뷰가 반영된 코멘트는 resolve 처리한다.
- 충돌이 발생한 경우 PR 작성자가 해결한다.
