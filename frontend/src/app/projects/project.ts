export type ProjectStatus = 'READY' | 'GENERATING' | 'FAILED';

export interface ProjectResponse {
  id: number;
  name: string;
  summary: string | null;
  status: ProjectStatus;
  llmModelId: string;
  createdAt: string;
}

export interface ProjectArtifacts {
  c4Context: string;
  c4Container: string;
  sdd: string;
  adrs: { id: string; title: string; markdown: string }[];
  sequenceDiagrams: { id: string; title: string; code: string }[];
}

export interface ProjectDetailResponse {
  id: number;
  name: string;
  summary: string | null;
  status: ProjectStatus;
  llmModelId: string;
  createdAt: string;
  generationError: string | null;
  artifacts: ProjectArtifacts | null;
}
