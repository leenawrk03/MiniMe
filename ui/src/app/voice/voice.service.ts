import { Injectable, NgZone, signal, inject } from '@angular/core';
import { matchWakeWord } from './wake-word';

@Injectable({ providedIn: 'root' })
export class VoiceService {
  private zone = inject(NgZone);

  private recog: any = null;
  private silenceTimer: any = null;
  private restartTimer: any = null;
  private wantRunning = false;

  // Keeps speech that Safari may split across recognition events.
  private wakeBuffer = '';

  supported =
    typeof window !== 'undefined' &&
    !!(
      (window as any).SpeechRecognition ||
      (window as any).webkitSpeechRecognition
    );

  listening = signal(false);
  armed = signal(false);
  transcript = signal('');
  status = signal('Idle');

  onCommand: (text: string) => void = () => {};

  start(): void {
    if (!this.supported || this.recog) return;

    const Ctor =
      (window as any).SpeechRecognition ||
      (window as any).webkitSpeechRecognition;

    const r = new Ctor();

    r.continuous = true;
    r.interimResults = true;
    r.lang = 'en-US';

    r.onstart = () => {
      this.zone.run(() => {
        this.listening.set(true);

        if (this.armed()) {
          this.status.set('Listening…');
        } else {
          this.status.set('Listening for “hey toto”');
        }
      });
    };

    r.onerror = (e: any) => {
      this.zone.run(() => {
        console.log('Speech recognition error:', e);

        if (e.error === 'not-allowed') {
          this.wantRunning = false;
          this.status.set('Microphone permission blocked');
        }
      });
    };

    r.onend = () => {
      this.zone.run(() => {
        this.listening.set(false);

        if (!this.wantRunning) {
          return;
        }

        clearTimeout(this.restartTimer);

        this.restartTimer = setTimeout(() => {
          try {
            if (this.wantRunning && !this.recog) {
              this.createRecognition();
            } else if (this.wantRunning) {
              r.start();
            }
          } catch {
            // Safari can occasionally report "already started".
          }
        }, 300);
      });
    };

    r.onresult = (event: any) => {
      this.zone.run(() => {
        let text = '';

        for (
          let i = event.resultIndex;
          i < event.results.length;
          i++
        ) {
          text += event.results[i][0].transcript + ' ';
        }

        text = text.trim();

        if (!text) return;

        console.log('Speech heard:', text);

        if (!this.armed()) {
          // Safari may send:
          // "Hey"
          // then "Toto"
          //
          // Keep both pieces in a buffer.
          this.wakeBuffer = `${this.wakeBuffer} ${text}`
            .trim()
            .slice(-200);

          const result = matchWakeWord(this.wakeBuffer);

          if (!result.hit) {
            this.status.set('Listening for “hey toto”');
            return;
          }

          console.log('Wake word detected:', 'hey toto');

          this.armed.set(true);
          this.status.set('Listening…');

          this.wakeBuffer = '';
          this.transcript.set(result.rest);

          if (result.rest) {
            this.scheduleSubmit();
          }

          return;
        }

        // We are already listening for the actual command.
        this.transcript.set(text);
        this.scheduleSubmit();
      });
    };

    this.recog = r;
    this.wantRunning = true;

    try {
      r.start();
    } catch {
      // Recognition may already be starting.
    }
  }

  private createRecognition(): void {
    this.recog = null;
    this.start();
  }

  stop(): void {
    this.wantRunning = false;

    clearTimeout(this.silenceTimer);
    clearTimeout(this.restartTimer);

    this.armed.set(false);
    this.wakeBuffer = '';
    this.transcript.set('');

    try {
      this.recog?.stop();
    } catch {}

    this.recog = null;
    this.listening.set(false);
    this.status.set('Idle');
  }

  toggle(): void {
    if (this.recog) {
      this.stop();
    } else {
      this.start();
    }
  }

  /**
   * Manual microphone button.
   * Skips the wake word and immediately listens for a command.
   */
  arm(): void {
    if (!this.recog) {
      this.start();
    }

    this.wakeBuffer = '';
    this.armed.set(true);
    this.transcript.set('');
    this.status.set('Listening…');
  }

  private scheduleSubmit(): void {
    clearTimeout(this.silenceTimer);

    this.silenceTimer = setTimeout(() => {
      this.zone.run(() => {
        const text = this.transcript().trim();

        this.armed.set(false);
        this.transcript.set('');
        this.wakeBuffer = '';
        this.status.set('Listening for “hey toto”');

        if (text) {
          console.log('Submitting voice command:', text);
          this.onCommand(text);
        }
      });
    }, 1400);
  }

  speak(text: string, onDone?: () => void): void {
    if (!('speechSynthesis' in window)) return;

    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);

    utterance.rate = 1.02;

    utterance.onstart = () => {
      this.zone.run(() => {
        this.status.set('Speaking…');
      });
    };

    utterance.onend = () => {
      this.zone.run(() => {
        this.status.set('Listening for “hey toto”');
        onDone?.();
      });
    };

    window.speechSynthesis.speak(utterance);
  }
}