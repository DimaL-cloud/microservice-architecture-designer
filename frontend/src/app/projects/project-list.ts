import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { switchMap, takeWhile, timer } from 'rxjs';

import { formatDistanceToNow } from 'date-fns';

import { Button } from '../shared/ui/button';
import { Chip } from '../shared/ui/chip';
import { Icon } from '../shared/ui/icon';
import { Input } from '../shared/ui/input';
import { LlmModelApi } from '../llm-models/llm-model-api';
import { ProjectResponse } from './project';
import { ProjectApi } from './project-api';
import { StatusTag } from './status-tag';

type FilterKey = 'all' | 'ready' | 'gen' | 'failed';

const FILTERS: readonly FilterKey[] = ['all', 'ready', 'gen', 'failed'];

const STATUS_LABEL: Record<FilterKey, string> = {
  all: 'All',
  ready: 'Ready',
  gen: 'Generating',
  failed: 'Failed'
};

// Refresh the list while any project is still generating, so cards flip to Ready/Failed live.
const LIST_POLL_INTERVAL_MS = 4000;

function backendToDesignStatus(s: ProjectResponse['status']): 'ready' | 'gen' | 'failed' {
  if (s === 'READY') return 'ready';
  if (s === 'GENERATING') return 'gen';
  return 'failed';
}

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [
    FormsModule,
    Button,
    Chip,
    Icon,
    Input,
    StatusTag
  ],
  templateUrl: './project-list.html'
})
export class ProjectList {
  private readonly projectApi = inject(ProjectApi);
  private readonly llmModelApi = inject(LlmModelApi);
  private readonly router = inject(Router);

  readonly q = signal('');
  readonly filter = signal<FilterKey>('all');

  readonly filters = FILTERS;

  readonly projects = toSignal(
    timer(0, LIST_POLL_INTERVAL_MS).pipe(
      switchMap(() => this.projectApi.list()),
      // poll while something is generating; inclusive emits the final settled list, then stops
      takeWhile(list => list.some(p => p.status === 'GENERATING'), true)
    ),
    { initialValue: [] as ProjectResponse[] }
  );

  readonly modelNames = toSignal(this.llmModelApi.namesById(), {
    initialValue: new Map<string, string>()
  });

  readonly filtered = computed(() => {
    const f = this.filter();
    const query = this.q().trim().toLowerCase();
    return this.projects().filter(p => {
      const key = backendToDesignStatus(p.status);
      if (f !== 'all' && key !== f) return false;
      if (query) {
        const haystack = (p.name + ' ' + (p.summary ?? '')).toLowerCase();
        if (!haystack.includes(query)) return false;
      }
      return true;
    });
  });

  filterLabel(f: FilterKey): string {
    return STATUS_LABEL[f];
  }

  modelName(id: string): string {
    return this.modelNames().get(id) ?? id;
  }

  updatedRelative(iso: string): string {
    return formatDistanceToNow(new Date(iso), { addSuffix: true });
  }

  onNewProject(): void {
    this.router.navigate(['/projects/new']);
  }

  openProject(id: number): void {
    this.router.navigate(['/projects', id]);
  }
}
