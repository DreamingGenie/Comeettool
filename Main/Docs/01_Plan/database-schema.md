# 데이터베이스 스키마 (ERD DDL)

ERD를 기반으로 작성한 DDL이다. 테이블·제약조건·외래키 정의를 담는다.
ERD 시각 다이어그램은 별도 도구에서 관리하며, 이 문서는 그 산출물(DDL)이다.

> **참고.** 아래 DDL은 **PostgreSQL** 문법이다(`UUID`, `BYTEA`, `JSONB`, `TIMESTAMPTZ`, `INTEGER[]`).
> 핵심 도메인 DB로 PostgreSQL 사용 확정 ([architecture.md](architecture.md) §3).

## DDL

```sql
CREATE TABLE documents_state (
    document_id UUID NOT NULL,
    yjs_state BYTEA NOT NULL,
    binary_size INTEGER NOT NULL,
    state_hash BYTEA NOT NULL,
    schema_version INTEGER NOT NULL,
    persisted_revision BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE chats (
    chat_id INTEGER NOT NULL,
    meeting_room_id INTEGER NOT NULL,
    team_id INTEGER NOT NULL,
    date TIMESTAMPTZ NOT NULL,
    chat TEXT NOT NULL
);

CREATE TABLE members (
    member_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    team_id INTEGER NOT NULL,
    role VARCHAR NOT NULL,
    authority VARCHAR NOT NULL,
    nickname VARCHAR NOT NULL
);

CREATE TABLE users (
    user_id INTEGER NOT NULL,
    password VARCHAR NOT NULL,
    nickname VARCHAR NOT NULL,
    user_profile_image VARCHAR NULL,
    email VARCHAR NOT NULL,
    phone VARCHAR NOT NULL,
    job_family VARCHAR NOT NULL,
    job_role VARCHAR NOT NULL,
    user_description TEXT NULL,
    sex VARCHAR NOT NULL,
    age INTEGER NOT NULL,
    user_color VARCHAR NULL DEFAULT '#000000',
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ NULL,
    is_deleted BOOLEAN NOT NULL
);

CREATE TABLE teams (
    team_id INTEGER NOT NULL,
    team_name VARCHAR NOT NULL,
    team_description TEXT NULL,
    team_owner_id INTEGER NOT NULL,
    team_profile_image VARCHAR NULL,
    team_color VARCHAR NULL DEFAULT '#000000',
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ NULL,
    is_deleted BOOLEAN NOT NULL,
    team_invite_link VARCHAR NULL
);

CREATE TABLE schedules (
    schedule_id INTEGER NOT NULL,
    team_id INTEGER NOT NULL,
    schedule_category VARCHAR(1000) NULL,
    schedule_title VARCHAR(1000) NULL,
    schedule_description TEXT NULL,
    start_time TIMESTAMPTZ NULL,
    end_time TIMESTAMPTZ NULL,
    user_id_arr INTEGER[] NULL
);

CREATE TABLE alarms (
    alarm_id INTEGER NOT NULL,
    message TEXT NOT NULL,
    alarm_category VARCHAR NOT NULL,
    alarm_property VARCHAR NOT NULL,
    user_id INTEGER NOT NULL
);

CREATE TABLE documents (
    document_id UUID NOT NULL,
    team_id INTEGER NOT NULL,
    document_title VARCHAR(1000) NULL DEFAULT '새 문서',
    final_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ NULL,
    is_deleted BOOLEAN NOT NULL
);

CREATE TABLE sound_texts (
    sound_text_id INTEGER NOT NULL,
    meeting_room_id INTEGER NOT NULL,
    team_id INTEGER NOT NULL,
    date TIMESTAMPTZ NOT NULL,
    sound_text TEXT NOT NULL
);

CREATE TABLE minutes (
    minute_id INTEGER NOT NULL,
    meeting_room_id INTEGER NOT NULL,
    team_id INTEGER NOT NULL,
    date TIMESTAMPTZ NOT NULL,
    minute TEXT NOT NULL,
    is_checked BOOLEAN NOT NULL
);

CREATE TABLE meeting_rooms (
    meeting_room_id INTEGER NOT NULL,
    team_id INTEGER NOT NULL,
    host_id INTEGER NOT NULL,
    meeting_room_name VARCHAR(250) NULL DEFAULT '새 회의',
    create_at TIMESTAMPTZ NOT NULL,
    delete_at TIMESTAMPTZ NULL,
    is_deleted BOOLEAN NOT NULL
);

CREATE TABLE meeting_member (
    meeting_member_id INTEGER NOT NULL,
    meeting_room_id INTEGER NOT NULL,
    member_id INTEGER NOT NULL,
    role VARCHAR(50) NOT NULL,
    is_host BOOLEAN NOT NULL
);

CREATE TABLE documents_version (
    document_version_id UUID NOT NULL,
    document_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    title_snapshot VARCHAR(1000) NULL DEFAULT '새 문서',
    yjs_state BYTEA NOT NULL,
    editor_json JSONB NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    binary_size INTEGER NOT NULL,
    state_hash BYTEA NOT NULL,
    schema_version INTEGER NOT NULL,
    source_version_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL,
    request_id UUID NULL
);

CREATE TABLE voices (
    voice_id INTEGER NOT NULL,
    meeting_room_id INTEGER NOT NULL,
    team_id INTEGER NOT NULL,
    date TIMESTAMPTZ NOT NULL,
    voice BYTEA NOT NULL
);

ALTER TABLE documents_state ADD CONSTRAINT PK_DOCUMENTS_STATE PRIMARY KEY (document_id);
ALTER TABLE chats ADD CONSTRAINT PK_CHATS PRIMARY KEY (chat_id);
ALTER TABLE members ADD CONSTRAINT PK_MEMBERS PRIMARY KEY (member_id);
ALTER TABLE users ADD CONSTRAINT PK_USERS PRIMARY KEY (user_id);
ALTER TABLE teams ADD CONSTRAINT PK_TEAMS PRIMARY KEY (team_id);
ALTER TABLE schedules ADD CONSTRAINT PK_SCHEDULES PRIMARY KEY (schedule_id);
ALTER TABLE alarms ADD CONSTRAINT PK_ALARMS PRIMARY KEY (alarm_id);
ALTER TABLE documents ADD CONSTRAINT PK_DOCUMENTS PRIMARY KEY (document_id);
ALTER TABLE sound_texts ADD CONSTRAINT PK_SOUND_TEXTS PRIMARY KEY (sound_text_id);
ALTER TABLE minutes ADD CONSTRAINT PK_MINUTES PRIMARY KEY (minute_id);
ALTER TABLE meeting_rooms ADD CONSTRAINT PK_MEETING_ROOMS PRIMARY KEY (meeting_room_id);
ALTER TABLE meeting_member ADD CONSTRAINT PK_MEETING_MEMBER PRIMARY KEY (meeting_member_id);
ALTER TABLE documents_version ADD CONSTRAINT PK_DOCUMENTS_VERSION PRIMARY KEY (document_version_id);
ALTER TABLE voices ADD CONSTRAINT PK_VOICES PRIMARY KEY (voice_id);

ALTER TABLE meeting_member
    ADD CONSTRAINT UK_MEETING_MEMBER_ROOM_MEMBER
    UNIQUE (meeting_room_id, member_id);

ALTER TABLE documents_version
    ADD CONSTRAINT UK_DOCUMENTS_VERSION_NUMBER
    UNIQUE (document_id, version_number);

ALTER TABLE documents_version
    ADD CONSTRAINT UK_DOCUMENTS_VERSION_REQUEST
    UNIQUE (document_id, request_id);

ALTER TABLE documents_state ADD CONSTRAINT FK_DOCUMENTS_TO_DOCUMENTS_STATE_1
    FOREIGN KEY (document_id) REFERENCES documents (document_id);

ALTER TABLE chats ADD CONSTRAINT FK_MEETING_ROOMS_TO_CHATS_1
    FOREIGN KEY (meeting_room_id) REFERENCES meeting_rooms (meeting_room_id);

ALTER TABLE members ADD CONSTRAINT FK_USERS_TO_MEMBERS_1
    FOREIGN KEY (user_id) REFERENCES users (user_id);

ALTER TABLE members ADD CONSTRAINT FK_TEAMS_TO_MEMBERS_1
    FOREIGN KEY (team_id) REFERENCES teams (team_id);

ALTER TABLE schedules ADD CONSTRAINT FK_TEAMS_TO_SCHEDULES_1
    FOREIGN KEY (team_id) REFERENCES teams (team_id);

ALTER TABLE alarms ADD CONSTRAINT FK_USERS_TO_ALARMS_1
    FOREIGN KEY (user_id) REFERENCES users (user_id);

ALTER TABLE documents ADD CONSTRAINT FK_TEAMS_TO_DOCUMENTS_1
    FOREIGN KEY (team_id) REFERENCES teams (team_id);

ALTER TABLE sound_texts ADD CONSTRAINT FK_MEETING_ROOMS_TO_SOUND_TEXTS_1
    FOREIGN KEY (meeting_room_id) REFERENCES meeting_rooms (meeting_room_id);

ALTER TABLE minutes ADD CONSTRAINT FK_MEETING_ROOMS_TO_MINUTES_1
    FOREIGN KEY (meeting_room_id) REFERENCES meeting_rooms (meeting_room_id);

ALTER TABLE meeting_rooms ADD CONSTRAINT FK_TEAMS_TO_MEETING_ROOMS_1
    FOREIGN KEY (team_id) REFERENCES teams (team_id);

ALTER TABLE meeting_member ADD CONSTRAINT FK_MEETING_ROOMS_TO_MEETING_MEMBER_1
    FOREIGN KEY (meeting_room_id) REFERENCES meeting_rooms (meeting_room_id);

ALTER TABLE meeting_member ADD CONSTRAINT FK_MEMBERS_TO_MEETING_MEMBER_1
    FOREIGN KEY (member_id) REFERENCES members (member_id);

ALTER TABLE documents_version ADD CONSTRAINT FK_DOCUMENTS_TO_DOCUMENTS_VERSION_1
    FOREIGN KEY (document_id) REFERENCES documents (document_id);

ALTER TABLE documents_version ADD CONSTRAINT FK_DOCUMENTS_VERSION_SOURCE
    FOREIGN KEY (source_version_id)
    REFERENCES documents_version (document_version_id)
    ON DELETE SET NULL;

ALTER TABLE voices ADD CONSTRAINT FK_MEETING_ROOMS_TO_VOICES_1
    FOREIGN KEY (meeting_room_id) REFERENCES meeting_rooms (meeting_room_id);
```
