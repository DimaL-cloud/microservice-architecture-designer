Read @specs/MVP.md @specs/structured-input.md @specs/questions.md.
New project page and questions page are already implemented. We need to implement artifact generation based on the project information, 
requirements and answered questions user provided.
'Save and Generate' button as for now does nothing. We need to implement backend endpoint to receive all the information, save project and
start artifacts generation.
Do not implement display of generated artifacts on frontend, only generation and sending to frontend.
Following artifacts should be generated:
- C4 Context diagram in Mermaid format
- C4 Container diagram in Mermaid format
- SDD (System Design Document) in Markdown format
- ADRs (Architecture Decision Records) in Markdown format
- Sequence diagrams in Mermaid format

About orchestration of artifacts generation. We can't generate all artifacts in one LLM call. Each LLM has max output tokens per request (64000, 128000 tokens depending on model).
Explore best pratice how we should orchestrate artifacts generation in separate agent. Generated artifacts should be consistent between each
other. Also we should generate multiple ADRs and multiple sequence diagrams which can't be done in one LLM call. We should do one LLM call
per artifact. In review step we can't just ask to review and fix - we might not fit in max tokens output, so discover how to orcestrate review step too.

On any error during generation, set FAILED status for the project.
Implement endpoint for restarting generation in case of failure. When generation was failed we should display in questions page
error message and button to restart generation.

After generation run review step. In separate agent explore best practice Markdown prompt to review generated artifacts and fix issues.
This step should be done automatically without user involvement. If review step finds issues, it should fix them and run validation again until all artifacts are correct.

After both generation and review steps we should validate mermaid diagrams via mermaid-validator microservice and start repair loop.
Use @backend/src/main/java/ua/dmytrolutsiuk/backend/client/mermaidvalidator/MermaidValidatorClient.java to make requests to mermaid-validator.
Max amount of attempts to fix Mermaid diagrams should be configurable via application properties, set it to 3 for now.
If after max attempts diagrams are still invalid, set FAILED status for the project.

Each Markdown prompt should be explored and implemented in separate agent.

In Project list we display summary under project name. This summary should be generated when based on information user provided.
Summary should be generated when user clicks "Save and Generate" button, so once it is generated and project is saved to db, user might go
to project list and see summary in project card. Explore in separate agent best practice Markdown prompt to generate brief summary.