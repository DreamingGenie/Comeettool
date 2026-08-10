--
-- PostgreSQL database dump
--

\restrict Max9ChO6AhXmqORL7mok8JolWNwPO63I9aqlbHvonamAMpbMqFA3Hd245xJiLed

-- Dumped from database version 17.10 (Debian 17.10-1.pgdg12+1)
-- Dumped by pg_dump version 17.10 (Debian 17.10-1.pgdg12+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.schedules DROP CONSTRAINT IF EXISTS fk_users_to_schedules_1;
ALTER TABLE IF EXISTS ONLY public.members DROP CONSTRAINT IF EXISTS fk_users_to_members_1;
ALTER TABLE IF EXISTS ONLY public.alarms DROP CONSTRAINT IF EXISTS fk_users_to_alarms_1;
ALTER TABLE IF EXISTS ONLY public.team_roles DROP CONSTRAINT IF EXISTS fk_teams_to_team_roles_1;
ALTER TABLE IF EXISTS ONLY public.schedules DROP CONSTRAINT IF EXISTS fk_teams_to_schedules_1;
ALTER TABLE IF EXISTS ONLY public.members DROP CONSTRAINT IF EXISTS fk_teams_to_members_1;
ALTER TABLE IF EXISTS ONLY public.meeting_rooms DROP CONSTRAINT IF EXISTS fk_teams_to_meeting_rooms_1;
ALTER TABLE IF EXISTS ONLY public.documents DROP CONSTRAINT IF EXISTS fk_teams_to_documents_1;
ALTER TABLE IF EXISTS ONLY public.members DROP CONSTRAINT IF EXISTS fk_team_roles_to_members_1;
ALTER TABLE IF EXISTS ONLY public.participants DROP CONSTRAINT IF EXISTS fk_members_to_participants_1;
ALTER TABLE IF EXISTS ONLY public.processing_jobs DROP CONSTRAINT IF EXISTS fk_meeting_rooms_to_processing_jobs_1;
ALTER TABLE IF EXISTS ONLY public.participants DROP CONSTRAINT IF EXISTS fk_meeting_rooms_to_participants_1;
ALTER TABLE IF EXISTS ONLY public.minutes DROP CONSTRAINT IF EXISTS fk_meeting_rooms_to_minutes_1;
ALTER TABLE IF EXISTS ONLY public.meeting_minutes DROP CONSTRAINT IF EXISTS fk_meeting_rooms_to_meeting_minutes_1;
ALTER TABLE IF EXISTS ONLY public.facilitator_reports DROP CONSTRAINT IF EXISTS fk_meeting_rooms_to_facilitator_reports_1;
ALTER TABLE IF EXISTS ONLY public.chats DROP CONSTRAINT IF EXISTS fk_meeting_rooms_to_chats_1;
ALTER TABLE IF EXISTS ONLY public.audio_transcriptions DROP CONSTRAINT IF EXISTS fk_meeting_rooms_to_audio_transcriptions_1;
ALTER TABLE IF EXISTS ONLY public.documents_version DROP CONSTRAINT IF EXISTS fk_documents_version_source;
ALTER TABLE IF EXISTS ONLY public.documents_version DROP CONSTRAINT IF EXISTS fk_documents_to_documents_version_1;
ALTER TABLE IF EXISTS ONLY public.documents_state DROP CONSTRAINT IF EXISTS fk_documents_to_documents_state_1;
DROP INDEX IF EXISTS public.uk_users_email_active;
DROP INDEX IF EXISTS public.idx_team_roles_team_id;
DROP INDEX IF EXISTS public.idx_schedules_user_id_arr;
DROP INDEX IF EXISTS public.idx_schedules_team_id;
DROP INDEX IF EXISTS public.idx_processing_jobs_meeting_id;
DROP INDEX IF EXISTS public.idx_participants_member_id;
DROP INDEX IF EXISTS public.idx_minutes_meeting_room_id;
DROP INDEX IF EXISTS public.idx_members_user_id;
DROP INDEX IF EXISTS public.idx_members_team_role_id;
DROP INDEX IF EXISTS public.idx_meeting_rooms_team_id;
DROP INDEX IF EXISTS public.idx_meeting_history_embedding_hnsw;
DROP INDEX IF EXISTS public.idx_invitations_target_user_id;
DROP INDEX IF EXISTS public.idx_facilitator_report_history_embedding_hnsw;
DROP INDEX IF EXISTS public.idx_documents_version_source_version_id;
DROP INDEX IF EXISTS public.idx_documents_team_id;
DROP INDEX IF EXISTS public.idx_chats_meeting_room_id;
DROP INDEX IF EXISTS public.idx_alarms_user_id;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY public.team_roles DROP CONSTRAINT IF EXISTS uk_team_roles_team_name;
ALTER TABLE IF EXISTS ONLY public.participants DROP CONSTRAINT IF EXISTS uk_participants_room_member;
ALTER TABLE IF EXISTS ONLY public.members DROP CONSTRAINT IF EXISTS uk_members_team_user;
ALTER TABLE IF EXISTS ONLY public.invitations DROP CONSTRAINT IF EXISTS uk_invitations_team_target;
ALTER TABLE IF EXISTS ONLY public.documents_version DROP CONSTRAINT IF EXISTS uk_documents_version_request;
ALTER TABLE IF EXISTS ONLY public.documents_version DROP CONSTRAINT IF EXISTS uk_documents_version_number;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS pk_users;
ALTER TABLE IF EXISTS ONLY public.teams DROP CONSTRAINT IF EXISTS pk_teams;
ALTER TABLE IF EXISTS ONLY public.team_roles DROP CONSTRAINT IF EXISTS pk_team_roles;
ALTER TABLE IF EXISTS ONLY public.schedules DROP CONSTRAINT IF EXISTS pk_schedules;
ALTER TABLE IF EXISTS ONLY public.processing_jobs DROP CONSTRAINT IF EXISTS pk_processing_jobs;
ALTER TABLE IF EXISTS ONLY public.participants DROP CONSTRAINT IF EXISTS pk_participants;
ALTER TABLE IF EXISTS ONLY public.minutes DROP CONSTRAINT IF EXISTS pk_minutes;
ALTER TABLE IF EXISTS ONLY public.members DROP CONSTRAINT IF EXISTS pk_members;
ALTER TABLE IF EXISTS ONLY public.meeting_rooms DROP CONSTRAINT IF EXISTS pk_meeting_rooms;
ALTER TABLE IF EXISTS ONLY public.meeting_minutes DROP CONSTRAINT IF EXISTS pk_meeting_minutes;
ALTER TABLE IF EXISTS ONLY public.meeting_history DROP CONSTRAINT IF EXISTS pk_meeting_history;
ALTER TABLE IF EXISTS ONLY public.invitations DROP CONSTRAINT IF EXISTS pk_invitations;
ALTER TABLE IF EXISTS ONLY public.facilitator_reports DROP CONSTRAINT IF EXISTS pk_facilitator_reports;
ALTER TABLE IF EXISTS ONLY public.facilitator_report_history DROP CONSTRAINT IF EXISTS pk_facilitator_report_history;
ALTER TABLE IF EXISTS ONLY public.documents_version DROP CONSTRAINT IF EXISTS pk_documents_version;
ALTER TABLE IF EXISTS ONLY public.documents_state DROP CONSTRAINT IF EXISTS pk_documents_state;
ALTER TABLE IF EXISTS ONLY public.documents DROP CONSTRAINT IF EXISTS pk_documents;
ALTER TABLE IF EXISTS ONLY public.chats DROP CONSTRAINT IF EXISTS pk_chats;
ALTER TABLE IF EXISTS ONLY public.audio_transcriptions DROP CONSTRAINT IF EXISTS pk_audio_transcriptions;
ALTER TABLE IF EXISTS ONLY public.alarms DROP CONSTRAINT IF EXISTS pk_alarms;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.teams;
DROP TABLE IF EXISTS public.team_roles;
DROP TABLE IF EXISTS public.schedules;
DROP TABLE IF EXISTS public.processing_jobs;
DROP TABLE IF EXISTS public.participants;
DROP TABLE IF EXISTS public.minutes;
DROP TABLE IF EXISTS public.members;
DROP TABLE IF EXISTS public.meeting_rooms;
DROP TABLE IF EXISTS public.meeting_minutes;
DROP TABLE IF EXISTS public.meeting_history;
DROP TABLE IF EXISTS public.invitations;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP TABLE IF EXISTS public.facilitator_reports;
DROP TABLE IF EXISTS public.facilitator_report_history;
DROP TABLE IF EXISTS public.documents_version;
DROP TABLE IF EXISTS public.documents_state;
DROP TABLE IF EXISTS public.documents;
DROP TABLE IF EXISTS public.chats;
DROP TABLE IF EXISTS public.audio_transcriptions;
DROP TABLE IF EXISTS public.alarms;
DROP EXTENSION IF EXISTS vector;
--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: alarms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.alarms (
    alarm_id bigint NOT NULL,
    message text NOT NULL,
    alarm_category character varying NOT NULL,
    alarm_property character varying NOT NULL,
    user_id bigint NOT NULL
);


--
-- Name: alarms_alarm_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.alarms ALTER COLUMN alarm_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.alarms_alarm_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: audio_transcriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audio_transcriptions (
    meeting_id bigint NOT NULL,
    transcript jsonb DEFAULT '[]'::jsonb NOT NULL,
    md_url character varying,
    pdf_url character varying,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: chats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chats (
    chat_id bigint NOT NULL,
    meeting_room_id bigint NOT NULL,
    team_id bigint NOT NULL,
    date timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    chat text NOT NULL
);


--
-- Name: chats_chat_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.chats ALTER COLUMN chat_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.chats_chat_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documents (
    document_id uuid NOT NULL,
    team_id bigint NOT NULL,
    document_title character varying(1000) DEFAULT '새 문서'::character varying NOT NULL,
    final_version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone,
    is_deleted boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_documents_final_version CHECK ((final_version >= 0))
);


--
-- Name: documents_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documents_state (
    document_id uuid NOT NULL,
    yjs_state bytea NOT NULL,
    binary_size integer NOT NULL,
    state_hash bytea NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    persisted_revision bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_documents_state_binary_size CHECK ((binary_size = octet_length(yjs_state))),
    CONSTRAINT ck_documents_state_hash_length CHECK ((octet_length(state_hash) = 32)),
    CONSTRAINT ck_documents_state_persisted_revision CHECK ((persisted_revision >= 0)),
    CONSTRAINT ck_documents_state_schema_version CHECK ((schema_version > 0))
);


--
-- Name: documents_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documents_version (
    document_version_id uuid NOT NULL,
    document_id uuid NOT NULL,
    version_number integer NOT NULL,
    title_snapshot character varying(1000) DEFAULT '새 문서'::character varying NOT NULL,
    yjs_state bytea NOT NULL,
    editor_json jsonb NOT NULL,
    trigger_type character varying(30) NOT NULL,
    binary_size integer NOT NULL,
    state_hash bytea NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    source_version_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    request_id uuid,
    CONSTRAINT ck_documents_version_binary_size CHECK ((binary_size = octet_length(yjs_state))),
    CONSTRAINT ck_documents_version_hash_length CHECK ((octet_length(state_hash) = 32)),
    CONSTRAINT ck_documents_version_number CHECK ((version_number > 0)),
    CONSTRAINT ck_documents_version_schema_version CHECK ((schema_version > 0))
);


--
-- Name: facilitator_report_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facilitator_report_history (
    id bigint NOT NULL,
    document text NOT NULL,
    embedding public.vector(1536) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: facilitator_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facilitator_reports (
    meeting_id bigint NOT NULL,
    title character varying NOT NULL,
    meeting_type character varying,
    overall_review text NOT NULL,
    participation_comment text NOT NULL,
    participation_stats jsonb DEFAULT '[]'::jsonb NOT NULL,
    quality_evaluation jsonb NOT NULL,
    strengths jsonb DEFAULT '[]'::jsonb NOT NULL,
    improvements jsonb DEFAULT '[]'::jsonb NOT NULL,
    decision_process_checks jsonb DEFAULT '[]'::jsonb NOT NULL,
    unresolved_issues_evaluation jsonb DEFAULT '[]'::jsonb NOT NULL,
    next_meeting_suggestions jsonb DEFAULT '[]'::jsonb NOT NULL,
    md_url character varying,
    pdf_url character varying,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invitations (
    invitation_id uuid NOT NULL,
    team_id bigint NOT NULL,
    inviter_id bigint NOT NULL,
    target_user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL
);


--
-- Name: meeting_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meeting_history (
    id bigint NOT NULL,
    document text NOT NULL,
    embedding public.vector(1536) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: meeting_minutes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meeting_minutes (
    meeting_id bigint NOT NULL,
    title character varying NOT NULL,
    summary text NOT NULL,
    topics jsonb DEFAULT '[]'::jsonb NOT NULL,
    decisions jsonb DEFAULT '[]'::jsonb NOT NULL,
    action_items jsonb DEFAULT '[]'::jsonb NOT NULL,
    open_issues jsonb DEFAULT '[]'::jsonb NOT NULL,
    is_confirmed boolean DEFAULT false NOT NULL,
    confirmed_at timestamp with time zone,
    md_url character varying,
    pdf_url character varying,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: meeting_rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meeting_rooms (
    meeting_room_id bigint NOT NULL,
    team_id bigint NOT NULL,
    host_id bigint NOT NULL,
    meeting_room_name character varying(250) DEFAULT '새 회의'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone,
    is_deleted boolean DEFAULT false NOT NULL
);


--
-- Name: meeting_rooms_meeting_room_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.meeting_rooms ALTER COLUMN meeting_room_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.meeting_rooms_meeting_room_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.members (
    member_id bigint NOT NULL,
    user_id bigint NOT NULL,
    team_id bigint NOT NULL,
    authority character varying NOT NULL,
    team_role_id bigint,
    nickname character varying NOT NULL
);


--
-- Name: members_member_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.members ALTER COLUMN member_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.members_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: minutes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.minutes (
    minute_id bigint NOT NULL,
    meeting_room_id bigint NOT NULL,
    team_id bigint NOT NULL,
    date timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    minute text NOT NULL,
    is_checked boolean NOT NULL
);


--
-- Name: minutes_minute_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.minutes ALTER COLUMN minute_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.minutes_minute_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.participants (
    participant_id bigint NOT NULL,
    meeting_room_id bigint NOT NULL,
    member_id bigint NOT NULL,
    participants_role character varying(50),
    is_in_meeting boolean NOT NULL
);


--
-- Name: participants_participant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.participants ALTER COLUMN participant_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.participants_participant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: processing_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.processing_jobs (
    id uuid NOT NULL,
    meeting_id bigint NOT NULL,
    status character varying DEFAULT 'pending'::character varying NOT NULL,
    error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schedules (
    schedule_id bigint NOT NULL,
    team_id bigint NOT NULL,
    creator_id bigint NOT NULL,
    schedule_category character varying(1000),
    schedule_title character varying(1000),
    schedule_description text,
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    user_id_arr bigint[],
    is_deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    schedule_color character varying DEFAULT '#566FEA'::character varying NOT NULL
);


--
-- Name: schedules_schedule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.schedules ALTER COLUMN schedule_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.schedules_schedule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: team_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team_roles (
    team_role_id bigint NOT NULL,
    team_id bigint NOT NULL,
    role_name character varying NOT NULL,
    color character varying,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: team_roles_team_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.team_roles ALTER COLUMN team_role_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.team_roles_team_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: teams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teams (
    team_id bigint NOT NULL,
    team_name character varying NOT NULL,
    team_description text,
    team_owner_id bigint NOT NULL,
    team_profile_image character varying,
    team_color character varying DEFAULT '#000000'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone,
    is_deleted boolean DEFAULT false NOT NULL,
    team_invite_link character varying
);


--
-- Name: teams_team_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.teams ALTER COLUMN team_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.teams_team_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id bigint NOT NULL,
    password_hash character varying(255) NOT NULL,
    nickname character varying,
    user_profile_image character varying,
    email character varying NOT NULL,
    phone character varying,
    job_family character varying,
    job_role character varying,
    user_description text,
    sex character varying,
    age integer,
    user_color character varying DEFAULT '#000000'::character varying NOT NULL,
    space_order text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone,
    is_deleted boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_users_age CHECK ((age = ANY (ARRAY[10, 20, 30, 40, 50, 60]))),
    CONSTRAINT ck_users_nickname_length CHECK ((char_length((nickname)::text) <= 20)),
    CONSTRAINT ck_users_sex CHECK (((sex)::text = ANY ((ARRAY['M'::character varying, 'F'::character varying])::text[])))
);


--
-- Name: users_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.users ALTER COLUMN user_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.users_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Data for Name: alarms; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.alarms (alarm_id, message, alarm_category, alarm_property, user_id) FROM stdin;
\.


--
-- Data for Name: audio_transcriptions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.audio_transcriptions (meeting_id, transcript, md_url, pdf_url, created_at) FROM stdin;
\.


--
-- Data for Name: chats; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chats (chat_id, meeting_room_id, team_id, date, chat) FROM stdin;
\.


--
-- Data for Name: documents; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.documents (document_id, team_id, document_title, final_version, created_at, updated_at, deleted_at, is_deleted) FROM stdin;
\.


--
-- Data for Name: documents_state; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.documents_state (document_id, yjs_state, binary_size, state_hash, schema_version, persisted_revision, updated_at) FROM stdin;
\.


--
-- Data for Name: documents_version; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.documents_version (document_version_id, document_id, version_number, title_snapshot, yjs_state, editor_json, trigger_type, binary_size, state_hash, schema_version, source_version_id, created_at, request_id) FROM stdin;
\.


--
-- Data for Name: facilitator_report_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.facilitator_report_history (id, document, embedding, created_at) FROM stdin;
\.


--
-- Data for Name: facilitator_reports; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.facilitator_reports (meeting_id, title, meeting_type, overall_review, participation_comment, participation_stats, quality_evaluation, strengths, improvements, decision_process_checks, unresolved_issues_evaluation, next_meeting_suggestions, md_url, pdf_url, created_at) FROM stdin;
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	initial schema	SQL	V1__initial_schema.sql	-1975499423	postgres	2026-08-09 16:04:15.067888	28	t
2	2	add schedules	SQL	V2__add_schedules.sql	-1829064823	postgres	2026-08-09 16:04:15.195386	5	t
3	3	add remaining schema	SQL	V3__add_remaining_schema.sql	-272181323	postgres	2026-08-09 16:04:15.247346	14	t
4	4	add ai runtime schema	SQL	V4__add_ai_runtime_schema.sql	-1888269916	postgres	2026-08-09 16:04:15.311304	15	t
\.


--
-- Data for Name: invitations; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.invitations (invitation_id, team_id, inviter_id, target_user_id, created_at, expires_at) FROM stdin;
\.


--
-- Data for Name: meeting_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.meeting_history (id, document, embedding, created_at) FROM stdin;
\.


--
-- Data for Name: meeting_minutes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.meeting_minutes (meeting_id, title, summary, topics, decisions, action_items, open_issues, is_confirmed, confirmed_at, md_url, pdf_url, created_at) FROM stdin;
\.


--
-- Data for Name: meeting_rooms; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.meeting_rooms (meeting_room_id, team_id, host_id, meeting_room_name, created_at, deleted_at, is_deleted) FROM stdin;
\.


--
-- Data for Name: members; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.members (member_id, user_id, team_id, authority, team_role_id, nickname) FROM stdin;
\.


--
-- Data for Name: minutes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.minutes (minute_id, meeting_room_id, team_id, date, minute, is_checked) FROM stdin;
\.


--
-- Data for Name: participants; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.participants (participant_id, meeting_room_id, member_id, participants_role, is_in_meeting) FROM stdin;
\.


--
-- Data for Name: processing_jobs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.processing_jobs (id, meeting_id, status, error_message, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: schedules; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.schedules (schedule_id, team_id, creator_id, schedule_category, schedule_title, schedule_description, start_time, end_time, user_id_arr, is_deleted, deleted_at, schedule_color) FROM stdin;
\.


--
-- Data for Name: team_roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.team_roles (team_role_id, team_id, role_name, color, created_at) FROM stdin;
\.


--
-- Data for Name: teams; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.teams (team_id, team_name, team_description, team_owner_id, team_profile_image, team_color, created_at, deleted_at, is_deleted, team_invite_link) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (user_id, password_hash, nickname, user_profile_image, email, phone, job_family, job_role, user_description, sex, age, user_color, space_order, created_at, deleted_at, is_deleted) FROM stdin;
\.


--
-- Name: alarms_alarm_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.alarms_alarm_id_seq', 1, false);


--
-- Name: chats_chat_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.chats_chat_id_seq', 1, false);


--
-- Name: meeting_rooms_meeting_room_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.meeting_rooms_meeting_room_id_seq', 1, false);


--
-- Name: members_member_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.members_member_id_seq', 1, false);


--
-- Name: minutes_minute_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.minutes_minute_id_seq', 1, false);


--
-- Name: participants_participant_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.participants_participant_id_seq', 1, false);


--
-- Name: schedules_schedule_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.schedules_schedule_id_seq', 1, false);


--
-- Name: team_roles_team_role_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.team_roles_team_role_id_seq', 1, false);


--
-- Name: teams_team_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.teams_team_id_seq', 1, false);


--
-- Name: users_user_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.users_user_id_seq', 1, false);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: alarms pk_alarms; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alarms
    ADD CONSTRAINT pk_alarms PRIMARY KEY (alarm_id);


--
-- Name: audio_transcriptions pk_audio_transcriptions; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audio_transcriptions
    ADD CONSTRAINT pk_audio_transcriptions PRIMARY KEY (meeting_id);


--
-- Name: chats pk_chats; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chats
    ADD CONSTRAINT pk_chats PRIMARY KEY (chat_id);


--
-- Name: documents pk_documents; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT pk_documents PRIMARY KEY (document_id);


--
-- Name: documents_state pk_documents_state; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents_state
    ADD CONSTRAINT pk_documents_state PRIMARY KEY (document_id);


--
-- Name: documents_version pk_documents_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents_version
    ADD CONSTRAINT pk_documents_version PRIMARY KEY (document_version_id);


--
-- Name: facilitator_report_history pk_facilitator_report_history; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facilitator_report_history
    ADD CONSTRAINT pk_facilitator_report_history PRIMARY KEY (id);


--
-- Name: facilitator_reports pk_facilitator_reports; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facilitator_reports
    ADD CONSTRAINT pk_facilitator_reports PRIMARY KEY (meeting_id);


--
-- Name: invitations pk_invitations; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT pk_invitations PRIMARY KEY (invitation_id);


--
-- Name: meeting_history pk_meeting_history; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meeting_history
    ADD CONSTRAINT pk_meeting_history PRIMARY KEY (id);


--
-- Name: meeting_minutes pk_meeting_minutes; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meeting_minutes
    ADD CONSTRAINT pk_meeting_minutes PRIMARY KEY (meeting_id);


--
-- Name: meeting_rooms pk_meeting_rooms; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meeting_rooms
    ADD CONSTRAINT pk_meeting_rooms PRIMARY KEY (meeting_room_id);


--
-- Name: members pk_members; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT pk_members PRIMARY KEY (member_id);


--
-- Name: minutes pk_minutes; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.minutes
    ADD CONSTRAINT pk_minutes PRIMARY KEY (minute_id);


--
-- Name: participants pk_participants; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participants
    ADD CONSTRAINT pk_participants PRIMARY KEY (participant_id);


--
-- Name: processing_jobs pk_processing_jobs; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.processing_jobs
    ADD CONSTRAINT pk_processing_jobs PRIMARY KEY (id);


--
-- Name: schedules pk_schedules; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedules
    ADD CONSTRAINT pk_schedules PRIMARY KEY (schedule_id);


--
-- Name: team_roles pk_team_roles; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_roles
    ADD CONSTRAINT pk_team_roles PRIMARY KEY (team_role_id);


--
-- Name: teams pk_teams; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT pk_teams PRIMARY KEY (team_id);


--
-- Name: users pk_users; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT pk_users PRIMARY KEY (user_id);


--
-- Name: documents_version uk_documents_version_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents_version
    ADD CONSTRAINT uk_documents_version_number UNIQUE (document_id, version_number);


--
-- Name: documents_version uk_documents_version_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents_version
    ADD CONSTRAINT uk_documents_version_request UNIQUE (document_id, request_id);


--
-- Name: invitations uk_invitations_team_target; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT uk_invitations_team_target UNIQUE (team_id, target_user_id);


--
-- Name: members uk_members_team_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT uk_members_team_user UNIQUE (team_id, user_id);


--
-- Name: participants uk_participants_room_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participants
    ADD CONSTRAINT uk_participants_room_member UNIQUE (meeting_room_id, member_id);


--
-- Name: team_roles uk_team_roles_team_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_roles
    ADD CONSTRAINT uk_team_roles_team_name UNIQUE (team_id, role_name);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_alarms_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alarms_user_id ON public.alarms USING btree (user_id);


--
-- Name: idx_chats_meeting_room_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chats_meeting_room_id ON public.chats USING btree (meeting_room_id);


--
-- Name: idx_documents_team_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documents_team_id ON public.documents USING btree (team_id);


--
-- Name: idx_documents_version_source_version_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documents_version_source_version_id ON public.documents_version USING btree (source_version_id);


--
-- Name: idx_facilitator_report_history_embedding_hnsw; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_facilitator_report_history_embedding_hnsw ON public.facilitator_report_history USING hnsw (embedding public.vector_cosine_ops);


--
-- Name: idx_invitations_target_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invitations_target_user_id ON public.invitations USING btree (target_user_id);


--
-- Name: idx_meeting_history_embedding_hnsw; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meeting_history_embedding_hnsw ON public.meeting_history USING hnsw (embedding public.vector_cosine_ops);


--
-- Name: idx_meeting_rooms_team_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meeting_rooms_team_id ON public.meeting_rooms USING btree (team_id);


--
-- Name: idx_members_team_role_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_members_team_role_id ON public.members USING btree (team_role_id);


--
-- Name: idx_members_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_members_user_id ON public.members USING btree (user_id);


--
-- Name: idx_minutes_meeting_room_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_minutes_meeting_room_id ON public.minutes USING btree (meeting_room_id);


--
-- Name: idx_participants_member_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_participants_member_id ON public.participants USING btree (member_id);


--
-- Name: idx_processing_jobs_meeting_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_processing_jobs_meeting_id ON public.processing_jobs USING btree (meeting_id);


--
-- Name: idx_schedules_team_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_schedules_team_id ON public.schedules USING btree (team_id);


--
-- Name: idx_schedules_user_id_arr; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_schedules_user_id_arr ON public.schedules USING gin (user_id_arr);


--
-- Name: idx_team_roles_team_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_team_roles_team_id ON public.team_roles USING btree (team_id);


--
-- Name: uk_users_email_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_users_email_active ON public.users USING btree (email) WHERE (is_deleted = false);


--
-- Name: documents_state fk_documents_to_documents_state_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents_state
    ADD CONSTRAINT fk_documents_to_documents_state_1 FOREIGN KEY (document_id) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: documents_version fk_documents_to_documents_version_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents_version
    ADD CONSTRAINT fk_documents_to_documents_version_1 FOREIGN KEY (document_id) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: documents_version fk_documents_version_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents_version
    ADD CONSTRAINT fk_documents_version_source FOREIGN KEY (source_version_id) REFERENCES public.documents_version(document_version_id) ON DELETE SET NULL;


--
-- Name: audio_transcriptions fk_meeting_rooms_to_audio_transcriptions_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audio_transcriptions
    ADD CONSTRAINT fk_meeting_rooms_to_audio_transcriptions_1 FOREIGN KEY (meeting_id) REFERENCES public.meeting_rooms(meeting_room_id) ON DELETE RESTRICT;


--
-- Name: chats fk_meeting_rooms_to_chats_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chats
    ADD CONSTRAINT fk_meeting_rooms_to_chats_1 FOREIGN KEY (meeting_room_id) REFERENCES public.meeting_rooms(meeting_room_id) ON DELETE RESTRICT;


--
-- Name: facilitator_reports fk_meeting_rooms_to_facilitator_reports_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facilitator_reports
    ADD CONSTRAINT fk_meeting_rooms_to_facilitator_reports_1 FOREIGN KEY (meeting_id) REFERENCES public.meeting_rooms(meeting_room_id) ON DELETE RESTRICT;


--
-- Name: meeting_minutes fk_meeting_rooms_to_meeting_minutes_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meeting_minutes
    ADD CONSTRAINT fk_meeting_rooms_to_meeting_minutes_1 FOREIGN KEY (meeting_id) REFERENCES public.meeting_rooms(meeting_room_id) ON DELETE RESTRICT;


--
-- Name: minutes fk_meeting_rooms_to_minutes_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.minutes
    ADD CONSTRAINT fk_meeting_rooms_to_minutes_1 FOREIGN KEY (meeting_room_id) REFERENCES public.meeting_rooms(meeting_room_id) ON DELETE RESTRICT;


--
-- Name: participants fk_meeting_rooms_to_participants_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participants
    ADD CONSTRAINT fk_meeting_rooms_to_participants_1 FOREIGN KEY (meeting_room_id) REFERENCES public.meeting_rooms(meeting_room_id) ON DELETE CASCADE;


--
-- Name: processing_jobs fk_meeting_rooms_to_processing_jobs_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.processing_jobs
    ADD CONSTRAINT fk_meeting_rooms_to_processing_jobs_1 FOREIGN KEY (meeting_id) REFERENCES public.meeting_rooms(meeting_room_id) ON DELETE RESTRICT;


--
-- Name: participants fk_members_to_participants_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participants
    ADD CONSTRAINT fk_members_to_participants_1 FOREIGN KEY (member_id) REFERENCES public.members(member_id) ON DELETE CASCADE;


--
-- Name: members fk_team_roles_to_members_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT fk_team_roles_to_members_1 FOREIGN KEY (team_role_id) REFERENCES public.team_roles(team_role_id) ON DELETE SET NULL;


--
-- Name: documents fk_teams_to_documents_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT fk_teams_to_documents_1 FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE RESTRICT;


--
-- Name: meeting_rooms fk_teams_to_meeting_rooms_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meeting_rooms
    ADD CONSTRAINT fk_teams_to_meeting_rooms_1 FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE RESTRICT;


--
-- Name: members fk_teams_to_members_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT fk_teams_to_members_1 FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE RESTRICT;


--
-- Name: schedules fk_teams_to_schedules_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedules
    ADD CONSTRAINT fk_teams_to_schedules_1 FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE RESTRICT;


--
-- Name: team_roles fk_teams_to_team_roles_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_roles
    ADD CONSTRAINT fk_teams_to_team_roles_1 FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE RESTRICT;


--
-- Name: alarms fk_users_to_alarms_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alarms
    ADD CONSTRAINT fk_users_to_alarms_1 FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;


--
-- Name: members fk_users_to_members_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT fk_users_to_members_1 FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;


--
-- Name: schedules fk_users_to_schedules_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedules
    ADD CONSTRAINT fk_users_to_schedules_1 FOREIGN KEY (creator_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict Max9ChO6AhXmqORL7mok8JolWNwPO63I9aqlbHvonamAMpbMqFA3Hd245xJiLed

