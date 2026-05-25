export type ProjectStatus = 'READY' | 'GENERATING' | 'FAILED';

export interface ProjectResponse {
  id: number;
  name: string;
  summary: string | null;
  status: ProjectStatus;
  llmModelId: string;
  createdAt: string;
}
