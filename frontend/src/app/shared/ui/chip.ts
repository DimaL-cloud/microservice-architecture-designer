import { Component, computed, input } from '@angular/core';

const BASE_CLASSES =
  'inline-flex items-center gap-[5px] px-2 py-[3px] rounded-full text-meta ' +
  'border cursor-pointer transition-colors duration-150 ease-out';

const SELECTED_CLASSES = 'bg-accent-subtle border-accent-border text-accent-strong';
const UNSELECTED_CLASSES =
  'bg-surface-muted border-border text-text-body hover:bg-surface-emphasis';

@Component({
  selector: 'button[mad-chip]',
  standalone: true,
  template: '<ng-content />',
  host: {
    '[class]': 'classes()'
  }
})
export class Chip {
  readonly selected = input(false);

  readonly classes = computed(
    () => `${BASE_CLASSES} ${this.selected() ? SELECTED_CLASSES : UNSELECTED_CLASSES}`
  );
}
