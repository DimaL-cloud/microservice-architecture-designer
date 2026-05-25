import { Component, computed, input } from '@angular/core';

type ButtonVariant = 'default' | 'primary';

const BASE_CLASSES =
  'inline-flex items-center justify-center gap-1.5 px-[11px] py-1.5 rounded-sm ' +
  'text-body font-medium leading-[1.4] whitespace-nowrap cursor-pointer border ' +
  'transition-colors duration-150 ease-out disabled:opacity-50 disabled:cursor-not-allowed';

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  default: 'bg-surface text-text-body border-border hover:bg-surface-muted',
  primary: 'bg-text text-surface border-text hover:bg-text-body'
};

@Component({
  selector: 'button[mad-button]',
  standalone: true,
  template: '<ng-content />',
  host: {
    '[class]': 'classes()'
  }
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('default');

  readonly classes = computed(() => `${BASE_CLASSES} ${VARIANT_CLASSES[this.variant()]}`);
}
