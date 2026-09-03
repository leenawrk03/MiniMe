// Phase 1: everything runs locally. Override with window.__MINIME_API__ if needed.
const w = window as unknown as { __MINIME_API__?: string };
export const API_BASE = w.__MINIME_API__ ?? 'http://localhost:842';
export const WS_URL = API_BASE.replace(/^http/, 'ws') + '/ws/chat';
