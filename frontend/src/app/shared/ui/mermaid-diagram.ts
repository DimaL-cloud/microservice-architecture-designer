import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  signal,
  viewChild
} from '@angular/core';

import { Icon } from './icon';
import { downloadText, slugify } from '../../projects/artifact-export';

const MIN_SCALE = 0.2;
const MAX_SCALE = 6;
const FIT_PADDING = 0.92;

let renderCounter = 0;
let mermaidReady: Promise<typeof import('mermaid').default> | null = null;

/** Loads + initializes mermaid exactly once, then reuses the instance. */
function ensureMermaid(): Promise<typeof import('mermaid').default> {
  if (!mermaidReady) {
    mermaidReady = import('mermaid').then(module => {
      module.default.initialize({
        startOnLoad: false,
        securityLevel: 'loose',
        theme: 'neutral',
        suppressErrorRendering: true
      });
      return module.default;
    });
  }
  return mermaidReady;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/**
 * Renders a Mermaid diagram into an interactive viewport: wheel zoom (toward the
 * cursor), click-drag pan, and a fit-to-screen reset. Built for a zoneless app —
 * rendering happens imperatively in an effect and the pan/zoom hot path uses
 * native listeners that mutate `transform` directly (no change-detection churn).
 */
@Component({
  selector: 'mad-mermaid-diagram',
  standalone: true,
  imports: [Icon],
  template: `
    <div class="relative">
      <div
        class="absolute top-2 right-2 z-10 flex items-center gap-0.5 rounded-md border border-border bg-surface/90 backdrop-blur px-1 py-0.5 shadow-sm"
      >
        <span class="font-mono text-meta text-text-subtle px-1 tabular-nums select-none">
          {{ zoomPercent() }}%
        </span>
        <button type="button" [class]="controlClass" title="Zoom out" (click)="onZoomOut()">
          <app-icon name="zoom-out" [size]="14" />
        </button>
        <button type="button" [class]="controlClass" title="Zoom in" (click)="onZoomIn()">
          <app-icon name="zoom-in" [size]="14" />
        </button>
        <button type="button" [class]="controlClass" title="Fit to screen" (click)="onReset()">
          <app-icon name="fit" [size]="14" />
        </button>
        <button type="button" [class]="controlClass" title="Download .mmd" (click)="onDownload()">
          <app-icon name="download" [size]="14" />
        </button>
      </div>

      <div
        #viewport
        class="relative overflow-hidden touch-none h-[clamp(360px,60vh,720px)] rounded-md border border-border bg-surface-inset cursor-grab"
      >
        <div #inner class="absolute top-0 left-0 will-change-transform"></div>
      </div>

      @if (error(); as err) {
        <div class="mt-2 rounded-md border border-danger-border bg-danger-subtle p-3">
          <div class="text-meta text-danger-strong font-medium">Could not render this diagram.</div>
          <pre
            class="mt-2 max-h-48 overflow-auto text-meta font-mono text-text-muted whitespace-pre-wrap"
            >{{ code() }}</pre
          >
        </div>
      }
    </div>
  `
})
export class MermaidDiagram {
  readonly code = input.required<string>();
  readonly title = input<string>('');

  readonly error = signal<string | null>(null);
  readonly zoomPercent = signal(100);

  readonly controlClass =
    'inline-flex items-center justify-center h-6 w-6 rounded text-text-subtle ' +
    'hover:text-text-body hover:bg-surface-muted cursor-pointer transition-colors';

  private readonly viewport = viewChild<ElementRef<HTMLDivElement>>('viewport');
  private readonly inner = viewChild<ElementRef<HTMLDivElement>>('inner');
  private readonly destroyRef = inject(DestroyRef);

  private x = 0;
  private y = 0;
  private scale = 1;
  private renderSeq = 0;
  private readonly abort = new AbortController();

  constructor() {
    // Read code() synchronously (before any await) so the effect re-runs on change.
    effect(() => {
      const code = this.code();
      void this.render(code);
    });

    afterNextRender(() => this.attachListeners());
    this.destroyRef.onDestroy(() => this.abort.abort());
  }

  onZoomIn(): void {
    this.zoomToCenter(1.2);
  }

  onZoomOut(): void {
    this.zoomToCenter(1 / 1.2);
  }

  onReset(): void {
    this.fit();
  }

  onDownload(): void {
    downloadText(`${slugify(this.title() || 'diagram')}.mmd`, this.code(), 'text/plain');
  }

  private async render(code: string): Promise<void> {
    const seq = ++this.renderSeq;
    const definition = code?.trim();
    const inner = this.inner()?.nativeElement;

    if (!definition) {
      this.error.set(null);
      if (inner) inner.innerHTML = '';
      return;
    }

    try {
      const mermaid = await ensureMermaid();
      const { svg } = await mermaid.render(`mad-mermaid-${renderCounter++}`, definition);
      if (seq !== this.renderSeq) return;
      const target = this.inner()?.nativeElement;
      if (!target) return;
      target.innerHTML = svg;
      this.error.set(null);
      this.fit();
    } catch (e) {
      if (seq !== this.renderSeq) return;
      const target = this.inner()?.nativeElement;
      if (target) target.innerHTML = '';
      this.error.set(e instanceof Error ? e.message : 'Failed to render diagram.');
    }
  }

  /** Scales the diagram to fit the viewport and centers it. */
  private fit(): void {
    const viewport = this.viewport()?.nativeElement;
    const inner = this.inner()?.nativeElement;
    const svg = inner?.querySelector('svg');
    if (!viewport || !inner || !svg) return;

    const box = svg.viewBox?.baseVal;
    const width = box && box.width ? box.width : svg.getBoundingClientRect().width || 1;
    const height = box && box.height ? box.height : svg.getBoundingClientRect().height || 1;

    // Pin the SVG to its intrinsic size so transforms drive all sizing.
    svg.setAttribute('width', String(width));
    svg.setAttribute('height', String(height));
    svg.style.maxWidth = 'none';
    svg.style.display = 'block';

    const fitScale = Math.min(viewport.clientWidth / width, viewport.clientHeight / height) * FIT_PADDING;
    this.scale = clamp(fitScale, MIN_SCALE, MAX_SCALE);
    this.x = (viewport.clientWidth - width * this.scale) / 2;
    this.y = (viewport.clientHeight - height * this.scale) / 2;
    this.applyTransform();
    this.zoomPercent.set(Math.round(this.scale * 100));
  }

  private applyTransform(): void {
    const inner = this.inner()?.nativeElement;
    if (!inner) return;
    inner.style.transformOrigin = '0 0';
    inner.style.transform = `translate(${this.x}px, ${this.y}px) scale(${this.scale})`;
  }

  private zoomAt(cx: number, cy: number, factor: number): void {
    const next = clamp(this.scale * factor, MIN_SCALE, MAX_SCALE);
    const ratio = next / this.scale;
    this.x = cx - (cx - this.x) * ratio;
    this.y = cy - (cy - this.y) * ratio;
    this.scale = next;
    this.applyTransform();
    this.zoomPercent.set(Math.round(this.scale * 100));
  }

  private zoomToCenter(factor: number): void {
    const viewport = this.viewport()?.nativeElement;
    if (!viewport) return;
    this.zoomAt(viewport.clientWidth / 2, viewport.clientHeight / 2, factor);
  }

  private attachListeners(): void {
    const viewport = this.viewport()?.nativeElement;
    if (!viewport) return;
    const options = { signal: this.abort.signal };

    viewport.addEventListener(
      'wheel',
      (e: WheelEvent) => {
        e.preventDefault();
        const rect = viewport.getBoundingClientRect();
        const factor = Math.exp(-e.deltaY * 0.0015);
        this.zoomAt(e.clientX - rect.left, e.clientY - rect.top, factor);
      },
      { ...options, passive: false }
    );

    let dragging = false;
    let lastX = 0;
    let lastY = 0;
    let pointerId = -1;

    viewport.addEventListener(
      'pointerdown',
      (e: PointerEvent) => {
        dragging = true;
        pointerId = e.pointerId;
        lastX = e.clientX;
        lastY = e.clientY;
        viewport.setPointerCapture(pointerId);
        viewport.style.cursor = 'grabbing';
      },
      options
    );

    viewport.addEventListener(
      'pointermove',
      (e: PointerEvent) => {
        if (!dragging) return;
        this.x += e.clientX - lastX;
        this.y += e.clientY - lastY;
        lastX = e.clientX;
        lastY = e.clientY;
        this.applyTransform();
      },
      options
    );

    const endDrag = () => {
      if (!dragging) return;
      dragging = false;
      if (pointerId !== -1 && viewport.hasPointerCapture(pointerId)) {
        viewport.releasePointerCapture(pointerId);
      }
      viewport.style.cursor = 'grab';
    };

    viewport.addEventListener('pointerup', endDrag, options);
    viewport.addEventListener('pointercancel', endDrag, options);
    viewport.addEventListener('pointerleave', endDrag, options);
  }
}
