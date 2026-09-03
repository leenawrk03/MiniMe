import { Injectable, signal } from '@angular/core';

type SpeechRecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start(): void;
  stop(): void;
  onresult: ((event: any) => void) | null;
  onend: (() => void) | null;
  onerror: (() => void) | null;
};

/**
 * Phase 1 voice: browser SpeechRecognition in, SpeechSynthesis out.
 * Later phases swap this for a local STT/TTS pipeline.
 */
@Injectable({ providedIn: 'root' })
export class VoiceService {
  readonly supported = signal(false);
  readonly listening = signal(false);

  private recognition: SpeechRecognitionLike | null = null;

  constructor() {
    const ctor =
      (window as any).SpeechRecognition ?? (window as any).webkitSpeechRecognition ?? null;
    if (ctor) {
      this.recognition = new ctor() as SpeechRecognitionLike;
      this.recognition.lang = 'en-US';
      this.recognition.continuous = false;
      this.recognition.interimResults = false;
      this.supported.set(true);
    }
  }

  listen(onTranscript: (text: string) => void): void {
    const rec = this.recognition;
    if (!rec || this.listening()) return;

    rec.onresult = (event: any) => {
      const text = event.results?.[0]?.[0]?.transcript ?? '';
      if (text) onTranscript(text);
    };
    rec.onend = () => this.listening.set(false);
    rec.onerror = () => this.listening.set(false);

    this.listening.set(true);
    rec.start();
  }

  stop(): void {
    this.recognition?.stop();
    this.listening.set(false);
  }

  speak(text: string, onDone?: () => void): void {
    if (!('speechSynthesis' in window)) {
      onDone?.();
      return;
    }
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 1.02;
    utterance.pitch = 1.05;
    utterance.onend = () => onDone?.();
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  }
}
