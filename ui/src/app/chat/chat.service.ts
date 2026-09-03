import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from '../api.config';
import { ChatMessage, OrbState } from './chat.models';

@Injectable({ providedIn: 'root' })
export class ChatService {
  /** One long conversation, loaded from Postgres on startup. */
  readonly messages = signal<ChatMessage[]>([]);
  readonly orbState = signal<OrbState>('idle');
  readonly error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  async loadHistory(): Promise<void> {
    try {
      const history = await firstValueFrom(
        this.http.get<ChatMessage[]>(`${API_BASE}/api/chat/history`),
      );
      this.messages.set(history);
      this.error.set(null);
    } catch {
      this.error.set('MiniMe backend is not reachable on port 842.');
    }
  }

  async send(text: string): Promise<void> {
    const trimmed = text.trim();
    if (!trimmed) return;

    const optimistic: ChatMessage = {
      id: `local-${Date.now()}`,
      role: 'user',
      content: trimmed,
      createdAt: new Date().toISOString(),
      pending: true,
    };
    this.messages.update((list) => [...list, optimistic]);
    this.orbState.set('thinking');

    try {
      const reply = await firstValueFrom(
        this.http.post<ChatMessage>(`${API_BASE}/api/chat/message`, { text: trimmed }),
      );
      this.messages.update((list) =>
        list.map((m) => (m.id === optimistic.id ? { ...m, pending: false } : m)).concat(reply),
      );
      this.error.set(null);
      this.orbState.set('speaking');
    } catch {
      this.error.set('Could not reach MiniMe. Is the backend running?');
      this.orbState.set('idle');
    }
  }

  async clear(): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${API_BASE}/api/chat/history`));
    this.messages.set([]);
  }
}
