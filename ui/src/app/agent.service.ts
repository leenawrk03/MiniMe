import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AgentService {
  private readonly API_BASE = 'http://localhost:842';

  async readScreen(prompt: string): Promise<string> {
    const electronAPI = (window as any).electronAPI;

    if (!electronAPI?.captureScreen) {
      throw new Error('Screen capture is not available');
    }

    const capture = await electronAPI.captureScreen();

    const response = await fetch(
      `${this.API_BASE}/api/agent/observe`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          prompt,
          imageBase64: capture.imageBase64,
          mimeType: capture.mimeType,
        }),
      },
    );

    if (!response.ok) {
      const error = await response.text();

      throw new Error(
        error || `Screen analysis failed: ${response.status}`,
      );
    }

    const result = await response.json();

    return result.text || '';
  }

  /**
   * Ask Electron to open an application.
   *
   * Electron/main.js is responsible for validating the
   * application against its allowlist.
   */
  async openApp(appName: string): Promise<void> {
    const electronAPI = (window as any).electronAPI;

    if (!electronAPI?.openApp) {
      throw new Error('Computer control is not available');
    }

    console.log('🖥️ Requesting app open:', appName);

    await electronAPI.openApp(appName);

    console.log('✅ App opened:', appName);
  }
}