-- MiniMe local database bootstrap (Phase 1).
-- pgvector is enabled now so Phase 2 memory embeddings need no migration.
CREATE EXTENSION IF NOT EXISTS vector;

-- Hibernate creates chat_message on first boot (ddl-auto: update).
-- Phase 2 will add:
--   CREATE TABLE memory_fact (id uuid PRIMARY KEY, content text, embedding vector(768), ...);
