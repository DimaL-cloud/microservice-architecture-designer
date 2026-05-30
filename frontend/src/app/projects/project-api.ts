import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ProjectDetailResponse, ProjectResponse } from './project';
import {
  GenerateQuestionsRequest,
  GeneratedQuestionsResponse,
  SaveAndGenerateRequest
} from './project-question';

@Injectable({ providedIn: 'root' })
export class ProjectApi {
  private readonly http = inject(HttpClient);

  list(): Observable<ProjectResponse[]> {
    return this.http.get<ProjectResponse[]>('/api/projects');
  }

  get(id: number): Observable<ProjectDetailResponse> {
    return this.http.get<ProjectDetailResponse>(`/api/projects/${id}`);
  }

  generateQuestions(
    request: GenerateQuestionsRequest
  ): Observable<GeneratedQuestionsResponse> {
    return this.http.post<GeneratedQuestionsResponse>(
      '/api/projects/questions',
      request
    );
  }

  saveAndGenerate(
    request: SaveAndGenerateRequest
  ): Observable<ProjectDetailResponse> {
    return this.http.post<ProjectDetailResponse>('/api/projects', request);
  }

  restartGeneration(id: number): Observable<ProjectDetailResponse> {
    return this.http.post<ProjectDetailResponse>(
      `/api/projects/${id}/restart-generation`,
      {}
    );
  }
}
