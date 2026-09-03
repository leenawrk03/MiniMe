import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MemoryService } from './memory.service';

/** "What I know about you" — editable view of MiniMe's persistent memory. */
@Component({
  selector: 'mm-memory-tab',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="tab-body">
      <p class="hint">
        Facts MiniMe has learned, embedded locally in Postgres + pgvector. Edit, pin or delete
        anything — nothing leaves this machine.
      </p>

      <form class="row" (submit)="add(); $event.preventDefault()">
        <input
          [ngModel]="draft()"
          (ngModelChange)="draft.set($event)"
          name="fact"
          placeholder="Teach MiniMe something about you…"
        />
        <button type="submit" [disabled]="!draft().trim()">Remember</button>
      </form>

      @if (facts().length === 0) {
        <p class="empty">Nothing remembered yet. Keep talking — MiniMe learns as you go.</p>
      }

      <ul class="list">
        @for (fact of facts(); track fact.id) {
          <li [class.pinned]="fact.pinned">
            @if (editingId() === fact.id) {
              <input
                class="grow"
                [ngModel]="editDraft()"
                (ngModelChange)="editDraft.set($event)"
                name="edit-{{ fact.id }}"
              />
              <button (click)="saveEdit(fact.id)">Save</button>
              <button class="ghost" (click)="editingId.set(null)">Cancel</button>
            } @else {
              <div class="grow">
                <p>{{ fact.content }}</p>
                <small>{{ fact.source }} · {{ fact.createdAt | date: 'mediumDate' }}</small>
              </div>
              <button
                class="ghost"
                [title]="fact.pinned ? 'Unpin' : 'Pin so it is always recalled'"
                (click)="memory.pinFact(fact.id, !fact.pinned)"
              >
                {{ fact.pinned ? '★' : '☆' }}
              </button>
              <button class="ghost" (click)="startEdit(fact.id, fact.content)">Edit</button>
              <button class="ghost danger" (click)="memory.deleteFact(fact.id)">Forget</button>
            }
          </li>
        }
      </ul>

      @if (facts().length > 0) {
        <button class="ghost danger wipe" (click)="forgetAll()">Forget everything</button>
      }
    </div>
  `,
  styleUrl: './tabs.css',
})
export class MemoryTabComponent implements OnInit {
  readonly draft = signal('');
  readonly editingId = signal<string | null>(null);
  readonly editDraft = signal('');
  readonly facts;

  constructor(public memory: MemoryService) {
    this.facts= this.memory.facts;
  }

  ngOnInit(): void {
    void this.memory.loadFacts();
  }

  async add(): Promise<void> {
    const text = this.draft().trim();
    if (!text) return;
    this.draft.set('');
    await this.memory.addFact(text);
  }

  startEdit(id: string, content: string): void {
    this.editingId.set(id);
    this.editDraft.set(content);
  }

  async saveEdit(id: string): Promise<void> {
    const text = this.editDraft().trim();
    if (text) await this.memory.editFact(id, text);
    this.editingId.set(null);
  }

  async forgetAll(): Promise<void> {
    if (confirm('Delete every remembered fact? This cannot be undone.')) {
      await this.memory.forgetEverything();
    }
  }
}
