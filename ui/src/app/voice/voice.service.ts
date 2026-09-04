import { Injectable, NgZone, signal, inject } from '@angular/core';
import { matchWakeWord } from './wake-word';

@Injectable({ providedIn: 'root' })
export class VoiceService {
  private zone = inject(NgZone);

  // ============================================================
  // CONFIG
  // ============================================================

  /**
   * Spring Boot backend.
   *
   * Change this if your backend runs on another port/host.
   */
  private readonly API_BASE = 'http://localhost:842';

  private readonly TRANSCRIBE_URL =
    `${this.API_BASE}/api/voice/transcribe`;

  /**
   * How long we wait after speech stops before sending
   * the recording to Spring Boot.
   */
  private readonly SILENCE_MS = 1200;

  /**
   * Ignore very short microphone noise.
   */
  private readonly MIN_RECORDING_MS = 300;

  /**
   * RMS threshold used by the simple microphone VAD.
   *
   * If your Mac microphone is very quiet/noisy, this can be
   * adjusted later.
   */
  private readonly SPEECH_THRESHOLD = 0.015;

  /**
   * How often the VAD checks microphone volume.
   */
  private readonly VAD_INTERVAL_MS = 50;

  // ============================================================
  // MEDIA
  // ============================================================

  private mediaStream: MediaStream | null = null;
  private mediaRecorder: MediaRecorder | null = null;

  private audioContext: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private vadTimer: any = null;

  private audioChunks: Blob[] = [];

  private recordingStartedAt = 0;
  private lastSpeechAt = 0;

  private processing = false;

  // ============================================================
  // STATE
  // ============================================================

  private wantRunning = false;

  /**
   * true when we are waiting for "hey toto".
   *
   * false while waiting for the actual command.
   */
  private wakeBuffer = '';

  supported =
    typeof window !== 'undefined' &&
    !!(
      navigator.mediaDevices &&
      typeof navigator.mediaDevices.getUserMedia === 'function'
    );

  listening = signal(false);
  armed = signal(false);
  transcript = signal('');
  status = signal('Idle');

  onCommand: (text: string) => void = () => {};

  // ============================================================
  // START
  // ============================================================

  async start(): Promise<void> {
    if (!this.supported) {
      this.zone.run(() => {
        this.status.set('Microphone not supported');
      });
      return;
    }

    if (this.wantRunning) {
      return;
    }

    this.wantRunning = true;

    try {
      await this.openMicrophone();

      this.zone.run(() => {
        this.listening.set(true);
        this.status.set('Listening for “hey toto”');
      });

      this.startVAD();
    } catch (error: any) {
      console.error('❌ Failed to start microphone:', error);

      this.wantRunning = false;

      this.zone.run(() => {
        this.listening.set(false);

        if (
          error?.name === 'NotAllowedError' ||
          error?.name === 'PermissionDeniedError'
        ) {
          this.status.set('Microphone permission blocked');
        } else {
          this.status.set(
            `Microphone error: ${error?.message || error?.name || 'Unknown error'}`
          );
        }
      });
    }
  }

  // ============================================================
  // MICROPHONE
  // ============================================================

  private async openMicrophone(): Promise<void> {
  if (this.mediaStream) {
    return;
  }

  console.log('🎤 Requesting microphone...');

  const stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      channelCount: 1,
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    },
    video: false,
  });

  this.mediaStream = stream;

  console.log('🎤 Microphone opened');

  const AudioContextClass =
    (window as any).AudioContext ||
    (window as any).webkitAudioContext;

  if (!AudioContextClass) {
    stream.getTracks().forEach(track => track.stop());
    this.mediaStream = null;

    throw new Error('Web Audio API is not supported');
  }

  const audioContext: AudioContext =
    new AudioContextClass();

  this.audioContext = audioContext;

  if (audioContext.state === 'suspended') {
    await audioContext.resume();
  }

  const source =
    audioContext.createMediaStreamSource(stream);

  const analyser =
    audioContext.createAnalyser();

  analyser.fftSize = 2048;
  analyser.smoothingTimeConstant = 0.2;

  source.connect(analyser);

  this.analyser = analyser;

  console.log('🎤 Audio analyser ready');
}

  // ============================================================
  // VAD
  // ============================================================

  private startVAD(): void {
    this.stopVAD();

    this.vadTimer = setInterval(() => {
      this.checkAudioLevel();
    }, this.VAD_INTERVAL_MS);
  }

  private stopVAD(): void {
    if (this.vadTimer) {
      clearInterval(this.vadTimer);
      this.vadTimer = null;
    }
  }

  private checkAudioLevel(): void {
  if (
    !this.wantRunning ||
    this.processing ||
    !this.analyser
  ) {
    return;
  }

  const analyser = this.analyser;

  const bufferLength = analyser.fftSize;
  const data = new Uint8Array(bufferLength);

  analyser.getByteTimeDomainData(data);

  let sum = 0;

  for (let i = 0; i < data.length; i++) {
    const normalized =
      (data[i] - 128) / 128;

    sum += normalized * normalized;
  }

  const rms =
    Math.sqrt(sum / data.length);

  const speaking =
    rms > this.SPEECH_THRESHOLD;

  const now = Date.now();

  if (speaking) {
    this.lastSpeechAt = now;

    if (!this.mediaRecorder) {
      this.beginRecording();
    }

    return;
  }

  if (!this.mediaRecorder) {
    return;
  }

  const elapsedSinceSpeech =
    now - this.lastSpeechAt;

  const recordingDuration =
    now - this.recordingStartedAt;

  if (
    recordingDuration >= this.MIN_RECORDING_MS &&
    elapsedSinceSpeech >= this.SILENCE_MS
  ) {
    this.finishRecording();
  }
}

  // ============================================================
  // RECORDING
  // ============================================================

  private beginRecording(): void {
    if (
      !this.mediaStream ||
      this.mediaRecorder ||
      this.processing
    ) {
      return;
    }

    const mimeType = this.getSupportedMimeType();

    console.log(
      '🎙️ Starting recording:',
      mimeType || 'browser default'
    );

    try {
      this.mediaRecorder = mimeType
        ? new MediaRecorder(this.mediaStream, {
            mimeType,
          })
        : new MediaRecorder(this.mediaStream);
    } catch (error) {
      console.error(
        '❌ MediaRecorder creation failed:',
        error
      );

      this.mediaRecorder = null;
      return;
    }

    this.audioChunks = [];

    this.recordingStartedAt = Date.now();
    this.lastSpeechAt = Date.now();

    const recorder = this.mediaRecorder;

    recorder.ondataavailable = (event: BlobEvent) => {
      if (event.data && event.data.size > 0) {
        this.audioChunks.push(event.data);
      }
    };

    recorder.onerror = (event: any) => {
      console.error(
        '❌ MediaRecorder error:',
        event
      );
    };

    recorder.onstop = () => {
      this.handleRecordingStopped();
    };

    try {
      recorder.start();
    } catch (error) {
      console.error(
        '❌ MediaRecorder.start() failed:',
        error
      );

      this.mediaRecorder = null;
      this.audioChunks = [];
    }
  }

  private finishRecording(): void {
    if (!this.mediaRecorder) {
      return;
    }

    console.log('🎙️ Speech ended, stopping recording');

    try {
      this.mediaRecorder.stop();
    } catch (error) {
      console.error(
        '❌ MediaRecorder.stop() failed:',
        error
      );

      this.mediaRecorder = null;
      this.audioChunks = [];
    }
  }

  private async handleRecordingStopped(): Promise<void> {
    const recorder = this.mediaRecorder;

    this.mediaRecorder = null;

    if (!recorder) {
      return;
    }

    if (!this.audioChunks.length) {
      return;
    }

    const mimeType =
      recorder.mimeType ||
      this.getSupportedMimeType() ||
      'audio/webm';

    const blob = new Blob(
      this.audioChunks,
      { type: mimeType }
    );

    this.audioChunks = [];

    console.log(
      '🎙️ Recorded audio:',
      blob.size,
      'bytes',
      blob.type
    );

    if (blob.size < 1000) {
      console.log('🎙️ Recording too small, ignoring');
      return;
    }

    await this.transcribe(blob);
  }

  // ============================================================
  // MIME TYPE
  // ============================================================

  private getSupportedMimeType(): string {
    const types = [
      'audio/webm;codecs=opus',
      'audio/webm',
      'audio/mp4',
      'audio/ogg;codecs=opus',
      'audio/ogg',
    ];

    for (const type of types) {
      if (
        typeof MediaRecorder !== 'undefined' &&
        MediaRecorder.isTypeSupported(type)
      ) {
        return type;
      }
    }

    return '';
  }

  // ============================================================
  // GEMINI / SPRING BOOT
  // ============================================================

  private async transcribe(blob: Blob): Promise<void> {
    if (this.processing) {
      console.log(
        '⏳ Already transcribing, ignoring recording'
      );
      return;
    }

    this.processing = true;

    this.zone.run(() => {
      this.status.set('Processing…');
    });

    try {
      const extension =
        this.getFileExtension(blob.type);

      const file = new File(
        [blob],
        `voice.${extension}`,
        {
          type: blob.type || 'audio/webm',
        }
      );

      const formData = new FormData();

      formData.append(
        'audio',
        file
      );

      console.log(
        '☁️ Sending audio to Spring Boot:',
        this.TRANSCRIBE_URL
      );

      const response = await fetch(
        this.TRANSCRIBE_URL,
        {
          method: 'POST',
          body: formData,
        }
      );

      if (!response.ok) {
        const errorText =
          await response.text();

        throw new Error(
          `HTTP ${response.status}: ${errorText}`
        );
      }

      const result =
        await response.json();

      console.log(
        '☁️ Transcription response:',
        result
      );

      const text =
        typeof result === 'string'
          ? result
          : result?.text ||
            result?.transcript ||
            '';

      const transcript =
        String(text).trim();

      if (!transcript) {
        console.log(
          '☁️ Gemini returned empty transcript'
        );

        return;
      }

      this.zone.run(() => {
        this.handleTranscript(transcript);
      });
    } catch (error: any) {
      console.error(
        '❌ Voice transcription failed:',
        error
      );

      this.zone.run(() => {
        this.status.set(
          'Voice service unavailable'
        );
      });
    } finally {
      this.processing = false;

      if (this.wantRunning) {
        this.zone.run(() => {
          if (this.armed()) {
            this.status.set('Listening…');
          } else {
            this.status.set(
              'Listening for “hey toto”'
            );
          }
        });
      }
    }
  }

  // ============================================================
  // TRANSCRIPT HANDLING
  // ============================================================

  private handleTranscript(text: string): void {
    console.log(
      '🗣️ Gemini transcript:',
      text
    );

    this.transcript.set(text);

    // ----------------------------------------------------------
    // WAKE WORD MODE
    // ----------------------------------------------------------

    if (!this.armed()) {
      this.wakeBuffer =
        `${this.wakeBuffer} ${text}`
          .trim()
          .slice(-300);

      console.log(
        '👂 Wake buffer:',
        this.wakeBuffer
      );

      const result =
        matchWakeWord(this.wakeBuffer);

      if (!result.hit) {
        this.status.set(
          'Listening for “hey toto”'
        );

        return;
      }

      console.log(
        '🟢 Wake word detected:',
        'hey toto'
      );

      this.armed.set(true);

      this.status.set(
        'Listening…'
      );

      this.wakeBuffer = '';

      const rest =
        (result.rest || '').trim();

      this.transcript.set(rest);

      /**
       * If the same recording contained:
       *
       * "hey toto open my calendar"
       *
       * then result.rest will already contain
       * the command.
       */
      if (rest) {
        this.submitCommand(rest);
      }

      return;
    }

    // ----------------------------------------------------------
    // COMMAND MODE
    // ----------------------------------------------------------

    this.submitCommand(text);
  }

  private submitCommand(text: string): void {
    const command =
      text.trim();

    if (!command) {
      return;
    }

    console.log(
      '🚀 Submitting voice command:',
      command
    );

    this.armed.set(false);
    this.transcript.set('');
    this.wakeBuffer = '';

    this.status.set(
      'Listening for “hey toto”'
    );

    this.onCommand(command);
  }

  // ============================================================
  // STOP
  // ============================================================

  stop(): void {
    console.log(
      '🛑 Stopping VoiceService'
    );

    this.wantRunning = false;
    this.processing = false;

    this.stopVAD();

    this.armed.set(false);
    this.wakeBuffer = '';
    this.transcript.set('');

    if (this.mediaRecorder) {
      try {
        this.mediaRecorder.stop();
      } catch {}

      this.mediaRecorder = null;
    }

    if (this.mediaStream) {
      for (const track of this.mediaStream.getTracks()) {
        try {
          track.stop();
        } catch {}
      }

      this.mediaStream = null;
    }

    if (this.audioContext) {
      try {
        this.audioContext.close();
      } catch {}

      this.audioContext = null;
    }

    this.analyser = null;
    this.audioChunks = [];

    this.zone.run(() => {
      this.listening.set(false);
      this.status.set('Idle');
    });
  }

  // ============================================================
  // TOGGLE
  // ============================================================

  toggle(): void {
    if (this.wantRunning) {
      this.stop();
    } else {
      void this.start();
    }
  }

  // ============================================================
  // MANUAL MICROPHONE BUTTON
  // ============================================================

  async arm(): Promise<void> {
    if (!this.wantRunning) {
      await this.start();
    }

    this.wakeBuffer = '';
    this.armed.set(true);
    this.transcript.set('');

    this.zone.run(() => {
      this.status.set('Listening…');
    });

    console.log(
      '🎤 Manual voice mode armed'
    );
  }

  // ============================================================
  // FILE EXTENSION
  // ============================================================

  private getFileExtension(
    mimeType: string
  ): string {
    const mime =
      mimeType.toLowerCase();

    if (mime.includes('mp4')) {
      return 'm4a';
    }

    if (mime.includes('ogg')) {
      return 'ogg';
    }

    if (mime.includes('wav')) {
      return 'wav';
    }

    if (mime.includes('mpeg')) {
      return 'mp3';
    }

    if (mime.includes('aac')) {
      return 'aac';
    }

    return 'webm';
  }

  // ============================================================
  // TEXT TO SPEECH
  // ============================================================

  speak(
    text: string,
    onDone?: () => void
  ): void {
    if (!('speechSynthesis' in window)) {
      return;
    }

    window.speechSynthesis.cancel();

    const utterance =
      new SpeechSynthesisUtterance(text);

    utterance.rate = 1.02;

    utterance.onstart = () => {
      this.zone.run(() => {
        this.status.set(
          'Speaking…'
        );
      });
    };

    utterance.onend = () => {
      this.zone.run(() => {
        this.status.set(
          'Listening for “hey toto”'
        );

        onDone?.();
      });
    };

    window.speechSynthesis.speak(
      utterance
    );
  }
}