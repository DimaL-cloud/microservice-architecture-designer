export type GeneratedQuestionType = 'TEXT' | 'NUMBER' | 'SINGLE' | 'MULTI';

export interface GeneratedQuestion {
  id: string;
  label: string;
  helpText: string | null;
  type: GeneratedQuestionType;
  options: string[] | null;
}

export interface GeneratedQuestionsResponse {
  questions: GeneratedQuestion[];
}

export interface StructuredAnswer {
  id: string;
  label: string;
  value: string | string[];
}

export interface GenerateQuestionsRequest {
  name: string;
  description: string;
  llmModelId: string;
  answers: StructuredAnswer[];
}

export interface SaveAndGenerateRequest {
  name: string;
  description: string;
  llmModelId: string;
  answers: StructuredAnswer[];
}
