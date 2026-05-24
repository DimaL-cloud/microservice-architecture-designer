import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';

import { LlmModel } from '../models/llm-model';

@Injectable({ providedIn: 'root' })
export class LlmModelService {
  private readonly http = inject(HttpClient);

  private readonly models$: Observable<LlmModel[]> = this.http
    .get<LlmModel[]>('/api/llm-models')
    .pipe(shareReplay({ bufferSize: 1, refCount: false }));

  list(): Observable<LlmModel[]> {
    return this.models$;
  }

  namesById(): Observable<Map<string, string>> {
    return this.models$.pipe(
      map(models => new Map(models.map(m => [m.id, m.name])))
    );
  }
}
