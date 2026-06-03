import './dom-setup.js';
import mermaid from 'mermaid';

mermaid.initialize({ startOnLoad: false, securityLevel: 'loose' });

export interface ValidationResult {
  id: string;
  valid: boolean;
  error?: string;
}

export async function validateDiagram(id: string, code: string): Promise<ValidationResult> {
  try {
    await mermaid.parse(code);
    return { id, valid: true };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return { id, valid: false, error: message };
  }
}
