import {
  AfterViewChecked,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { OrbComponent } from './orb/orb.component';
import { ChatService } from './chat/chat.service';
import { VoiceService } from './chat/voice.service';
import { MemoryTabComponent } from './memory/memory-tab.component';
import { SkillsTabComponent } from './memory/skills-tab.component';
import { ReminderSocketService } from './memory/reminder-socket.service';

type Tab = 'chat' | 'memory' | 'skills';

@Component({
  selector: 'mm-root',
  standalone: true,
  imports: [FormsModule, DatePipe, OrbComponent, MemoryTabComponent, SkillsTabComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit, AfterViewChecked {
  @ViewChild('scroller') private scroller?: ElementRef<HTMLDivElement>;

  readonly expanded = signal(false);
  readonly tab = signal<Tab>('chat');
  readonly draft = signal('');

  readonly messages ;
  readonly error;
  readonly orbState = computed(() =>
    this.voice.listening() ? 'listening' : this.chat.orbState(),
  );

  constructor(
    private chat: ChatService,
    private voice: VoiceService,
    private reminders: ReminderSocketService,
  ) {
    this.messages = this.chat.messages;
    this.error = this.chat.error;
  }

  get voiceSupported(): boolean {
    return this.voice.supported();
  }

  get listening(): boolean {
    return this.voice.listening();
  }

  ngOnInit(): void {
    void this.chat.loadHistory();
    // Phase 2: due reminders are pushed from the backend; the orb speaks them.
    this.reminders.connect((text) => {
      this.chat.orbState.set('speaking');
      this.voice.speak(`Reminder: ${text}`, () => this.chat.orbState.set('idle'));
    });
  }

  ngAfterViewChecked(): void {
    const el = this.scroller?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }

  toggle(): void {
    this.setExpanded(!this.expanded());
  }

  /** Keeps the frameless Electron window sized to the current layout. */
  private setExpanded(value: boolean): void {
    this.expanded.set(value);
    (window as unknown as { minime?: { resize(v: boolean): void } }).minime?.resize(value);
  }

  async send(): Promise<void> {
    const text = this.draft();
    if (!text.trim()) return;
    this.draft.set('');
    await this.chat.send(text);
    this.speakLast();
  }

  micClick(): void {
    if (this.listening) {
      this.voice.stop();
      return;
    }
    this.setExpanded(true);
    this.voice.listen(async (transcript) => {
      await this.chat.send(transcript);
      this.speakLast();
    });
  }

  private speakLast(): void {
    const last = this.messages().at(-1);
    if (last?.role !== 'assistant') return;
    this.voice.speak(last.content, () => this.chat.orbState.set('idle'));
  }
}
