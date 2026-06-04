# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**MAD (Microservice Architecture Designer)** is an AI-powered tool that turns a structured project description into a full microservice-architecture design package: C4 Context + C4 Container diagrams, a Software Design Document (SDD), Architecture Decision Records (ADRs), and sequence diagrams. The differentiator over a generic chatbot is **structured input** — a guided questionnaire plus LLM-generated follow-up questions — and a multi-stage generation pipeline that keeps every artifact mutually consistent.

It is a thesis project. CORS is wide open and there is no auth — fine for the current scope, but don't assume production hardening exists.

## Architecture: four deployable services

The repo is a polyglot monorepo wired together by `docker-compose.yml`:

```
frontend (Angular 21, :4200)  ──/api──►  backend (Spring Boot 4, :8080)
                                              │
                          ┌───────────────────┼────────────────────┐
                          ▼                    ▼                    ▼
                  postgres (:5432)    mermaid-validator      Anthropic / OpenAI
                  jsonb storage          (Node, :3000)         (Spring AI)
```

- **`backend/`** — Java 25 / Spring Boot 4.0.6 (Maven). The brain: REST API, the LLM artifact-generation pipeline, persistence.
- **`frontend/`** — Angular 21 **zoneless** standalone-component SPA. Calls the backend under `/api`.
- **`mermaid-validator/`** — small Node 22 / Hono / TypeScript service that validates Mermaid diagram source headlessly (`mermaid.parse` inside jsdom). Consumed only by the backend.
- **`postgres`** — single `mad` database; project data stored as `jsonb`.

Startup is healthcheck-gated: the backend will not start until Postgres (`pg_isready`) **and** mermaid-validator (`/health`) are healthy. The frontend only has a plain `depends_on backend` (no health gate).

`specs/` holds the design docs that drive the implementation (`MVP.md`, `generate-artifacts.md`, `project-page.md`, `questions.md`, `structured-input.md`) — read these before changing pipeline or UI behavior; they are the source of intent.

## The generation pipeline (the heart of the system)

A project flows: **brief → follow-up questions → blueprint → per-artifact generation → automatic review → Mermaid validate/repair → READY**. It is grounded on a single canonical blueprint so all artifacts stay consistent, and it runs asynchronously off the request thread.

1. **Questions** (`QuestionGenerationService`, `POST /api/projects/questions`) — generates 5–10 typed clarifying questions from the brief.
2. **Save & Generate** (`ProjectGenerationService.saveAndGenerate`, `POST /api/projects`) — validates the model id, commits the `Project` row as `GENERATING` with a placeholder summary **in its own transaction**, then fires the async orchestrator and returns `201` immediately. The frontend polls `GET /api/projects/{id}`.
3. **Orchestration** (`ArtifactGenerationOrchestrator.generate`, `@Async("artifactGenerationExecutor")`):
   - **Summary** (`ProjectSummaryService`) — best-effort raw-text call that replaces the placeholder summary (`ProjectSummaryService.PLACEHOLDER`) with the real one. Runs first so the project-list card updates quickly. Reuses an already-generated summary on restart (a real, non-placeholder value is kept); only a placeholder/null is (re)generated, and a failure there clears it to null. Never fails generation.
   - **Blueprint** (`BlueprintService`) — a structured-output (`.entity`) LLM call. Produces `ArchitectureBlueprint` (system overview, containers, relationships, decisions, key flows). Decisions are clamped to `max-adrs` (8) and flows to `max-flows` (6) so fan-out can't grow unboundedly (shared `BlueprintClamp`).
   - **Blueprint review** (`BlueprintReviewService`) — a second structured-output call (`prompts/blueprint-review.md`) that reviews the generated blueprint against the brief and microservice best practice (shared-database and other antipatterns) and returns a **corrected** `ArchitectureBlueprint`, re-clamped and referential-integrity-checked. **Hard gate:** an LLM failure or a corrected blueprint with dangling/duplicate ids throws → project `FAILED` (it is *not* best-effort like Summary). Runs before artifacts; the reviewed blueprint is the one persisted, so restart reuses it without re-reviewing.
   - **Artifacts** (`ArtifactGenerationService`) — one raw-text LLM call **per artifact** (C4 context, C4 container, SDD, one ADR per decision, one sequence diagram per flow), fanned onto the bounded `artifactCallExecutor`. Each body *is* the artifact; `PromptText.stripFences` removes a stray wrapping code fence.
   - **Review** (`ArtifactReviewService`) — automatic, no-human pass: re-runs each artifact through `prompts/review.md` with the blueprint as source of truth; the corrected output replaces the original.
   - **Mermaid repair** (`MermaidRepairService`) — sends every diagram to mermaid-validator; for invalid ones, LLM-repairs feeding back the validator error, re-validates **only** the repaired ones, looping up to `mermaid-repair-max-attempts` (3). Still invalid → throws → project `FAILED`.
   - `completeReady` → status `READY`. Any exception anywhere → `markFailed` → status `FAILED`.

Key invariants when touching this code:

- **Blueprint generation and blueprint review are the only structured-output (`.entity`) calls; both produce `ArchitectureBlueprint`.** Everything downstream is raw `.content()` text stored/validated directly.
- **No JPA transaction is ever held across an LLM/HTTP call.** The orchestrator runs outside any transaction and reloads the `Project` by id in each short `ProjectPersistenceService` step. `saveAndGenerate` deliberately commits the `GENERATING` row before firing the async task so the background thread reliably sees it.
- **Generation state lives only in memory.** On restart, `GenerationRecoveryListener` marks any project stuck in `GENERATING` as `FAILED` (clearing a leftover summary placeholder but keeping a real summary). Recover via `POST /api/projects/{id}/restart-generation`, which re-runs from the persisted brief and **reuses an already-persisted (already-reviewed) blueprint and summary** (so a bad blueprint survives restarts and is not re-reviewed/re-billed, and a good summary isn't re-billed or clobbered).
- **Status is a 3-state machine only:** `GENERATING`, `READY`, `FAILED` (no PENDING/IN_REVIEW). Enforced by both the JPA enum and a DB `CHECK` constraint.
- **Concurrency is deliberately throttled.** `artifactGenerationExecutor` (max 4) runs one orchestrator per project; `artifactCallExecutor` caps per-artifact LLM calls at `generation.concurrency`, which is **1** in `application.yml` (constrained API tier) even though the property default is 3 — so artifacts currently generate sequentially. Raise `concurrency` only if the API account allows it.

### LLM access conventions

- **`LlmChatService` is the single LLM gateway.** `call(...)` = structured output, `callForText(...)` = raw text. It switches on `LlmModel.provider`: Anthropic uses `maxTokens`, OpenAI uses `maxCompletionTokens` (reasoning models require the latter). All calls go through `withRetry` (6 attempts, 5s→60s exponential backoff) whose transient-error detection is **string/heuristic-based** (matches `429`/`529`/`rate limit`/`overload` in the cause chain) — wrapped/renamed provider exceptions could slip through.
- Two `ChatClient` beans (`anthropicChatClient`, `openAiChatClient`) are defined in `ChatClientConfig`; `spring.ai.chat.client.enabled=false` disables Spring AI's default auto-configured client so these qualified beans are used.
- The selectable model catalog is `llm.models` in `application.yml` (Claude Opus/Sonnet/Haiku, GPT-5 family). A brief carries `llmModelId`; `LlmModelProperties.findById` resolves it to a provider.
- Prompts are Markdown files in `backend/src/main/resources/prompts/`. **C4 diagrams are intentionally NOT Mermaid's native C4 macros** — the prompts force a v11 `flowchart` with `layout: elk` + `classDef` styling, a readability workaround the repair prompt is also aware of.

### Two Jackson engines coexist (don't unify blindly)

- **`tools.jackson` (Jackson 3)** via `JsonCodec` — for briefs/blueprints/artifacts, all `jsonb` (de)serialization, and `JsonNode` payload fields.
- **`com.fasterxml.jackson` (Jackson 2)** — used *only* for `@JsonProperty(required = true)` schema annotations on `ArchitectureBlueprint` / `GeneratedQuestionsResponse` / `MermaidValidationRequest` so Spring AI emits a valid output schema.

### Persistence

Single JPA entity `Project` (`@Table "projects"`). `brief`, `blueprint`, `artifacts` are `jsonb` columns mapped as `String` via `@JdbcTypeCode(SqlTypes.JSON)` and (de)serialized in the service layer, not by JPA. **`ddl-auto: validate`** — the schema is owned by Flyway (`resources/db/migration/`); Hibernate fails to start if entities and migrations drift. The detail response parses the stored artifact string into a `JsonNode` tree (`ProjectResponseAssembler`) so the frontend receives a real object; the list view uses the lightweight MapStruct `ProjectMapper`.

## Frontend conventions

- **Zoneless by omission** — there is no `zone.js` import, no `polyfills` entry, and no `provideZonelessChangeDetection` call. Angular 21 runs zoneless when zone.js is absent; change detection is driven **only by signals**.
- **Zoneless patterns in practice:** forms use one-way `[ngModel]="sig()"` + `(ngModelChange)="sig.set(...)"`, **not** `[(ngModel)]`. Hot paths (mermaid pan/zoom, icon SVG injection, SDD heading scan) use `viewChild` `ElementRef` + `effect()`/`afterNextRender` + **native `addEventListener`** with direct DOM writes, deliberately avoiding template event bindings to keep change-detection churn at zero. New DOM-mutating work outside signals must follow this pattern.
- **Routing** — 4 routes, all lazy-loaded via `loadComponent` (`app.routes.ts`). `MadTitleStrategy` sets `<title> | MAD`.
- **Data** — `project-api.ts` / `llm-model-api.ts` are root-provided `HttpClient` wrappers returning rxjs Observables; `provideHttpClient(withFetch())`. Status polling (`project-list`, `project-detail`) uses `timer(0, 4000).pipe(switchMap, takeWhile(GENERATING, /*inclusive*/ true))` so it self-terminates once everything settles. `llm-model-api` shares one request app-wide via `shareReplay`.
- **Mermaid** (`shared/ui/mermaid-diagram.ts`) — module-level singleton lazy-imports `mermaid` + `@mermaid-js/layout-elk` once and registers the ELK layout. Heavy libs (`mermaid`, `elk`, `jszip`) are lazy-imported to keep the initial bundle small; they're listed in `angular.json` `allowedCommonJsDependencies`.
- **Styling** — Tailwind v4 with **no `tailwind.config.js`**: enabled via `@import "tailwindcss"` in `styles.css` + `@tailwindcss/postcss`. All design tokens live in `src/styles/tokens/*.css` inside `@theme {}` blocks, so utilities like `bg-surface`, `text-accent-strong`, `animate-pulse-soft` are **generated from token vars** — renaming a token var changes the utility name. Rendered markdown is styled by a hand-written `.prose-mad` block (no typography plugin).
- **Export** (`artifact-export.ts`) — lazy-imports `jszip`; `slugify()` preserves Unicode letters (`\p{L}`) so Cyrillic project names survive.
- **Dev proxy** — `ng serve` proxies `/api` → `http://localhost:8080` (`proxy.conf.json`). The Docker image only static-serves `dist/frontend/browser` and does **not** proxy `/api` — containerized prod needs an external reverse proxy in front of `/api`.

## mermaid-validator conventions

- Validity = whether **`mermaid.parse(code)` resolves** (no SVG is rendered); the full `mermaid` lib + jsdom exist only to reuse Mermaid's own grammar parsers. Invalid → `err.message` returned verbatim.
- **Import order is load-bearing:** `src/validator.ts` imports `./dom-setup.js` *before* `mermaid`. `dom-setup` installs browser globals onto `globalThis` as an import-time side effect; if mermaid loaded first it would crash on undefined browser globals in Node.
- ESM-only (`"type": "module"`, NodeNext) — intra-project imports use `.js` extensions even though sources are `.ts`.
- Only two routes: `GET /health` (`{status:"ok"}`, gates backend startup) and `POST /validate` (`{diagrams:[{id?,code}]}` → `{results:[{id,valid,error?}]}`). Always send explicit ids — the backend uses `c4-context`, `c4-container`, `seq:<flowId>` and maps errors back by id.

## Commands

### Whole stack (Docker)
```bash
cp .env.example .env          # then fill ANTHROPIC_API_KEY and OPENAI_API_KEY
docker compose up --build     # builds + starts all 4 services; app at http://localhost:4200
docker compose up -d postgres mermaid-validator   # just the deps, to run backend/frontend locally
```

### Backend (`cd backend/`, Java 25, Maven wrapper)
```bash
./mvnw spring-boot:run                         # run (dev profile is default; needs Postgres :5432 + validator :3000)
./mvnw clean package -DskipTests               # build a jar
./mvnw test                                    # run tests
./mvnw test -Dtest=ApplicationTests            # single test class
./mvnw test -Dtest='ApplicationTests#contextLoads'   # single test method
```
The default `dev` profile reads `application-dev.yml` (untracked, local-only) for the local Anthropic key; alternatively set `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` env vars (they default to `dummy`).

### Frontend (`cd frontend/`, Angular 21)
```bash
npm install
npm start                                      # ng serve → http://localhost:4200 (proxies /api → :8080)
npm run build                                  # production build → dist/frontend/browser
npm test                                       # @angular/build:unit-test (vitest runner)
ng test --include "src/app/projects/**/*.spec.ts"   # single file / glob
ng test --filter "slugify"                     # tests matching a name substring
```

### mermaid-validator (`cd mermaid-validator/`, Node 22)
```bash
npm install
npm run dev                                    # tsx watch on :3000
npm run build && npm start                     # tsc → dist/, then node dist/index.js
curl -s http://localhost:3000/health           # → {"status":"ok"}
```

## Test coverage note

Test infrastructure is wired up (backend Spring Boot test starters; frontend `@angular/build:unit-test` + vitest + jsdom), but actual coverage is currently minimal — only `ApplicationTests.contextLoads` on the backend and no committed frontend specs yet.
