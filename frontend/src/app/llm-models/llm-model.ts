export type LlmProvider = 'ANTHROPIC' | 'OPENAI';

export interface LlmModel {
  id: string;
  name: string;
  provider: LlmProvider;
}
