import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';

import { formatDistanceToNow } from 'date-fns';

import { IconComponent } from '../../components/icon/icon.component';
import { ProjectResponse, ProjectStatus } from '../../models/project';
import { ProjectService } from '../../services/project.service';
import { LlmModelService } from '../../services/llm-model.service';

type FilterKey = 'all' | 'ready' | 'gen' | 'failed';

const FILTERS: readonly FilterKey[] = ['all', 'ready', 'gen', 'failed'];

const STATUS_LABEL: Record<FilterKey, string> = {
  all: 'All',
  ready: 'Ready',
  gen: 'Generating',
  failed: 'Failed'
};

function backendToDesignStatus(s: ProjectStatus): 'ready' | 'gen' | 'failed' {
  if (s === 'READY') return 'ready';
  if (s === 'GENERATING') return 'gen';
  return 'failed';
}

interface StatusVisual {
  tagClass: string;
  label: string;
}

const STATUS_VISUAL: Record<ProjectStatus, StatusVisual> = {
  READY: { tagClass: 'ok', label: 'Ready' },
  GENERATING: { tagClass: 'accent', label: 'Generating' },
  FAILED: { tagClass: 'danger', label: 'Failed' }
};

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [FormsModule, IconComponent],
  templateUrl: './projects.component.html',
  styleUrl: './projects.component.css'
})
export class ProjectsComponent {
  private readonly projectService = inject(ProjectService);
  private readonly llmModelService = inject(LlmModelService);

  readonly q = signal('');
  readonly filter = signal<FilterKey>('all');

  readonly filters = FILTERS;

  readonly projects = toSignal(this.projectService.list(), {
    initialValue: [] as ProjectResponse[]
  });

  readonly modelNames = toSignal(this.llmModelService.namesById(), {
    initialValue: new Map<string, string>()
  });

  readonly filtered = computed(() => {
    const f = this.filter();
    const query = this.q().trim().toLowerCase();
    return this.projects().filter(p => {
      const key = backendToDesignStatus(p.status);
      if (f !== 'all' && key !== f) return false;
      if (query) {
        const haystack = (p.name + ' ' + (p.description ?? '')).toLowerCase();
        if (!haystack.includes(query)) return false;
      }
      return true;
    });
  });

  filterLabel(f: FilterKey): string {
    return STATUS_LABEL[f];
  }

  statusVisual(s: ProjectStatus): StatusVisual {
    return STATUS_VISUAL[s];
  }

  modelName(id: string): string {
    return this.modelNames().get(id) ?? id;
  }

  updatedRelative(iso: string): string {
    return formatDistanceToNow(new Date(iso), { addSuffix: true });
  }

  onNewProject(): void {
    // intentionally no-op for this iteration
  }
}
