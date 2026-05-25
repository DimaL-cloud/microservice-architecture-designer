import { Component, computed, input } from '@angular/core';

import { Icon } from '../shared/ui/icon';
import { ProjectStatus } from './project';

type Visual = {
  label: string;
  icon: 'check' | 'pulse' | 'alert';
  colors: string;
};

const VISUAL: Record<ProjectStatus, Visual> = {
  READY: {
    label: 'Ready',
    icon: 'check',
    colors: 'bg-success-subtle text-success-strong border-success-border'
  },
  GENERATING: {
    label: 'Generating',
    icon: 'pulse',
    colors: 'bg-accent-subtle text-accent-strong border-accent-border'
  },
  FAILED: {
    label: 'Failed',
    icon: 'alert',
    colors: 'bg-danger-subtle text-danger-strong border-danger-border'
  }
};

const BASE_CLASSES =
  'inline-flex items-center gap-1 px-1.5 py-px rounded text-meta ' +
  'tracking-[0.02em] border leading-[1.5] font-mono';

@Component({
  selector: 'mad-status-tag',
  standalone: true,
  imports: [Icon],
  template: `
    <span [class]="classes()">
      @if (visual().icon === 'check') {
        <app-icon name="check" [size]="9" />
      } @else if (visual().icon === 'pulse') {
        <span class="inline-block w-1.5 h-1.5 rounded-full bg-current animate-pulse-soft"></span>
      } @else {
        <app-icon name="alert" [size]="9" />
      }
      {{ visual().label }}
    </span>
  `
})
export class StatusTag {
  readonly status = input.required<ProjectStatus>();

  readonly visual = computed(() => VISUAL[this.status()]);
  readonly classes = computed(() => `${BASE_CLASSES} ${this.visual().colors}`);
}
