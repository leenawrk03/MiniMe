export interface MemoryFact {
  id: string;
  content: string;
  kind: string;
  source: string;
  confidence: number;
  pinned: boolean;
  createdAt: string;
  lastUsedAt: string | null;
  similarity: number | null;
}

export interface Skill {
  id: string;
  name: string;
  triggerWord: string | null;
  instructions: string;
  enabled: boolean;
  createdAt: string;
}

export interface Reminder {
  id: string;
  text: string;
  dueAt: string;
  done: boolean;
  createdAt: string;
}
