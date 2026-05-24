import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ProjectResponse } from '../models/project';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);

  list(): Observable<ProjectResponse[]> {
    return this.http.get<ProjectResponse[]>('/api/projects');
  }
}
