# MiniMe — Phase 1 (Foundation)

A glowing companion orb that lives in the bottom-right corner of your desktop.
Click it (or hit the mic) and it expands into one long, continuous conversation.

Everything is local: your conversation never leaves your machine except for the
model call itself.

## Stack

| Layer | Choice |
| --- | --- |
| Desktop shell | Electron (frameless, transparent, always-on-top) |
| UI | Angular 18 + TypeScript (standalone components, signals) |
| Backend | Java 21 + Spring Boot 3.3 (REST + WebSocket) |
| AI framework | LangChain4j |
| Model | Gemini Flash |
| Database | Local PostgreSQL 16 + pgvector |

## What Phase 1 ships

- Floating orb with four states: **breathes** when idle, **ripples** when
  listening, **spins** when thinking, **pulses** when speaking.
- Expanding chat panel with tabs for *Chat*, *What I know about you* and
  *Skills & Reminders* (the latter two are wired placeholders for Phases 2–3).
- One long conversation persisted in Postgres and reloaded on every launch.
- Voice in / voice out via the browser speech APIs (swapped for a local
  pipeline in a later phase).
- Spring Boot backend with a LangChain4j Gemini chat call, plus a `/ws/chat`
  WebSocket that emits `thinking` / `speaking` states for the orb.
- pgvector enabled from day one, so Phase 2 memory needs no migration.

## Layout

```
minime/
├── desktop/        Electron main + preload (the floating window)
├── ui/             Angular renderer (orb, chat panel, tabs, voice)
├── backend/        Spring Boot: chat API, LangChain4j, JPA persistence
├── db/init.sql     pgvector bootstrap
└── docker-compose.yml
```

## Run it

### 1. Database

```bash
docker compose up -d          # Postgres + pgvector on localhost:5433
```

No Docker? Create a local `minime` database owned by `minime/minime` on port
5433 and run `db/init.sql` against it.

### 2. Backend (port 842)

```bash
cd backend
export GEMINI_API_KEY=your_key_here    # optional; omit for offline echo mode
mvn spring-boot:run
```

Check it: `curl http://localhost:842/api/health`

### 3. UI + Electron

**Order matters** — Electron loads the built UI from `ui/dist`, so build it first:

```bash
cd ui && npm install && npm run build     # production build into ui/dist
cd ../desktop && npm install
npm run doctor                            # verifies electron binary + dist + backend
npm start                                 # the floating orb appears bottom-right
```

`http://localhost:4200` is only the *browser* view of the Angular app. The real
companion is the Electron window started from `desktop/` — you do not need the
dev server for it.

Once running, the orb is reachable from the **tray icon** (Show / Hide / Reload /
Quit) and via the global shortcut **Ctrl/Cmd + Shift + M**.

For hot reload during development, run `npm start` in `ui/` (Angular dev server
on :4200) and `npm run start:dev` in `desktop/`.

### Debug switches

| Command / env | Effect |
| --- | --- |
| `npm run doctor` | Checks electron binary, `ui/dist/index.html`, backend on :842 |
| `npm run start:opaque` | Solid non-transparent window + DevTools — proves the window exists |
| `npm run start:debug` | DevTools + renderer console piped to your terminal |
| `MINIME_DEV_URL=...` | Load a URL instead of `ui/dist` |

### Troubleshooting: "no window appeared"

| Symptom | Cause | Fix |
| --- | --- | --- |
| `npm start` exits immediately | Electron binary never downloaded | `npm install electron --force` (or set `ELECTRON_MIRROR`) |
| Process runs, nothing on screen | Transparent window with a failed page load | `npm run start:opaque` and read the terminal log |
| Window shows a "UI is not built" page | `ui/dist` missing | `cd ui && npm run build` |
| Orb visible, chat errors | Backend not running | `cd backend && mvn spring-boot:run` |
| Orb off-screen (multi-monitor) | — | Ctrl/Cmd+Shift+M or tray → Show (position is clamped to the work area) |
| Linux: no window at all | No compositor for transparency | `npm run start:opaque` |


## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Liveness + phase info |
| GET | `/api/chat/history` | The full long conversation |
| POST | `/api/chat/message` | `{ "text": "..." }` → assistant reply |
| DELETE | `/api/chat/history` | Wipe the conversation |
| WS | `/ws/chat` | Send `{ "text": "..." }`, receive state + message events |

## Configuration

`backend/src/main/resources/application.yml` holds the model name, system
prompt, conversation id and datasource. Secrets come from the environment —
see `.env.example`.

## Roadmap

- **Phase 2** — Persistent memory: embeddings in pgvector, a Memory Agent, and
  a real "What I know about you" tab with editable facts.
- **Phase 3** — Skills engine, reminders scheduler, screen service.
- **Phase 4** — Deep research agent + tools.
- **Phase 5** — Local STT/TTS, packaged installers, Ollama fallback provider.
# MiniMe
