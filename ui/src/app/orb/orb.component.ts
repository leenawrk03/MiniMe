import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';

import { OrbState } from '../chat/chat.models';

@Component({
  selector: 'mm-orb',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,

  template: `
    <button
      class="orb"
      [class.idle]="state === 'idle'"
      [class.listening]="state === 'listening'"
      [class.thinking]="state === 'thinking'"
      [class.speaking]="state === 'speaking'"
      [attr.aria-label]="'MiniMe is ' + state"
      (click)="toggle.emit()"
    >
      <!-- Outer glow -->
      <span class="halo"></span>

      <!-- Listening ripple -->
      <span class="ring ring-1"></span>
      <span class="ring ring-2"></span>
      <span class="ring ring-3"></span>

      <!-- Bot -->
      <span class="core">
        <img
          src="assets/bot-icon.png"
          alt="MiniMe"
          class="bot-icon"
        />
      </span>
    </button>
  `,

  styleUrl: './orb.component.css',
})
export class OrbComponent {
  @Input() state: OrbState = 'idle';

  @Output() toggle = new EventEmitter<void>();
}