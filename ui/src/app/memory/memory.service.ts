import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from '../api.config';
import { MemoryFact, Reminder, Skill } from './memory.models';

/** Backs the "What I know about you" and "Skills & Reminders" tabs. */
@Injectable({ providedIn: 'root' })
export class MemoryService {
  readonly facts = signal<MemoryFact[]>([]);
  readonly skills = signal<Skill[]>([]);
  readonly reminders = signal<Reminder[]>([]);
  readonly loading = signal(false);

  constructor(private http: HttpClient) {}

  // ---------------------------------------------------------------- memory --
  async loadFacts(): Promise<void> {
    this.loading.set(true);
    try {
      this.facts.set(await firstValueFrom(this.http.get<MemoryFact[]>(`${API_BASE}/api/memory`)));
    } finally {
      this.loading.set(false);
    }
  }

  async addFact(content: string): Promise<void> {
    await firstValueFrom(this.http.post<MemoryFact>(`${API_BASE}/api/memory`, { content }));
    await this.loadFacts();
  }

  async editFact(id: string, content: string): Promise<void> {
    await firstValueFrom(this.http.patch<void>(`${API_BASE}/api/memory/${id}`, { content }));
    await this.loadFacts();
  }

  async pinFact(id: string, pinned: boolean): Promise<void> {
    await firstValueFrom(this.http.patch<void>(`${API_BASE}/api/memory/${id}`, { pinned }));
    await this.loadFacts();
  }

  async deleteFact(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${API_BASE}/api/memory/${id}`));
    this.facts.update((list) => list.filter((f) => f.id !== id));
  }

  async forgetEverything(): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${API_BASE}/api/memory`));
    this.facts.set([]);
  }

  // ---------------------------------------------------------------- skills --
  async loadSkills(): Promise<void> {
    this.skills.set(await firstValueFrom(this.http.get<Skill[]>(`${API_BASE}/api/skills`)));
  }

  async addSkill(name: string, instructions: string, triggerWord?: string): Promise<void> {
    await firstValueFrom(
      this.http.post<Skill>(`${API_BASE}/api/skills`, {
        name,
        instructions,
        triggerWord: triggerWord?.trim() || null,
      }),
    );
    await this.loadSkills();
  }

  async toggleSkill(id: string, enabled: boolean): Promise<void> {
    await firstValueFrom(
      this.http.patch<Skill>(`${API_BASE}/api/skills/${id}?enabled=${enabled}`, {}),
    );
    await this.loadSkills();
  }

  async deleteSkill(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${API_BASE}/api/skills/${id}`));
    this.skills.update((list) => list.filter((s) => s.id !== id));
  }

  // ------------------------------------------------------------- reminders --
  async loadReminders(): Promise<void> {
    this.reminders.set(await firstValueFrom(this.http.get<Reminder[]>(`${API_BASE}/api/reminders`)));
  }

  /** `dueAtLocal` comes from an <input type="datetime-local"> value. */
  async addReminder(text: string, dueAtLocal: string): Promise<void> {
    const dueAt = dueAtLocal ? new Date(dueAtLocal).toISOString() : null;
    await firstValueFrom(this.http.post<Reminder>(`${API_BASE}/api/reminders`, { text, dueAt }));
    await this.loadReminders();
  }

  async completeReminder(id: string, done: boolean): Promise<void> {
    await firstValueFrom(
      this.http.patch<Reminder>(`${API_BASE}/api/reminders/${id}?done=${done}`, {}),
    );
    await this.loadReminders();
  }

  async deleteReminder(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${API_BASE}/api/reminders/${id}`));
    this.reminders.update((list) => list.filter((r) => r.id !== id));
  }
}
