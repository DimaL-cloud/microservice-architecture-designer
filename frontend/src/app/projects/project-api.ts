import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ProjectResponse } from './project';
import {
  GenerateQuestionsRequest,
  GeneratedQuestionsResponse
} from './project-question';

@Injectable({ providedIn: 'root' })
export class ProjectApi {
  private readonly http = inject(HttpClient);

  list(): Observable<ProjectResponse[]> {
    return this.http.get<ProjectResponse[]>('/api/projects');
  }

  generateQuestions(
    request: GenerateQuestionsRequest
  ): Observable<GeneratedQuestionsResponse> {
    return this.http.post<GeneratedQuestionsResponse>(
      '/api/projects/questions',
      request
    );
  }
}
