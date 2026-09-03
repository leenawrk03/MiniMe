# MiniMe — Phase 2

Phase 2 adds **persistent memory (pgvector)** and **custom skills & reminders**, and turns the two
placeholder tabs into real, working screens.

## Files to CREATE

**Database**
- `db/phase2.sql` — `memory_fact` (with `vector(768)` + ivfflat index), `skill`, `reminder`

**Backend (`backend/src/main/java/dev/minime/`)**
- `memory/MemoryFact.java` — API-facing record for one remembered fact
- `memory/MemoryRepository.java` — JDBC + pgvector: insert, cosine search, dedupe, pin, delete
- `memory/EmbeddingProvider.java` — 768-dim embedding contract
- `memory/GeminiEmbeddingProvider.java` — Gemini embeddings with local fallback
- `memory/LocalEmbeddingProvider.java` — offline hashed embeddings (no API key needed)
- `memory/MemoryService.java` — fact extraction, recall, prompt block, CRUD
- `config/MemoryConfig.java` — picks the embedding provider at startup
- `skills/SkillEntity.java`, `skills/SkillRepository.java`
- `skills/ReminderEntity.java`, `skills/ReminderRepository.java`
- `skills/SkillService.java` — skill/reminder CRUD + prompt blocks
- `skills/ReminderScheduler.java` — polls every 20s, pushes due reminders over WebSocket
- `web/MemoryController.java` — `/api/memory`
- `web/SkillController.java` — `/api/skills`, `/api/reminders`

**UI (`ui/src/app/memory/`)**
- `memory.models.ts` — `MemoryFact`, `Skill`, `Reminder`
- `memory.service.ts` — HTTP client for all Phase 2 endpoints
- `memory-tab.component.ts` — "What I know about you" (add / edit / pin / forget)
- `skills-tab.component.ts` — skills + reminders management
- `reminder-socket.service.ts` — listens for pushed reminder events
- `tabs.css` — shared tab styling

## Files to UPDATE

- `backend/.../MiniMeApplication.java` — `@EnableScheduling`
- `backend/.../chat/ChatService.java` — inject recalled memory + active skills + open reminders into
  the prompt, then extract new facts after each reply
- `backend/.../config/MiniMeProperties.java` — new `minime.memory.*` block
- `backend/.../web/ChatSocketHandler.java` — added `broadcast()` for reminder events
- `backend/src/main/resources/application.yml` — memory settings + richer system prompt
- `ui/src/app/app.component.ts` / `app.component.html` — mount the two real tabs, speak due reminders
- `docker-compose.yml` — also run `db/phase2.sql` on first boot

## Setup (on top of Phase 1)

```bash
# 1. Apply the Phase 2 schema to the existing database
psql postgresql://minime:minime@localhost:5433/minime -f db/phase2.sql
#    (or: docker compose down -v && docker compose up -d  to recreate from scratch)

# 2. Backend
cd backend && mvn spring-boot:run

# 3. UI + desktop
cd ../ui && npm install && npm run build
cd ../desktop && npm start
```

## API reference

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/memory` | all remembered facts |
| GET | `/api/memory/search?q=` | semantic recall |
| POST | `/api/memory` | teach a fact manually |
| PATCH | `/api/memory/{id}` | edit content / pin |
| DELETE | `/api/memory/{id}` · `/api/memory` | forget one / forget all |
| GET/POST | `/api/skills` | list / create skill |
| PATCH | `/api/skills/{id}?enabled=` | enable / disable |
| DELETE | `/api/skills/{id}` | delete skill |
| GET/POST | `/api/reminders` | list / create reminder |
| PATCH | `/api/reminders/{id}?done=` | complete / reopen |
| DELETE | `/api/reminders/{id}` | delete reminder |

## How memory works

1. You send a message → MiniMe embeds it and pulls the top 6 cosine-nearest facts (similarity ≥ 0.55,
   pinned facts first) into the system prompt.
2. Gemini answers with that context.
3. After replying, an extraction pass distils durable facts from your message; anything ≥ 0.93 similar
   to an existing fact is skipped as a duplicate.
4. Without a `GEMINI_API_KEY`, a local hashed embedder plus pattern-based extraction keeps memory
   working offline.

Everything stays in your local Postgres — no cloud storage, no third-party sync.
