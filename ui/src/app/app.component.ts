// import {
//   AfterViewChecked,
//   Component,
//   ElementRef,
//   OnInit,
//   ViewChild,
//   computed,
//   signal,
// } from '@angular/core';
// import { FormsModule } from '@angular/forms';
// import { DatePipe } from '@angular/common';

// import { OrbComponent } from './orb/orb.component';
// import { ChatService } from './chat/chat.service';
// import { VoiceService } from './voice/voice.service';
// import { MemoryTabComponent } from './memory/memory-tab.component';
// import { SkillsTabComponent } from './memory/skills-tab.component';
// import { ReminderSocketService } from './memory/reminder-socket.service';
// import { AgentService } from './agent.service';

// type Tab = 'chat' | 'memory' | 'skills';

// @Component({
//   selector: 'mm-root',
//   standalone: true,
//   imports: [
//     FormsModule,
//     DatePipe,
//     OrbComponent,
//     MemoryTabComponent,
//     SkillsTabComponent,
//   ],
//   templateUrl: './app.component.html',
//   styleUrl: './app.component.css',
// })
// export class AppComponent implements OnInit, AfterViewChecked {
//   @ViewChild('scroller')
//   private scroller?: ElementRef<HTMLDivElement>;

//   readonly expanded = signal(false);
//   readonly tab = signal<Tab>('chat');
//   readonly draft = signal('');

//   readonly messages;
//   readonly error;

//   /*
//    * Computer-control confirmation.
//    *
//    * Example:
//    *
//    * User: "Hey Toto, open Chrome"
//    * MiniMe: "I can open Google Chrome. Should I?"
//    * User: "Yes"
//    */
//   private pendingAppAction: string | null = null;
//   private waitingForActionConfirmation = false;

//   readonly orbState = computed(() => {
//     if (this.voice.armed()) {
//       return 'listening';
//     }

//     if (this.voice.listening()) {
//       return 'listening';
//     }

//     return this.chat.orbState();
//   });

//   constructor(
//     private chat: ChatService,
//     private voice: VoiceService,
//     private reminders: ReminderSocketService,
//     private agent: AgentService,
//   ) {
//     this.messages = this.chat.messages;
//     this.error = this.chat.error;

//     this.voice.onCommand = async (text: string) => {
//       await this.handleVoiceCommand(text);
//     };
//   }

//   get voiceSupported(): boolean {
//     return this.voice.supported;
//   }

//   get listening(): boolean {
//     return this.voice.listening();
//   }

//   ngOnInit(): void {
//     void this.chat.loadHistory();

//     /*
//      * Start wake-word listening automatically.
//      */
//     if (this.voiceSupported) {
//       setTimeout(() => {
//         this.voice.start();
//       }, 500);
//     }

//     /*
//      * Reminders.
//      */
//     this.reminders.connect((text) => {
//       this.chat.orbState.set('speaking');

//       this.voice.speak(
//         `Reminder: ${text}`,
//         () => this.chat.orbState.set('idle'),
//       );
//     });
//   }

//   ngAfterViewChecked(): void {
//     const el = this.scroller?.nativeElement;

//     if (el) {
//       el.scrollTop = el.scrollHeight;
//     }
//   }

//   toggle(): void {
//     this.setExpanded(!this.expanded());
//   }

//   private setExpanded(value: boolean): void {
//   this.expanded.set(value);

//   (
//     window as unknown as {
//       electronAPI?: {
//         resize(v: boolean): void;
//       };
//     }
//   ).electronAPI?.resize(value);
// }

//   /**
//    * Normal typed chat.
//    */
//   async send(): Promise<void> {
//     const text = this.draft().trim();

//     if (!text) {
//       return;
//     }

//     this.draft.set('');

//     await this.chat.send(text);
//     this.speakLast();
//   }

//   /**
//    * Manual microphone button.
//    */
//   micClick(): void {
//     if (!this.voiceSupported) {
//       return;
//     }

//     if (this.listening && this.voice.armed()) {
//       this.voice.stop();
//       return;
//     }

//     this.setExpanded(true);
//     this.voice.arm();
//   }

//   /**
//    * Handles a command captured after "Hey Toto".
//    */
//   private async handleVoiceCommand(text: string): Promise<void> {
//     const command = text.trim();

//     if (!command) {
//       return;
//     }

//     console.log('MiniMe voice command:', command);

//     this.setExpanded(true);

//     try {
//       /*
//        * ==========================================================
//        * PENDING COMPUTER ACTION
//        * ==========================================================
//        */

//       if (this.pendingAppAction) {
//   const appName = this.pendingAppAction;

//   const lower = command.toLowerCase().trim();

//   const confirmed =
//     lower === 'yes' ||
//     lower === 'yeah' ||
//     lower === 'yep' ||
//     lower === 'sure' ||
//     lower === 'yes do it' ||
//     lower === 'go ahead' ||
//     lower === 'open it';

//   const rejected =
//     lower === 'no' ||
//     lower === 'nope' ||
//     lower === 'cancel' ||
//     lower === "don't" ||
//     lower === 'do not';

//   if (confirmed) {
//     this.pendingAppAction = null;
//     this.waitingForActionConfirmation = false;

//     try {
//       await this.agent.openApp(appName);

//       this.chat.orbState.set('speaking');

//       this.voice.speak(
//         'Done.',
//         () => {},
//       );
//     } catch (error) {
//       console.error(
//         '❌ Could not open app:',
//         error,
//       );

//       this.chat.orbState.set('speaking');

//       this.voice.speak(
//         `Sorry, I couldn't open ${appName}.`,
//       );
//     }

//     return;
//   }

//   if (rejected) {
//     this.pendingAppAction = null;
//     this.waitingForActionConfirmation = false;

//     this.chat.orbState.set('speaking');

//     this.voice.speak('Okay.');

//     return;
//   }

//   /*
//    * IMPORTANT:
//    * Don't destroy the pending action if the answer
//    * wasn't understood.
//    */
//   this.waitingForActionConfirmation = true;

//   this.chat.orbState.set('speaking');

//   this.voice.speak(
//     'Please say yes or no.',
//     () => {
//       this.voice.listenForConfirmation();
//     },
//   );

//   return;
// }

//       /*
//        * ==========================================================
//        * SCREEN / VISION COMMANDS
//        * ==========================================================
//        */

//       const lower = command.toLowerCase();

//       const screenCommand =
//         lower.includes('what am i looking at') ||
//         lower.includes('what is on my screen') ||
//         lower.includes("what's on my screen") ||
//         lower.includes('read my screen') ||
//         lower.includes('look at my screen') ||
//         lower.includes('see my screen') ||
//         lower.includes('what do you see') ||
//         lower.includes('can you see my screen');

//       if (screenCommand) {
//         console.log('👀 Screen agent activated');

//         this.chat.orbState.set('thinking');

//         this.voice.status.set(
//           'Looking at your screen…',
//         );

//         const answer =
//           await this.agent.readScreen(command);

//         console.log(
//           '👀 Gemini screen response:',
//           answer,
//         );

//         if (!answer) {
//           throw new Error(
//             'Gemini returned an empty screen description',
//           );
//         }

//         this.chat.orbState.set('speaking');

//         this.voice.speak(
//           answer,
//           () => this.chat.orbState.set('idle'),
//         );

//         return;
//       }

//       /*
//        * ==========================================================
//        * OPEN APPLICATION
//        * ==========================================================
//        */

//       const appName = this.detectAppToOpen(lower);

// if (appName) {
//   console.log(
//     '🖥️ Computer action detected:',
//     'OPEN_APP',
//     appName,
//   );

//   this.pendingAppAction = appName;
//   this.waitingForActionConfirmation = true;

//   this.chat.orbState.set('speaking');

//   this.voice.speak(
//     `I can open ${appName}. Should I?`,
//     () => {
//       this.voice.listenForConfirmation();
//     },
//   );

//   return;
// }

//       /*
//        * ==========================================================
//        * NORMAL CHAT
//        * ==========================================================
//        */

//       await this.chat.send(command);

//       this.speakLast();

//     } catch (error) {
//       console.error(
//         'Voice command failed:',
//         error,
//       );

//       this.pendingAppAction = null;

//       this.chat.orbState.set('idle');

//       this.voice.speak(
//         'Sorry, I had trouble doing that.',
//         () => this.chat.orbState.set('idle'),
//       );
//     }
//   }

//   /**
//    * Converts natural voice phrases into one of the exact
//    * application names allowed by Electron.
//    *
//    * Examples:
//    *
//    * "open Chrome"
//    * "launch Google Chrome"
//    * "start Safari"
//    */
//   private detectAppToOpen(command: string): string | null {
//     const openIntent =
//       command.includes('open ') ||
//       command.includes('launch ') ||
//       command.includes('start ') ||
//       command.includes('run ');

//     if (!openIntent) {
//       return null;
//     }

//     if (
//       command.includes('chrome') ||
//       command.includes('google chrome')
//     ) {
//       return 'Google Chrome';
//     }

//     if (command.includes('safari')) {
//       return 'Safari';
//     }

//     if (
//       command.includes('visual studio code') ||
//       command.includes('vs code') ||
//       command.includes('vscode')
//     ) {
//       return 'Visual Studio Code';
//     }

//     if (command.includes('terminal')) {
//       return 'Terminal';
//     }

//     if (command.includes('finder')) {
//       return 'Finder';
//     }

//     if (command.includes('notes')) {
//       return 'Notes';
//     }

//     if (command.includes('calendar')) {
//       return 'Calendar';
//     }

//     if (command.includes('mail')) {
//       return 'Mail';
//     }

//     if (command.includes('messages')) {
//       return 'Messages';
//     }

//     if (command.includes('slack')) {
//       return 'Slack';
//     }

//     if (command.includes('whatsapp')) {
//       return 'WhatsApp';
//     }

//     return null;
//   }

//   /**
//    * Speak the latest assistant response.
//    */
//   private speakLast(): void {
//     const last = this.messages().at(-1);

//     if (last?.role !== 'assistant') {
//       this.chat.orbState.set('idle');
//       return;
//     }

//     this.chat.orbState.set('speaking');

//     this.voice.speak(
//       last.content,
//       () => this.chat.orbState.set('idle'),
//     );
//   }
// }

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
import { AgentService } from './agent.service';

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
    private agent: AgentService,
  ) {
    this.messages = this.chat.messages;
    this.error = this.chat.error;

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

    if (this.voiceSupported) {
      setTimeout(() => {
        this.voice.start();
      }, 500);
    }

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

  private setExpanded(value: boolean): void {
  this.expanded.set(value);

  (
    window as unknown as {
      electronAPI?: {
        resize(v: boolean): void;
      };
    }
  ).electronAPI?.resize(value);
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
   * Manual microphone button.
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
   *
   * There is intentionally NO confirmation step.
   * Commands are executed directly.
   */
  private async handleVoiceCommand(text: string): Promise<void> {
    const command = text.trim();

    if (!command) {
      return;
    }

    console.log('MiniMe voice command:', command);

    this.setExpanded(true);

    try {
      const lower = command.toLowerCase();

      /*
       * ==========================================================
       * SCREEN / VISION COMMANDS
       * ==========================================================
       */

      const screenCommand =
        lower.includes('what am i looking at') ||
        lower.includes('what is on my screen') ||
        lower.includes("what's on my screen") ||
        lower.includes('read my screen') ||
        lower.includes('look at my screen') ||
        lower.includes('see my screen') ||
        lower.includes('what do you see') ||
        lower.includes('can you see my screen');

      if (screenCommand) {
        console.log('👀 Screen agent activated');

        this.chat.orbState.set('thinking');

        this.voice.status.set(
          'Looking at your screen…',
        );

        const answer =
          await this.agent.readScreen(command);

        console.log(
          '👀 Gemini screen response:',
          answer,
        );

        if (!answer) {
          throw new Error(
            'Gemini returned an empty screen description',
          );
        }

        this.chat.orbState.set('speaking');

        this.voice.speak(
          answer,
          () => this.chat.orbState.set('idle'),
        );

        return;
      }

      /*
       * ==========================================================
       * OPEN APPLICATION
       * ==========================================================
       *
       * Direct execution.
       *
       * User:
       *   "Hey Toto, open Notes"
       *
       * MiniMe:
       *   open Notes immediately.
       *
       * There is NO:
       *   "Should I?"
       *   "Yes?"
       *   "No?"
       */

      const appName =
        this.detectAppToOpen(lower);

      if (appName) {
        console.log(
          '🖥️ Computer action detected:',
          'OPEN_APP',
          appName,
        );

        this.chat.orbState.set('thinking');

        this.voice.status.set(
          `Opening ${appName}…`,
        );

        await this.agent.openApp(appName);

        this.chat.orbState.set('speaking');

        this.voice.speak(
          `Done. I opened ${appName}.`,
          () => {
            this.chat.orbState.set('idle');
          },
        );

        return;
      }

      /*
       * ==========================================================
       * NORMAL CHAT
       * ==========================================================
       */

      await this.chat.send(command);

      this.speakLast();

    } catch (error) {
      console.error(
        'Voice command failed:',
        error,
      );

      this.chat.orbState.set('idle');

      this.voice.speak(
        'Sorry, I had trouble doing that.',
        () => this.chat.orbState.set('idle'),
      );
    }
  }

  /**
   * Converts natural voice phrases into one of the exact
   * application names allowed by Electron.
   *
   * Examples:
   *
   * "open Chrome"
   * "launch Google Chrome"
   * "start Safari"
   */
  private detectAppToOpen(command: string): string | null {
    const openIntent =
      command.includes('open ') ||
      command.includes('launch ') ||
      command.includes('start ') ||
      command.includes('run ');

    if (!openIntent) {
      return null;
    }

    if (
      command.includes('chrome') ||
      command.includes('google chrome')
    ) {
      return 'Google Chrome';
    }

    if (command.includes('safari')) {
      return 'Safari';
    }

    if (
      command.includes('visual studio code') ||
      command.includes('vs code') ||
      command.includes('vscode')
    ) {
      return 'Visual Studio Code';
    }

    if (command.includes('terminal')) {
      return 'Terminal';
    }

    if (command.includes('finder')) {
      return 'Finder';
    }

    if (command.includes('notes')) {
      return 'Notes';
    }

    if (command.includes('calendar')) {
      return 'Calendar';
    }

    if (command.includes('mail')) {
      return 'Mail';
    }

    if (command.includes('messages')) {
      return 'Messages';
    }

    if (command.includes('slack')) {
      return 'Slack';
    }

    if (command.includes('whatsapp')) {
      return 'WhatsApp';
    }

    return null;
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
