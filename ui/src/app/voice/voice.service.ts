import { Injectable, NgZone, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class VoiceService {
  private readonly API_BASE = 'http://localhost:842';

  readonly supported = !!navigator.mediaDevices?.getUserMedia;

  readonly listening = signal(false);
  readonly armed = signal(false);
  readonly transcript = signal('');
  readonly status = signal('Listening for “hey toto”');

  private mediaStream?: MediaStream;
  private mediaRecorder?: MediaRecorder;
  private audioContext?: AudioContext;
  private analyser?: AnalyserNode;
  private vadFrame?: number;
  private silenceTimer?: ReturnType<typeof setTimeout>;

  private chunks: Blob[] = [];

  private wantRunning = false;

  private wakeBuffer = '';

  private readonly wakeWords = [
    'hey toto',
    'hey toto,',
    'hey total',
    'hey toto.',
    'hi toto',
  ];

  /**
   * Called by AppComponent whenever a voice command is ready.
   */
  onCommand?: (text: string) => void | Promise<void>;

  constructor(private zone: NgZone) {}

  /**
   * Start continuous microphone monitoring.
   */
  async start(): Promise<void> {
    if (!this.supported || this.wantRunning) {
      return;
    }

    this.wantRunning = true;

    try {
      await this.ensureMicrophone();

      this.zone.run(() => {
        this.listening.set(true);
        this.armed.set(false);
        this.transcript.set('');
        this.status.set('Listening for “hey toto”');
      });

      this.startVAD();

      console.log('🎤 Voice listening started');
    } catch (error) {
      console.error('❌ Could not start voice:', error);

      this.wantRunning = false;

      this.zone.run(() => {
        this.listening.set(false);
        this.armed.set(false);
        this.status.set('Microphone unavailable');
      });
    }
  }

  /**
   * Stop continuous microphone monitoring.
   */
  stop(): void {
    this.wantRunning = false;
    this.wakeBuffer = '';

    this.stopRecording();
    this.stopVAD();

    if (this.mediaStream) {
      for (const track of this.mediaStream.getTracks()) {
        track.stop();
      }

      this.mediaStream = undefined;
    }

    this.zone.run(() => {
      this.listening.set(false);
      this.armed.set(false);
      this.transcript.set('');
      this.status.set('Voice off');
    });

    console.log('🎤 Voice listening stopped');
  }

  /**
   * Manually enter command mode from the microphone button.
   */
  arm(): void {
    if (!this.mediaStream) {
      console.warn(
        '🎤 Cannot arm manually because microphone is not available.',
      );
      return;
    }

    this.wakeBuffer = '';

    this.zone.run(() => {
      this.armed.set(true);
      this.transcript.set('');
      this.status.set('Listening…');
    });

    console.log('🎤 Manual voice mode armed');
  }

  /**
   * Speak text using browser speech synthesis.
   */
  speak(text: string, onDone?: () => void): void {
    if (!text?.trim()) {
      onDone?.();
      return;
    }

    try {
      window.speechSynthesis.cancel();

      const utterance = new SpeechSynthesisUtterance(text);

      utterance.rate = 1.0;
      utterance.pitch = 1.0;
      utterance.volume = 1.0;

      utterance.onstart = () => {
        this.zone.run(() => {
          this.status.set('Speaking…');
        });
      };

      utterance.onend = () => {
        try {
          onDone?.();
        } catch (error) {
          console.error(
            '❌ Voice callback failed:',
            error,
          );
        }

        this.zone.run(() => {
          if (this.armed()) {
            this.status.set('Listening…');
          } else {
            this.status.set('Listening for “hey toto”');
          }
        });
      };

      utterance.onerror = (event) => {
        console.error(
          '❌ Speech synthesis error:',
          event,
        );

        try {
          onDone?.();
        } catch (error) {
          console.error(
            '❌ Voice callback failed:',
            error,
          );
        }

        this.zone.run(() => {
          if (this.armed()) {
            this.status.set('Listening…');
          } else {
            this.status.set('Listening for “hey toto”');
          }
        });
      };

      window.speechSynthesis.speak(utterance);
    } catch (error) {
      console.error(
        '❌ Speech synthesis failed:',
        error,
      );

      try {
        onDone?.();
      } catch (callbackError) {
        console.error(
          '❌ Voice callback failed:',
          callbackError,
        );
      }

      this.zone.run(() => {
        if (this.armed()) {
          this.status.set('Listening…');
        } else {
          this.status.set('Listening for “hey toto”');
        }
      });
    }
  }

  /**
   * Make sure microphone stream exists.
   */
  private async ensureMicrophone(): Promise<void> {
    if (this.mediaStream) {
      return;
    }

    this.mediaStream =
      await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });

    console.log('🎤 Microphone access granted');
  }

  /**
   * Start simple RMS-based voice activity detection.
   */
  private startVAD(): void {
    if (!this.mediaStream) {
      return;
    }

    this.stopVAD();

    this.audioContext = new AudioContext();

    const source =
      this.audioContext.createMediaStreamSource(
        this.mediaStream,
      );

    this.analyser =
      this.audioContext.createAnalyser();

    this.analyser.fftSize = 2048;

    source.connect(this.analyser);

    const data = new Uint8Array(
      this.analyser.fftSize,
    );

    const check = () => {
      if (
        !this.wantRunning ||
        !this.analyser
      ) {
        return;
      }

      this.analyser.getByteTimeDomainData(data);

      let sum = 0;

      for (let i = 0; i < data.length; i++) {
        const value =
          (data[i] - 128) / 128;

        sum += value * value;
      }

      const rms = Math.sqrt(
        sum / data.length,
      );

      const SPEECH_THRESHOLD = 0.035;

      if (rms > SPEECH_THRESHOLD) {
        this.handleSpeechDetected();
      }

      this.vadFrame =
        requestAnimationFrame(check);
    };

    this.vadFrame =
      requestAnimationFrame(check);
  }

  /**
   * Stop VAD.
   */
  private stopVAD(): void {
    if (this.vadFrame !== undefined) {
      cancelAnimationFrame(
        this.vadFrame,
      );

      this.vadFrame = undefined;
    }

    if (this.silenceTimer) {
      clearTimeout(
        this.silenceTimer,
      );

      this.silenceTimer = undefined;
    }

    if (this.audioContext) {
      void this.audioContext
        .close()
        .catch(() => {});

      this.audioContext = undefined;
    }

    this.analyser = undefined;
  }

  /**
   * Called whenever VAD detects speech.
   */
  private handleSpeechDetected(): void {
    if (!this.wantRunning) {
      return;
    }

    if (
      this.mediaRecorder?.state ===
      'recording'
    ) {
      this.restartSilenceTimer();
      return;
    }

    this.startRecording();
  }

  /**
   * Begin recording one utterance.
   */
  private startRecording(): void {
    if (!this.mediaStream) {
      return;
    }

    if (
      this.mediaRecorder?.state ===
      'recording'
    ) {
      return;
    }

    this.chunks = [];

    let mimeType = '';

    if (
      MediaRecorder.isTypeSupported(
        'audio/webm;codecs=opus',
      )
    ) {
      mimeType =
        'audio/webm;codecs=opus';
    } else if (
      MediaRecorder.isTypeSupported(
        'audio/webm',
      )
    ) {
      mimeType = 'audio/webm';
    } else if (
      MediaRecorder.isTypeSupported(
        'audio/mp4',
      )
    ) {
      mimeType = 'audio/mp4';
    }

    try {
      this.mediaRecorder = mimeType
        ? new MediaRecorder(
            this.mediaStream,
            { mimeType },
          )
        : new MediaRecorder(
            this.mediaStream,
          );
    } catch (error) {
      console.error(
        '❌ Could not create MediaRecorder:',
        error,
      );

      return;
    }

    this.mediaRecorder.ondataavailable =
      (event: BlobEvent) => {
        if (event.data.size > 0) {
          this.chunks.push(
            event.data,
          );
        }
      };

    this.mediaRecorder.onstop = () => {
      const type =
        this.mediaRecorder?.mimeType ||
        mimeType ||
        'audio/webm';

      const blob = new Blob(
        this.chunks,
        { type },
      );

      this.chunks = [];

      if (blob.size > 0) {
        void this.transcribe(blob);
      }
    };

    this.mediaRecorder.onerror =
      (event) => {
        console.error(
          '❌ MediaRecorder error:',
          event,
        );
      };

    this.mediaRecorder.start();

    console.log(
      '🎙️ Recording started',
    );

    this.restartSilenceTimer();
  }

  /**
   * Reset silence timer while speech continues.
   */
  private restartSilenceTimer(): void {
    if (this.silenceTimer) {
      clearTimeout(
        this.silenceTimer,
      );
    }

    this.silenceTimer =
      setTimeout(() => {
        this.stopRecording();
      }, 1200);
  }

  /**
   * Stop current utterance recording.
   */
  private stopRecording(): void {
    if (this.silenceTimer) {
      clearTimeout(
        this.silenceTimer,
      );

      this.silenceTimer = undefined;
    }

    if (
      this.mediaRecorder &&
      this.mediaRecorder.state ===
        'recording'
    ) {
      console.log(
        '🎙️ Recording stopped',
      );

      this.mediaRecorder.stop();
    }
  }

  /**
   * Send recorded audio to Gemini transcription backend.
   */
  private async transcribe(
    blob: Blob,
  ): Promise<void> {
    console.log(
      `🎤 Sending audio for transcription: ${blob.size} bytes, ${blob.type}`,
    );

    try {
      const formData =
        new FormData();

      const extension =
        this.getExtension(
          blob.type,
        );

      const file = new File(
        [blob],
        `voice.${extension}`,
        {
          type:
            blob.type ||
            'audio/webm',
        },
      );

      formData.append(
        'audio',
        file,
      );

      const response =
        await fetch(
          `${this.API_BASE}/api/voice/transcribe`,
          {
            method: 'POST',
            body: formData,
          },
        );

      if (!response.ok) {
        const errorText =
          await response.text();

        throw new Error(
          errorText ||
            `Transcription failed: ${response.status}`,
        );
      }

      const result =
        await response.json();

      const text = String(
        result.text || '',
      ).trim();

      console.log(
        '🎤 Transcription:',
        text,
      );

      if (!text) {
        return;
      }

      this.zone.run(() => {
        this.transcript.set(text);
      });

      this.handleTranscript(text);
    } catch (error) {
      console.error(
        '❌ Voice transcription failed:',
        error,
      );

      this.zone.run(() => {
        if (this.armed()) {
          this.status.set('Listening…');
        } else {
          this.status.set(
            'Listening for “hey toto”',
          );
        }
      });
    }
  }

  /**
   * Process completed transcription.
   *
   * There is NO confirmation mode here.
   *
   * Every completed command is either:
   *
   * 1. A manually armed command, or
   * 2. A wake-word command.
   */
  private handleTranscript(
    text: string,
  ): void {
    const cleanText =
      text.trim();

    if (!cleanText) {
      return;
    }

    console.log(
      '🎤 Handling transcript:',
      cleanText,
      'armed:',
      this.armed(),
    );

    /*
     * =====================================================
     * MANUAL MICROPHONE MODE
     * =====================================================
     */
    if (this.armed()) {
      this.submitCommand(
        cleanText,
      );

      return;
    }

    /*
     * =====================================================
     * NORMAL WAKE WORD MODE
     * =====================================================
     */

    this.wakeBuffer =
      `${this.wakeBuffer} ${cleanText}`
        .trim()
        .replace(/\s+/g, ' ');

    console.log(
      '👂 Wake buffer:',
      this.wakeBuffer,
    );

    const wakeMatch =
      this.matchWakeWord(
        this.wakeBuffer,
      );

    if (!wakeMatch) {
      if (
        this.wakeBuffer.length >
        100
      ) {
        this.wakeBuffer =
          this.wakeBuffer.slice(
            -100,
          );
      }

      return;
    }

    const rest =
      this.wakeBuffer
        .slice(
          wakeMatch.index +
            wakeMatch.word.length,
        )
        .trim();

    console.log(
      '👋 Wake word detected. Remaining command:',
      rest,
    );

    this.wakeBuffer = '';

    /*
     * We heard:
     *
     * "Hey Toto"
     *
     * Give acknowledgement.
     */
    this.zone.run(() => {
      this.armed.set(false);
      this.transcript.set('');
      this.status.set(
        'Speaking…',
      );
    });

    this.speak(
      'yes Leena',
      () => {
        if (!this.wantRunning) {
          return;
        }

        /*
         * If the wake phrase contained
         * the command:
         *
         * "Hey Toto, open Chrome"
         *
         * submit it immediately.
         */
        if (rest) {
          this.submitCommand(
            rest,
          );

          return;
        }

        /*
         * If only "Hey Toto" was heard,
         * arm the microphone for the
         * next command.
         */
        this.zone.run(() => {
          this.armed.set(true);
          this.status.set(
            'Listening…',
          );
        });
      },
    );
  }

  /**
   * Submit command to AppComponent.
   *
   * IMPORTANT:
   *
   * There is no confirmation state.
   *
   * Whatever command arrives here is
   * immediately passed to AppComponent.
   */
  private submitCommand(
    text: string,
  ): void {
    const command =
      text.trim();

    if (!command) {
      return;
    }

    console.log(
      '🚀 Submitting voice command:',
      command,
    );

    this.armed.set(false);
    this.transcript.set('');
    this.wakeBuffer = '';

    this.zone.run(() => {
      this.status.set(
        'Listening for “hey toto”',
      );
    });

    try {
      const result =
        this.onCommand?.(
          command,
        );

      if (
        result instanceof Promise
      ) {
        void result.catch(
          (error) => {
            console.error(
              '❌ Voice command handler failed:',
              error,
            );
          },
        );
      }
    } catch (error) {
      console.error(
        '❌ Voice command handler failed:',
        error,
      );
    }
  }

  /**
   * Find wake word in accumulated transcript.
   */
  private matchWakeWord(
    text: string,
  ): {
    index: number;
    word: string;
  } | null {
    const lower =
      text.toLowerCase();

    let best:
      | {
          index: number;
          word: string;
        }
      | null = null;

    for (
      const word of this.wakeWords
    ) {
      const index =
        lower.indexOf(word);

      if (index === -1) {
        continue;
      }

      if (
        !best ||
        index < best.index
      ) {
        best = {
          index,
          word,
        };
      }
    }

    return best;
  }

  /**
   * Convert MIME type to file extension.
   */
  private getExtension(
    mimeType: string,
  ): string {
    const type =
      mimeType.toLowerCase();

    if (type.includes('wav')) {
      return 'wav';
    }

    if (
      type.includes('mpeg') ||
      type.includes('mp3')
    ) {
      return 'mp3';
    }

    if (type.includes('mp4')) {
      return 'mp4';
    }

    if (type.includes('ogg')) {
      return 'ogg';
    }

    if (type.includes('flac')) {
      return 'flac';
    }

    if (type.includes('aac')) {
      return 'aac';
    }

    return 'webm';
  }
}
