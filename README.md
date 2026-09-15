# MiniMe

> A personal AI desktop companion that lives as a glowing orb on your macOS desktop.

MiniMe is a desktop AI assistant designed to feel less like a traditional chatbot and more like a small personal companion.

It combines a conversational AI backend, persistent memory, voice interaction, a floating Electron orb, and Model Context Protocol (MCP) integrations to let the assistant interact with the user's computer and GitHub account.

---

## ✨ Features

### 🟣 Desktop AI Orb

MiniMe runs as an always-on-top floating orb on macOS.

- Floating bottom-right desktop assistant
- Transparent Electron window
- Always-on-top behavior
- Click to expand into the chat interface
- Animated assistant states
  - Idle
  - Listening
  - Thinking
  - Speaking
- Designed to remain available while other applications are maximized

---

### 💬 AI Chat

MiniMe provides conversational interaction through a Spring Boot backend and Gemini.

The assistant can:

- Understand natural-language requests
- Maintain conversational context
- Use available tools when appropriate
- Produce concise conversational responses
- Fall back between configured Gemini models

---

### 🧠 Persistent Memory

MiniMe includes a persistent memory system backed by PostgreSQL and vector search.

The memory layer allows the assistant to:

- Store useful facts
- Recall relevant information
- Retrieve semantically similar memories
- Use remembered information naturally during conversations

The system uses embeddings and vector similarity to retrieve relevant memories.

---

### 🎙️ Voice Assistant

MiniMe supports hands-free interaction.

The voice pipeline includes:

- Microphone capture
- Voice activity detection (VAD)
- Silence detection
- Audio recording
- Backend transcription
- Wake-word detection
- Text-to-speech responses

Example:

```text
Hey Toto
      ↓
MiniMe wakes up
      ↓
"Yes Leena"
      ↓
User gives command
      ↓
MiniMe executes the request
