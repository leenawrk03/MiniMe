export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
  pending?: boolean;
}

/** Drives the orb animation. */
export type OrbState = 'idle' | 'listening' | 'thinking' | 'speaking';
