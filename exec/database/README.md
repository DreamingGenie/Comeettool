# 데이터베이스 덤프 안내

## 산출물

- 덤프: `comeettool_dump.sql`
- 체크섬: `comeettool_dump.sha256`

이 덤프는 운영 DB에서 추출한 파일이 아니다. 저장소의 Flyway `V1`~`V4`를 PostgreSQL 17과 pgvector 0.8.2 환경에 적용한 뒤 생성한 제출·로컬 구축용 임시 스키마 덤프다.

검증 결과는 다음과 같다.

- PostgreSQL major version: 17
- Flyway schema version: v4
- Flyway 적용 이력: 4개
- `public` 기본 테이블: 21개
- `vector` 확장: 포함
- 애플리케이션 데이터: 0건
- 운영 데이터 및 Secret: 포함하지 않음
- Owner와 ACL: 제외(`--no-owner --no-privileges`)

Flyway 이력 4건은 스키마 버전 식별을 위해 포함되어 있다. 실제 운영 배포는 이 덤프가 아니라 Migration ECS Task와 Flyway SQL을 사용한다. 런타임 DB Role별 GRANT도 덤프에서 제외했으므로 운영에서는 Flyway placeholder에 실제 Role 이름을 전달해야 한다.

## 요구사항

- PostgreSQL 17 클라이언트 및 서버
- pgvector 확장을 제공하는 PostgreSQL 서버
- 대상 데이터베이스에 확장을 생성할 수 있는 계정

PostgreSQL 17의 `pg_dump`가 생성한 메타 명령을 포함하므로 복원에도 PostgreSQL 17의 `psql` 사용을 권장한다.

## 체크섬 확인

PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 .\exec\database\comeettool_dump.sql
Get-Content .\exec\database\comeettool_dump.sha256
```

기대 SHA-256:

```text
502a9df071f11c24315aa87b8575cb4a270d869e00eed8ccac1462d81c7f6fe6
```

## 복원

덤프에는 `--clean --if-exists`로 생성된 객체 정리 구문이 들어 있다. 기존 업무 DB가 아니라 새로 만든 전용 데이터베이스에 복원한다.

```powershell
createdb -h <DB_HOST> -p 5432 -U <DB_ADMIN_USER> comeettool_porting
psql -h <DB_HOST> -p 5432 -U <DB_ADMIN_USER> -d comeettool_porting -v ON_ERROR_STOP=1 -f .\exec\database\comeettool_dump.sql
```

복원 확인:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';

SELECT count(*)
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';
```

정상 기준은 Flyway 이력 4개가 모두 성공, `vector` 확장 1개, `public` 기본 테이블 21개다.
