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