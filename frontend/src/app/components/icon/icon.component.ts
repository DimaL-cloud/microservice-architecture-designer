import { Component, input } from '@angular/core';

export type IconName = 'logo' | 'plus' | 'search' | 'check' | 'alert' | 'model';

@Component({
  selector: 'app-icon',
  standalone: true,
  templateUrl: './icon.component.html',
  styleUrl: './icon.component.css'
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input<number>(14);
}
