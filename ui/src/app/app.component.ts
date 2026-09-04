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
import { VoiceService } from './voice/voice.service';
import { MemoryTabComponent } from './memory/memory-tab.component';
import { SkillsTabComponent } from './memory/skills-tab.component';
import { ReminderSocketService } from './memory/reminder-socket.service';

type Tab = 'chat' | 'memory' | 'skills';

@Component({
  selector: 'mm-root',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    OrbComponent,
    MemoryTabComponent,
    SkillsTabComponent,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit, AfterViewChecked {
  @ViewChild('scroller')
  private scroller?: ElementRef<HTMLDivElement>;

  readonly expanded = signal(false);
  readonly tab = signal<Tab>('chat');
  readonly draft = signal('');

  readonly messages;
  readonly error;

  readonly orbState = computed(() => {
    if (this.voice.armed()) {
      return 'listening';
    }

    if (this.voice.listening()) {
      return 'listening';
    }

    return this.chat.orbState();
  });

  constructor(
    private chat: ChatService,
    private voice: VoiceService,
    private reminders: ReminderSocketService,
  ) {
    this.messages = this.chat.messages;
    this.error = this.chat.error;

    /*
     * VoiceService calls this whenever it hears:
     *
     * "Hey Toto ..."
     */
    this.voice.onCommand = async (text: string) => {
      await this.handleVoiceCommand(text);
    };
  }

  get voiceSupported(): boolean {
    return this.voice.supported;
  }

  get listening(): boolean {
    return this.voice.listening();
  }

  ngOnInit(): void {
    void this.chat.loadHistory();

    /*
     * Start wake-word listening automatically.
     *
     * MiniMe will now listen for:
     *
     * "Hey Toto"
     */
    if (this.voiceSupported) {
      setTimeout(() => {
        this.voice.start();
      }, 500);
    }

    /*
     * Phase 2 reminders.
     */
    this.reminders.connect((text) => {
      this.chat.orbState.set('speaking');

      this.voice.speak(
        `Reminder: ${text}`,
        () => this.chat.orbState.set('idle'),
      );
    });
  }

  ngAfterViewChecked(): void {
    const el = this.scroller?.nativeElement;

    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }

  toggle(): void {
    this.setExpanded(!this.expanded());
  }

  /**
   * Keeps the frameless Electron window sized to the
   * current layout.
   */
  private setExpanded(value: boolean): void {
    this.expanded.set(value);

    (
      window as unknown as {
        minime?: {
          resize(v: boolean): void;
        };
      }
    ).minime?.resize(value);
  }

  /**
   * Normal typed chat.
   */
  async send(): Promise<void> {
    const text = this.draft().trim();

    if (!text) {
      return;
    }

    this.draft.set('');

    await this.chat.send(text);
    this.speakLast();
  }

  /**
   * Orb/microphone button.
   *
   * Manual click skips the wake word and immediately
   * starts listening for a command.
   */
  micClick(): void {
    if (!this.voiceSupported) {
      return;
    }

    if (this.listening && this.voice.armed()) {
      this.voice.stop();
      return;
    }

    this.setExpanded(true);
    this.voice.arm();
  }

  /**
   * Handles a command captured after "Hey Toto".
   */
  private async handleVoiceCommand(text: string): Promise<void> {
    const command = text.trim();

    if (!command) {
      return;
    }

    console.log('MiniMe voice command:', command);

    /*
     * Open the panel so the user can see the conversation.
     */
    this.setExpanded(true);

    /*
     * Send the spoken command through the exact same
     * ChatService used by typed chat.
     */
    try {
      await this.chat.send(command);

      /*
       * Speak the assistant's latest response.
       */
      this.speakLast();
    } catch (error) {
      console.error('Voice command failed:', error);

      /*
       * Return to wake-word listening even if the backend
       * fails.
       */
      this.chat.orbState.set('idle');
    }
  }

  /**
   * Speak the latest assistant response.
   */
  private speakLast(): void {
    const last = this.messages().at(-1);

    if (last?.role !== 'assistant') {
      this.chat.orbState.set('idle');
      return;
    }

    this.chat.orbState.set('speaking');

    this.voice.speak(
      last.content,
      () => this.chat.orbState.set('idle'),
    );
  }
}