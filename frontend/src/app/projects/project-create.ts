import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';

import { Button } from '../shared/ui/button';
import { Chip } from '../shared/ui/chip';
import { Input } from '../shared/ui/input';
import { LlmModelApi } from '../llm-models/llm-model-api';
import {
  Answer,
  ChoiceQuestion,
  DECIDE,
  OTHER,
  Question,
  SECTIONS,
  defaultAnswerForQuestion
} from './project-create-schema';

@Component({
  selector: 'app-project-create',
  standalone: true,
  imports: [FormsModule, RouterLink, Button, Chip, Input],
  templateUrl: './project-create.html'
})
export class ProjectCreate {
  private readonly llmModelApi = inject(LlmModelApi);

  readonly sections = SECTIONS;
  readonly DECIDE = DECIDE;
  readonly OTHER = OTHER;

  readonly projectName = signal('');
  readonly description = signal('');
  readonly selectedLlm = signal<string | null>(null);

  readonly llmModels = toSignal(this.llmModelApi.list(), { initialValue: [] });

  private readonly answers = signal<Record<string, Answer>>({});

  readonly canContinue = computed(
    () =>
      this.projectName().trim().length > 0 &&
      this.description().trim().length > 0 &&
      this.selectedLlm() !== null
  );

  textValue(q: Question): string {
    if (q.id === 'name') return this.projectName();
    if (q.id === 'description') return this.description();
    const a = this.answers()[q.id];
    return a?.kind === 'text' ? a.value : '';
  }

  setTextValue(q: Question, value: string): void {
    if (q.id === 'name') {
      this.projectName.set(value);
      return;
    }
    if (q.id === 'description') {
      this.description.set(value);
      return;
    }
    this.updateAnswer(q, { kind: 'text', value });
  }

  isSelected(q: ChoiceQuestion, option: string): boolean {
    const a = this.answers()[q.id];
    if (!a) return false;
    if (a.kind === 'single') return a.selected === option;
    if (a.kind === 'multi') return a.selected.includes(option);
    return false;
  }

  toggle(q: ChoiceQuestion, option: string): void {
    const current = this.currentChoice(q);
    if (q.type === 'single') {
      const next = current.selected === option ? '' : option;
      const otherText = next === OTHER ? current.otherText : '';
      this.updateAnswer(q, { kind: 'single', selected: next, otherText });
      return;
    }

    let selected: string[];
    if (option === DECIDE) {
      selected = current.kind === 'multi' && current.selected.includes(DECIDE) ? [] : [DECIDE];
    } else {
      const prev = current.kind === 'multi' ? current.selected.filter(s => s !== DECIDE) : [];
      selected = prev.includes(option) ? prev.filter(s => s !== option) : [...prev, option];
    }
    const otherText = selected.includes(OTHER)
      ? current.kind === 'multi'
        ? current.otherText
        : ''
      : '';
    this.updateAnswer(q, { kind: 'multi', selected, otherText });
  }

  showOtherInput(q: ChoiceQuestion): boolean {
    return this.isSelected(q, OTHER);
  }

  otherText(q: ChoiceQuestion): string {
    const a = this.answers()[q.id];
    if (a?.kind === 'single' || a?.kind === 'multi') return a.otherText;
    return '';
  }

  setOtherText(q: ChoiceQuestion, value: string): void {
    const current = this.currentChoice(q);
    if (current.kind === 'single') {
      this.updateAnswer(q, { ...current, otherText: value });
    } else {
      this.updateAnswer(q, { ...current, otherText: value });
    }
  }

  onContinue(): void {
    // intentionally no-op for this iteration
  }

  private currentChoice(
    q: ChoiceQuestion
  ): Extract<Answer, { kind: 'single' | 'multi' }> {
    const a = this.answers()[q.id];
    if (a?.kind === 'single' || a?.kind === 'multi') return a;
    return defaultAnswerForQuestion(q) as Extract<Answer, { kind: 'single' | 'multi' }>;
  }

  private updateAnswer(q: Question, next: Answer): void {
    this.answers.update(prev => ({ ...prev, [q.id]: next }));
  }
}
