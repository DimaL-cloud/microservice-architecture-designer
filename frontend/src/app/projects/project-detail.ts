import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { switchMap, takeWhile, timer } from 'rxjs';

import { formatDistanceToNow } from 'date-fns';
import { MarkdownComponent } from 'ngx-markdown';

import { Button } from '../shared/ui/button';
import { Chip } from '../shared/ui/chip';
import { Icon } from '../shared/ui/icon';
import { MermaidDiagram } from '../shared/ui/mermaid-diagram';
import { LlmModelApi } from '../llm-models/llm-model-api';
import { ProjectArtifacts, ProjectDetailResponse } from './project';
import { ProjectApi } from './project-api';
import { downloadText, exportAllAsZip, slugify } from './artifact-export';

const STATUS_POLL_INTERVAL_MS = 4000;

export type TabKey = 'context' | 'container' | 'sdd' | 'adrs' | 'sequence';

interface TocEntry {
  level: number;
  text: string;
  id: string;
}

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [RouterLink, Button, Chip, Icon, MermaidDiagram, MarkdownComponent],
  templateUrl: './project-detail.html'
})
export class ProjectDetail {
  private readonly projectApi = inject(ProjectApi);
  private readonly llmModelApi = inject(LlmModelApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly tabs: { key: TabKey; label: string }[] = [
    { key: 'context', label: 'C4 Context' },
    { key: 'container', label: 'C4 Container' },
    { key: 'sdd', label: 'SDD' },
    { key: 'adrs', label: 'ADRs' },
    { key: 'sequence', label: 'Sequence diagrams' }
  ];

  readonly project = signal<ProjectDetailResponse | null>(null);
  readonly loading = signal(false);
  readonly statusError = signal<string | null>(null);
  readonly exporting = signal(false);

  readonly activeTab = signal<TabKey>('context');
  readonly selectedAdr = signal(0);
  readonly selectedSeq = signal(0);
  readonly toc = signal<TocEntry[]>([]);
  readonly activeHeading = signal<string | null>(null);

  readonly modelNames = toSignal(this.llmModelApi.namesById(), {
    initialValue: new Map<string, string>()
  });

  readonly artifacts = computed<ProjectArtifacts | null>(() => this.project()?.artifacts ?? null);

  private readonly sddContent = viewChild<ElementRef<HTMLElement>>('sddContent');
  private projectId: number | null = null;
  private headingObserver: IntersectionObserver | null = null;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = Number(idParam);
    if (idParam && !Number.isNaN(id)) {
      this.projectId = id;
      this.pollStatus(id);
    }
    this.destroyRef.onDestroy(() => this.headingObserver?.disconnect());
  }

  modelName(id: string): string {
    return this.modelNames().get(id) ?? id;
  }

  createdRelative(iso: string): string {
    return formatDistanceToNow(new Date(iso), { addSuffix: true });
  }

  selectTab(tab: TabKey): void {
    this.activeTab.set(tab);
  }

  /** Classes for a master-detail rail item (ADRs / Sequence diagrams). */
  railItemClass(selected: boolean): string {
    const base =
      'block w-full text-left text-body px-3 py-2 rounded-md border cursor-pointer ' +
      'transition-colors leading-snug whitespace-normal break-words ';
    return (
      base +
      (selected
        ? 'bg-accent-subtle border-accent-border text-accent-strong'
        : 'bg-surface border-border text-text-body hover:bg-surface-muted')
    );
  }

  /** Builds the SDD Table of Contents from the rendered Markdown headings. */
  onSddReady(): void {
    const root = this.sddContent()?.nativeElement;
    if (!root) return;
    const headings = Array.from(
      root.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6')
    );
    const used = new Map<string, number>();
    const entries: TocEntry[] = [];
    for (const heading of headings) {
      const text = (heading.textContent ?? '').trim();
      if (!text) continue;
      const base = slugify(text);
      const seen = used.get(base) ?? 0;
      used.set(base, seen + 1);
      const id = seen === 0 ? base : `${base}-${seen + 1}`;
      heading.id = id;
      entries.push({ level: Number(heading.tagName.substring(1)), text, id });
    }
    this.toc.set(entries);
    this.activeHeading.set(entries[0]?.id ?? null);
    this.observeHeadings(headings.filter(h => h.id));
  }

  scrollToHeading(id: string): void {
    this.activeHeading.set(id);
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** Classes for a Table-of-Contents entry, highlighting the section in view. */
  tocItemClass(id: string): string {
    const base =
      'block w-full text-left text-meta py-1 truncate cursor-pointer bg-transparent ' +
      'border-y-0 border-r-0 border-l-2 transition-colors ';
    return (
      base +
      (this.activeHeading() === id
        ? 'border-l-accent text-accent-strong font-medium'
        : 'border-l-transparent text-text-subtle hover:text-text-body')
    );
  }

  /** Scrollspy: highlight the TOC entry whose section is near the top of the viewport. */
  private observeHeadings(headings: HTMLElement[]): void {
    this.headingObserver?.disconnect();
    if (!headings.length) return;
    const order = headings.map(h => h.id);
    const visible = new Set<string>();
    this.headingObserver = new IntersectionObserver(
      changes => {
        for (const change of changes) {
          const id = (change.target as HTMLElement).id;
          if (change.isIntersecting) visible.add(id);
          else visible.delete(id);
        }
        const active = order.find(id => visible.has(id));
        if (active) this.activeHeading.set(active);
      },
      { root: null, rootMargin: '-80px 0px -55% 0px', threshold: 0 }
    );
    for (const heading of headings) this.headingObserver.observe(heading);
  }

  async onExportAll(): Promise<void> {
    const project = this.project();
    if (!project || this.exporting()) return;
    this.exporting.set(true);
    try {
      await exportAllAsZip(project);
    } finally {
      this.exporting.set(false);
    }
  }

  downloadSdd(): void {
    const artifacts = this.artifacts();
    if (artifacts) downloadText('sdd.md', artifacts.sdd, 'text/markdown');
  }

  downloadCurrentAdr(): void {
    const adr = this.artifacts()?.adrs[this.selectedAdr()];
    if (adr) downloadText(`${slugify(adr.title)}.md`, adr.markdown, 'text/markdown');
  }

  onRestartGeneration(): void {
    const id = this.projectId;
    if (id == null || this.loading()) return;
    this.statusError.set(null);
    this.projectApi.restartGeneration(id).subscribe({
      next: project => {
        this.project.set(project);
        this.pollStatus(id);
      },
      error: () => {
        this.statusError.set('Failed to restart generation. Please try again.');
      }
    });
  }

  private pollStatus(id: number): void {
    this.loading.set(true);
    timer(0, STATUS_POLL_INTERVAL_MS)
      .pipe(
        switchMap(() => this.projectApi.get(id)),
        // keep polling while generating; inclusive flag emits the final READY/FAILED value too
        takeWhile(project => project.status === 'GENERATING', true),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: project => {
          this.loading.set(false);
          this.project.set(project);
        },
        error: () => {
          this.loading.set(false);
          this.statusError.set('Could not load project status.');
        }
      });
  }
}
