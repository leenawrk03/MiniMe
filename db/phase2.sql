-- MiniMe Phase 2 schema: persistent memory (pgvector) + skills & reminders.
-- Run once against the minime database:
--   psql postgresql://minime:minime@localhost:5433/minime -f db/phase2.sql

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------- memory ----
-- Embeddings are written/read with JdbcTemplate (Hibernate has no vector type),
-- so this table is created here rather than by ddl-auto.
CREATE TABLE IF NOT EXISTS memory_fact (
    id           uuid PRIMARY KEY,
    content      text        NOT NULL,
    kind         varchar(32) NOT NULL DEFAULT 'fact',
    source       varchar(32) NOT NULL DEFAULT 'chat',
    confidence   real        NOT NULL DEFAULT 0.8,
    pinned       boolean     NOT NULL DEFAULT false,
    embedding    vector(768),
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz
);

-- Cosine similarity index (ivfflat needs data to be useful; safe to create now).
CREATE INDEX IF NOT EXISTS idx_memory_fact_embedding
    ON memory_fact USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_memory_fact_created ON memory_fact (created_at DESC);

-- ------------------------------------------------------- skills/reminders ---
CREATE TABLE IF NOT EXISTS skill (
    id           uuid PRIMARY KEY,
    name         varchar(120) NOT NULL,
    trigger_word varchar(120),
    instructions text         NOT NULL,
    enabled      boolean      NOT NULL DEFAULT true,
    created_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS reminder (
    id          uuid PRIMARY KEY,
    text        text        NOT NULL,
    due_at      timestamptz NOT NULL,
    done        boolean     NOT NULL DEFAULT false,
    notified    boolean     NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reminder_due ON reminder (due_at) WHERE done = false;
