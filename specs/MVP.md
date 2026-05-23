Name: Microservice Architecture Designer
Summary: Web application for developers, CTOs and system architects to simplify the design of microservice architecture. 
One of the advantages is structured input. In regular chatbots like Claude or ChatGPT, it's easy to forget to specify a parameter, 
such as non-functional requirements that affects the microservice architecture.
Application flow:
1. User creates project with filled information about what he wants to design.
2. Send all this information to AI to come up with questions and options, then these questions appear on website, where user can answer.
3. Send information and answered questions to design microservice architecture. LLM generates:
- C4 Context diagram in Mermaid format
- C4 Container diagram in Mermaid format
- SDD (System Design Document) in Markdown format
- ADRs (Architecture Decision Records) in Markdown format
- Sequence diagrams in Mermaid format
4. Review output architecture using separate request to LLM and fix issues. No actions required from user, backend performs this step automatically.
5. Display in UI to user SDD (System Design Document), C4 architecture diagram, ADRs, sequence diagrams.

User can also choose existing project and view generated artifacts.

User should be able to choose LLM model. As for now we need to support OpenAI and Claude models. LLM picker is per-project setting.

The structured input is kept on the frontend. Backend saves it as JSONB in db, because it is only used to build the LLM prompt.

Prompts should be stored in backend/src/main/resources in Markdown format.

Mermaid diagrams should be validated via mermaid-validator microservice. It returns error which will be helpful to fix the diagram if it is not correct.