import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MemoryService } from './memory.service';

/** "Skills & Reminders" — custom instructions plus a local reminder scheduler. */
@Component({
  selector: 'mm-skills-tab',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="tab-body">
      <h3>Custom skills</h3>
      <p class="hint">
        Extra instructions MiniMe follows. Leave the trigger blank to always apply it, or set a word
        so it only kicks in when you mention it.
      </p>

      <form class="stack" (submit)="addSkill(); $event.preventDefault()">
        <input
          [ngModel]="skillName()"
          (ngModelChange)="skillName.set($event)"
          name="skillName"
          placeholder="Skill name (e.g. Standup summariser)"
        />
        <input
          [ngModel]="skillTrigger()"
          (ngModelChange)="skillTrigger.set($event)"
          name="skillTrigger"
          placeholder="Trigger word (optional)"
        />
        <textarea
          [ngModel]="skillInstructions()"
          (ngModelChange)="skillInstructions.set($event)"
          name="skillInstructions"
          rows="3"
          placeholder="What should MiniMe do?"
        ></textarea>
        <button type="submit" [disabled]="!skillName().trim() || !skillInstructions().trim()">
          Add skill
        </button>
      </form>

      <ul class="list">
        @for (skill of skills(); track skill.id) {
          <li [class.off]="!skill.enabled">
            <div class="grow">
              <p>
                <strong>{{ skill.name }}</strong>
                @if (skill.triggerWord) {
                  <span class="chip">{{ skill.triggerWord }}</span>
                }
              </p>
              <small>{{ skill.instructions }}</small>
            </div>
            <button class="ghost" (click)="memory.toggleSkill(skill.id, !skill.enabled)">
              {{ skill.enabled ? 'Disable' : 'Enable' }}
            </button>
            <button class="ghost danger" (click)="memory.deleteSkill(skill.id)">Delete</button>
          </li>
        }
      </ul>

      <h3>Reminders</h3>
      <form class="row" (submit)="addReminder(); $event.preventDefault()">
        <input
          [ngModel]="reminderText()"
          (ngModelChange)="reminderText.set($event)"
          name="reminderText"
          placeholder="Remind me to…"
        />
        <input
          type="datetime-local"
          [ngModel]="reminderDue()"
          (ngModelChange)="reminderDue.set($event)"
          name="reminderDue"
        />
        <button type="submit" [disabled]="!reminderText().trim()">Add</button>
      </form>

      @if (reminders().length === 0) {
        <p class="empty">No reminders yet.</p>
      }

      <ul class="list">
        @for (reminder of reminders(); track reminder.id) {
          <li [class.off]="reminder.done">
            <input
              type="checkbox"
              [checked]="reminder.done"
              (change)="memory.completeReminder(reminder.id, !reminder.done)"
            />
            <div class="grow">
              <p>{{ reminder.text }}</p>
              <small>{{ reminder.dueAt | date: 'short' }}</small>
            </div>
            <button class="ghost danger" (click)="memory.deleteReminder(reminder.id)">Delete</button>
          </li>
        }
      </ul>
    </div>
  `,
  styleUrl: './tabs.css',
})
export class SkillsTabComponent implements OnInit {
  readonly skillName = signal('');
  readonly skillTrigger = signal('');
  readonly skillInstructions = signal('');
  readonly reminderText = signal('');
  readonly reminderDue = signal('');

  readonly skills;
  readonly reminders;

  constructor(public memory: MemoryService) {
    this.skills= this.memory.skills;
    this.reminders= this.memory.reminders;
  }

  ngOnInit(): void {
    void this.memory.loadSkills();
    void this.memory.loadReminders();
  }

  async addSkill(): Promise<void> {
    const name = this.skillName().trim();
    const instructions = this.skillInstructions().trim();
    if (!name || !instructions) return;
    await this.memory.addSkill(name, instructions, this.skillTrigger());
    this.skillName.set('');
    this.skillTrigger.set('');
    this.skillInstructions.set('');
  }

  async addReminder(): Promise<void> {
    const text = this.reminderText().trim();
    if (!text) return;
    await this.memory.addReminder(text, this.reminderDue());
    this.reminderText.set('');
    this.reminderDue.set('');
  }
}
