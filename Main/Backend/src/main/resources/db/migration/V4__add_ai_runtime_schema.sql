-- Move AI-owned schema creation out of the application runtime and into
-- Flyway. The migration role must be allowed to install the pgvector
-- extension; the AI runtime role receives only data access privileges.

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE TABLE public.meeting_history (
    id BIGINT NOT NULL,
    document TEXT NOT NULL,
    embedding public.vector(1536) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_meeting_history PRIMARY KEY (id)
);

CREATE TABLE public.facilitator_report_history (
    id BIGINT NOT NULL,
    document TEXT NOT NULL,
    embedding public.vector(1536) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_facilitator_report_history PRIMARY KEY (id)
);

CREATE INDEX idx_meeting_history_embedding_hnsw
    ON public.meeting_history
    USING hnsw (embedding public.vector_cosine_ops);

CREATE INDEX idx_facilitator_report_history_embedding_hnsw
    ON public.facilitator_report_history
    USING hnsw (embedding public.vector_cosine_ops);

GRANT USAGE
    ON SCHEMA public
    TO ${aiRuntimeRole};

GRANT USAGE
    ON TYPE public.vector
    TO ${aiRuntimeRole};

GRANT SELECT
    ON TABLE public.meeting_rooms
    TO ${aiRuntimeRole};

GRANT SELECT, INSERT, UPDATE
    ON TABLE
        public.processing_jobs,
        public.meeting_minutes,
        public.facilitator_reports,
        public.audio_transcriptions,
        public.meeting_history,
        public.facilitator_report_history
    TO ${aiRuntimeRole};
