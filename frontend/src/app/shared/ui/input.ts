import { Directive } from '@angular/core';

const INPUT_CLASSES =
  'w-full pl-3 pr-[11px] py-2 rounded-sm border border-border bg-surface ' +
  'text-body text-text outline-none transition-[border-color,box-shadow] duration-150 ease-out ' +
  'focus:border-accent focus:shadow-focus-ring';

@Directive({
  selector: 'input[mad-input]',
  standalone: true,
  host: {
    class: INPUT_CLASSES
  }
})
export class Input {}
