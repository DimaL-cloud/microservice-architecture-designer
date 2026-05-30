import { Component, DestroyRef, WritableSignal, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { switchMap, takeWhile, timer } from 'rxjs';

import { Button } from '../shared/ui/button';
import { Chip } from '../shared/ui/chip';
import { Icon } from '../shared/ui/icon';
import { Input } from '../shared/ui/input';
import { LlmModelApi } from '../llm-models/llm-model-api';
import { ProjectDetailResponse } from './project';
import { ProjectApi } from './project-api';
import {
  Answer,
  ChoiceQuestion,
  DECIDE,
  OTHER,
  Question,
  SECTIONS,
  defaultAnswerForQuestion
} from './project-create-schema';
import {
  GenerateQuestionsRequest,
  GeneratedQuestion,
  SaveAndGenerateRequest,
  StructuredAnswer
} from './project-question';

const DECIDE_FOR_ME_LABEL = '(decide for me)';
const STATUS_POLL_INTERVAL_MS = 4000;

type Phase = 'input' | 'questions' | 'status';

class AnswerMapController {
  constructor(private readonly answers: WritableSignal<Record<string, Answer>>) {}

  textValue(q: Question): string {
    const a = this.answers()[q.id];
    return a?.kind === 'text' ? a.value : '';
  }

  setTextValue(q: Question, value: string): void {
    this.update(q, { kind: 'text', value });
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
      const next = current.kind === 'single' && current.selected === option ? '' : option;
      const otherText = next === OTHER && current.kind === 'single' ? current.otherText : '';
      this.update(q, { kind: 'single', selected: next, otherText });
      return;
    }

    let selected: string[];
    if (option === DECIDE) {
      selected = current.kind === 'multi' && current.selected.includes(DECIDE) ? [] : [DECIDE];
    } else {
      const prev = current.kind === 'multi' ? current.selected.filter(s => s !== DECIDE) : [];
      selected = prev.includes(option) ? prev.filter(s => s !== option) : [...prev, option];
    }
    const otherText = selected.includes(OTHER) && current.kind === 'multi' ? current.otherText : '';
    this.update(q, { kind: 'multi', selected, otherText });
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
    this.update(q, { ...current, otherText: value });
  }

  private currentChoice(q: ChoiceQuestion): Extract<Answer, { kind: 'single' | 'multi' }> {
    const a = this.answers()[q.id];
    if (a?.kind === 'single' || a?.kind === 'multi') return a;
    return defaultAnswerForQuestion(q) as Extract<Answer, { kind: 'single' | 'multi' }>;
  }

  private update(q: Question, next: Answer): void {
    this.answers.update(prev => ({ ...prev, [q.id]: next }));
  }
}

@Component({
  selector: 'app-project-create',
  standalone: true,
  imports: [FormsModule, RouterLink, Button, Chip, Icon, Input],
  templateUrl: './project-create.html'
})
export class ProjectCreate {
  private readonly llmModelApi = inject(LlmModelApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.projectId.set(id);
      this.phase.set('status');
      this.pollStatus(id);
    }
  }

  readonly sections = SECTIONS;
  readonly DECIDE = DECIDE;
  readonly OTHER = OTHER;

  readonly projectName = signal('');
  readonly description = signal('');
  readonly selectedLlm = signal<string | null>(null);

  readonly llmModels = toSignal(this.llmModelApi.list(), { initialValue: [] });

  private readonly structuredAnswers = signal<Record<string, Answer>>({});
  private readonly generatedAnswers = signal<Record<string, Answer>>({});

  readonly structured = new AnswerMapController(this.structuredAnswers);
  readonly generated = new AnswerMapController(this.generatedAnswers);

  readonly phase = signal<Phase>('input');
  readonly generating = signal(false);
  readonly generationError = signal<string | null>(null);
  readonly generatedQuestions = signal<Question[]>([]);

  // "Save and Generate" submission state.
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);

  // Status phase (reached via /projects/:id, e.g. clicking a failed project card).
  readonly projectId = signal<number | null>(null);
  readonly statusProject = signal<ProjectDetailResponse | null>(null);
  readonly statusLoading = signal(false);
  readonly statusError = signal<string | null>(null);

  readonly canContinue = computed(
    () =>
      this.projectName().trim().length > 0 &&
      this.description().trim().length > 0 &&
      this.selectedLlm() !== null
  );

  textValue(q: Question): string {
    if (q.id === 'name') return this.projectName();
    if (q.id === 'description') return this.description();
    return this.structured.textValue(q);
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
    this.structured.setTextValue(q, value);
  }

  isSelected(q: ChoiceQuestion, option: string): boolean {
    return this.structured.isSelected(q, option);
  }

  toggle(q: ChoiceQuestion, option: string): void {
    this.structured.toggle(q, option);
  }

  showOtherInput(q: ChoiceQuestion): boolean {
    return this.structured.showOtherInput(q);
  }

  otherText(q: ChoiceQuestion): string {
    return this.structured.otherText(q);
  }

  setOtherText(q: ChoiceQuestion, value: string): void {
    this.structured.setOtherText(q, value);
  }

  onContinue(): void {
    const llmModelId = this.selectedLlm();
    if (!llmModelId || this.generating()) return;

    this.generating.set(true);
    this.generationError.set(null);

    const request: GenerateQuestionsRequest = {
      name: this.projectName().trim(),
      description: this.description().trim(),
      llmModelId,
      answers: this.buildStructuredAnswers()
    };

    this.projectApi.generateQuestions(request).subscribe({
      next: (response) => {
        const converted = response.questions.map(g => this.toQuestion(g));
        this.generatedQuestions.set(converted);
        this.resetGeneratedAnswers(converted);
        this.phase.set('questions');
        this.generating.set(false);
      },
      error: () => {
        this.generationError.set('Failed to generate questions. Please try again.');
        this.generating.set(false);
      }
    });
  }

  onBackToEdit(): void {
    this.phase.set('input');
  }

  onSaveAndGenerate(): void {
    const llmModelId = this.selectedLlm();
    if (!llmModelId || this.saving()) return;

    this.saving.set(true);
    this.saveError.set(null);

    const request: SaveAndGenerateRequest = {
      name: this.projectName().trim(),
      description: this.description().trim(),
      llmModelId,
      answers: [...this.buildStructuredAnswers(), ...this.buildGeneratedAnswers()]
    };

    this.projectApi.saveAndGenerate(request).subscribe({
      next: () => {
        // Generation continues in the background; the project list reflects its status.
        this.router.navigate(['/']);
      },
      error: () => {
        this.saveError.set('Failed to save project. Please try again.');
        this.saving.set(false);
      }
    });
  }

  onRestartGeneration(): void {
    const id = this.projectId();
    if (id == null || this.statusLoading()) return;
    this.statusError.set(null);
    this.projectApi.restartGeneration(id).subscribe({
      next: project => {
        this.statusProject.set(project);
        this.pollStatus(id);
      },
      error: () => {
        this.statusError.set('Failed to restart generation. Please try again.');
      }
    });
  }

  private pollStatus(id: number): void {
    this.statusLoading.set(true);
    timer(0, STATUS_POLL_INTERVAL_MS)
      .pipe(
        switchMap(() => this.projectApi.get(id)),
        // keep polling while generating; the inclusive flag emits the final READY/FAILED value too
        takeWhile(project => project.status === 'GENERATING', true),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: project => {
          this.statusLoading.set(false);
          this.statusProject.set(project);
        },
        error: () => {
          this.statusLoading.set(false);
          this.statusError.set('Could not load project status.');
        }
      });
  }

  private buildGeneratedAnswers(): StructuredAnswer[] {
    const result: StructuredAnswer[] = [];
    const answersById = this.generatedAnswers();
    for (const q of this.generatedQuestions()) {
      const a = answersById[q.id];
      if (!a) continue;
      if (a.kind === 'text') {
        const trimmed = a.value.trim();
        if (trimmed.length > 0) {
          result.push({ id: q.id, label: q.label, value: trimmed });
        }
        continue;
      }
      if (a.kind === 'single') {
        if (a.selected === '') continue;
        const resolved = this.resolveOption(a.selected, a.otherText);
        if (resolved === null) continue;
        result.push({ id: q.id, label: q.label, value: resolved });
        continue;
      }
      if (a.kind === 'multi') {
        if (a.selected.length === 0) continue;
        const values: string[] = [];
        for (const opt of a.selected) {
          const resolved = this.resolveOption(opt, a.otherText);
          if (resolved !== null) values.push(resolved);
        }
        if (values.length > 0) {
          result.push({ id: q.id, label: q.label, value: values });
        }
      }
    }
    return result;
  }

  private buildStructuredAnswers(): StructuredAnswer[] {
    const result: StructuredAnswer[] = [];
    const answersById = this.structuredAnswers();
    for (const section of SECTIONS) {
      for (const q of section.questions) {
        if (q.id === 'name' || q.id === 'description') continue;
        const a = answersById[q.id];
        if (!a) continue;
        if (a.kind === 'text') {
          const trimmed = a.value.trim();
          if (trimmed.length > 0) {
            result.push({ id: q.id, label: q.label, value: trimmed });
          }
          continue;
        }
        if (a.kind === 'single') {
          if (a.selected === '') continue;
          const resolved = this.resolveOption(a.selected, a.otherText);
          if (resolved === null) continue;
          result.push({ id: q.id, label: q.label, value: resolved });
          continue;
        }
        if (a.kind === 'multi') {
          if (a.selected.length === 0) continue;
          const values: string[] = [];
          for (const opt of a.selected) {
            const resolved = this.resolveOption(opt, a.otherText);
            if (resolved !== null) values.push(resolved);
          }
          if (values.length > 0) {
            result.push({ id: q.id, label: q.label, value: values });
          }
        }
      }
    }
    return result;
  }

  private resolveOption(opt: string, otherText: string): string | null {
    if (opt === DECIDE) return DECIDE_FOR_ME_LABEL;
    if (opt === OTHER) {
      const trimmed = otherText.trim();
      return trimmed.length > 0 ? trimmed : null;
    }
    return opt;
  }

  private toQuestion(g: GeneratedQuestion): Question {
    const helpText = g.helpText ?? undefined;
    if (g.type === 'SINGLE' || g.type === 'MULTI') {
      return {
        id: g.id,
        label: g.label,
        helpText,
        type: g.type === 'SINGLE' ? 'single' : 'multi',
        options: g.options ?? []
      };
    }
    return {
      id: g.id,
      label: g.label,
      helpText,
      type: g.type === 'NUMBER' ? 'number' : 'text'
    };
  }

  private resetGeneratedAnswers(questions: Question[]): void {
    const initial: Record<string, Answer> = {};
    for (const q of questions) {
      initial[q.id] = defaultAnswerForQuestion(q);
    }
    this.generatedAnswers.set(initial);
  }
}
