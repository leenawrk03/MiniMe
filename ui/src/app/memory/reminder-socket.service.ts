import { Injectable, signal } from '@angular/core';
import { WS_URL } from '../api.config';

/**
 * Listens on the chat WebSocket for server-pushed reminder events so the orb
 * can announce them even when the panel is collapsed.
 */
@Injectable({ providedIn: 'root' })
export class ReminderSocketService {
  readonly lastReminder = signal<{ id: string; text: string; dueAt: string } | null>(null);
  private socket?: WebSocket;

  connect(onReminder: (text: string) => void): void {
    if (this.socket && this.socket.readyState <= WebSocket.OPEN) return;
    try {
      this.socket = new WebSocket(WS_URL);
    } catch {
      return; // backend not up yet; reminders still visible in the tab
    }
    this.socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (payload?.type === 'reminder') {
          this.lastReminder.set(payload);
          onReminder(payload.text);
        }
      } catch {
        /* ignore malformed frames */
      }
    };
    this.socket.onclose = () => {
      // Reconnect slowly so a restarted backend re-attaches automatically.
      setTimeout(() => this.connect(onReminder), 5000);
    };
  }
}
