# 코드 컨벤션

## 코드 스타일

### 기본 원칙

1. 코드는 가독성과 유지보수성을 우선한다.
2. 불필요하게 복잡한 코드는 지양하고, 명확한 이름과 구조를 사용한다.
3. 팀에서 정한 네이밍, 패키지, 예외 처리, 응답 형식을 따른다.
4. 코드 포맷팅은 IDE 자동 정렬 기능을 사용한다.

### 들여쓰기

- 들여쓰기는 Space 4칸을 사용한다.
- IDE 자동 포맷팅 기준을 따른다.

### 중괄호 규칙

- 중괄호는 생략하지 않는다.
- 여는 중괄호는 선언문과 같은 줄에 작성한다.
- 닫는 중괄호는 별도의 줄에 작성한다.

### import 규칙

- 사용하지 않는 import는 제거한다.
- wildcard import는 사용하지 않는다.
- IDE의 optimize imports 기능을 사용한다.

### 클래스 작성 순서

1. 상수
2. 필드
3. 생성자
4. public 메서드
5. private 메서드

### 메서드 작성 규칙

- 메서드 길이가 과도하게 길어지면 역할에 따라 분리한다.
- 조건문이 복잡해지는 경우 별도 메서드로 분리한다.
- 중복 코드는 공통 메서드로 분리한다.

### 주석 작성 규칙

- 코드만으로 의도를 파악하기 어려운 경우에만 주석을 작성한다.
- 단순히 코드 내용을 반복하는 주석은 작성하지 않는다.
- 임시 주석이나 TODO는 필요한 경우에만 작성하고, 이슈 번호를 함께 남긴다.

### Lombok 사용 규칙

- 생성자 주입은 `@RequiredArgsConstructor`를 사용한다.
- 무분별한 `@Data` 사용은 지양한다.
- Entity에는 `@Setter` 사용을 지양한다.
- 필요한 경우에만 `@Getter`, `@NoArgsConstructor`, `@Builder`를 사용한다.

### Entity 작성 규칙

- Entity에는 `@Setter` 사용을 지양한다.
- Entity의 상태 변경은 의미 있는 메서드를 통해 수행한다.
- 기본 생성자는 `protected` 접근 제어자를 사용한다.
- 연관관계는 기본적으로 `LAZY` 로딩을 사용한다.

### Controller 작성 규칙

- Controller는 요청과 응답을 처리하는 역할만 담당한다.
- 비즈니스 로직은 Service 계층에서 처리한다.
- Request DTO를 통해 요청 데이터를 받는다.
- Response DTO를 통해 응답 데이터를 반환한다.

### Service 작성 규칙

- Service는 비즈니스 로직을 담당한다.
- Controller에 비즈니스 로직을 작성하지 않는다.
- Repository 접근은 Service 계층에서 처리한다.
- 트랜잭션이 필요한 메서드에는 `@Transactional`을 사용한다.
- 조회 전용 메서드에는 `@Transactional(readOnly = true)`를 사용한다.

## 패키지 구조

```
src
├── main
│   ├── java
│   │   └── com
│   │       └── ssafy
│   │           └── backend
│   │               ├── DemoApplication.java
│   │               ├── auth
│   │               │   ├── controller
│   │               │   ├── domain
│   │               │   ├── dto
│   │               │   ├── exception
│   │               │   ├── mapper
│   │               │   └── service
│   │               │        ├── impl
│   │               │        │    └── serviceImpl
│   │               │        └── interface
│   │               ├── restaurant
│   │               ├── favorite
│   │               ├── menu
│   │               ├── review
│   │               ├── diet
│   │               ├── post
│   │               ├── comment
│   │               ├── question
│   │               ├── config
│   │               └── image
│   └── resources
│       ├── static
│       │     ├── images
│       │     └── assets
│       ├── mapper
│       └── application.properties
```

## 네이밍 규칙

### 클래스 / DTO / Entity

- `controller`, `service`, `mapper` → 도메인 + 계층이름
  - 예) `ImageController`, `UserServiceImpl`
- `dto` → [Request / Response] + [기능명] + Dto
  - 예) `RequestLoginDto`, `ResponsePostListDto`
- `domain` → DB 테이블명
  - 예) `User`, `Image`

### 메서드

**Controller** — 도메인명 +
- 조회 (GET): `find` + `List`(다건), `Details`(단건)
- 생성 (POST): `add`
- 수정 (PUT/PATCH): `modify`
- 삭제 (DELETE): `remove`

**Service**
- 단일 조회: `find` + 도메인 (결과가 없을 때 null이나 Optional 반환 시)
- 목록 조회: `find` + 도메인 + `List` (또는 `[명사]List`, `[명사]All`)
- 생성: `add` + 도메인
- 수정: `modify` + 도메인
- 삭제: `remove` + 도메인
- 조건 검증/존재 여부: `is[명사]`, `has[명사]`, `check[명사]` (반환값 boolean)

**Mapper**
- SQL 명령어 + 도메인

### 변수명

- 축약 없이 그대로 사용한다.

## 프론트엔드 컨벤션

- Vue Project Convention을 따른다. (별도 문서 참조)
