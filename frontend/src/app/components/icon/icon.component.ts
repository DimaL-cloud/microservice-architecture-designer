import { HttpClient } from '@angular/common/http';
import { Component, ElementRef, effect, inject, input } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type IconName = 'logo' | 'plus' | 'search' | 'check' | 'alert' | 'model';

@Component({
  selector: 'app-icon',
  standalone: true,
  template: '',
  styleUrl: './icon.component.css'
})
export class IconComponent {
  private static readonly cache = new Map<IconName, Promise<string>>();

  private readonly http = inject(HttpClient);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly name = input.required<IconName>();
  readonly size = input<number>(14);

  constructor() {
    effect(async () => {
      const name = this.name();
      const size = this.size();
      const svg = await this.load(name);
      this.host.nativeElement.innerHTML = this.applySize(svg, size);
    });
  }

  private load(name: IconName): Promise<string> {
    let cached = IconComponent.cache.get(name);
    if (!cached) {
      cached = firstValueFrom(
        this.http.get(`icons/${name}.svg`, { responseType: 'text' })
      );
      IconComponent.cache.set(name, cached);
    }
    return cached;
  }

  private applySize(svg: string, size: number): string {
    return svg.replace(/<svg([^>]*)>/, (_, attrs: string) => {
      const cleaned = attrs.replace(/\s(width|height)="[^"]*"/g, '');
      return `<svg${cleaned} width="${size}" height="${size}">`;
    });
  }
}
