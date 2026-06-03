Read @specs/MVP.md @specs/structured-input.md @specs/questions.md @specs/generate-artifacts.md .
Projects list, new project page and questions page are already implemented.
We need to implement project page where user can see generated artifacts:
- C4 Context diagram in Mermaid format
- C4 Container diagram in Mermaid format
- SDD (System Design Document) in Markdown format
- ADRs (Architecture Decision Records) in Markdown format
- Sequence diagrams in Mermaid format

Artifacts should be displayed in separate tabs.

SDD should have Table of Contents based on Markdown.

A project can have many ADRs, so the ADRs tab should use a master–detail layout:
a left rail lists ADR titles; selecting one renders that ADR's Markdown in the reader pane on the
right. The first ADR is selected by default.
The Sequence diagrams tab follows the same master–detail pattern (title rail + diagram pane), since it
is also a list of artifacts.

"Architecture generated successfully" message can be removed, because it is not relevant anymore - we display artifacts in project page as they are generated.
Under project name display summary, used LLM model and creation date.

## Mermaid diagram interaction

Every Mermaid diagram (C4 Context, C4 Container, and Sequence diagrams) must be interactive so large
diagrams remain readable:
- Zoom in / zoom out with the mouse wheel (scroll).
- Pan / navigate the diagram by clicking and dragging with the mouse.
- A control to reset the view back to the default zoom and position (fit-to-screen).

## Export artifacts

Users can export generated artifacts in two ways:

1. **Export All** — a primary action in the project-page header that downloads every artifact as a
   single `.zip`. This is the main way to grab the whole architecture package. The archive should be
   organized into folders.
2. **Per-artifact download** — each artifact view has a small "Download" control that downloads only
   the currently displayed artifact. In the ADRs and Sequence diagrams master–detail tabs, this
   downloads the currently selected item (not the whole list).

All Mermaid diagrams (C4 Context, C4 Container, Sequence diagrams) export as `.mmd`. SDD and ADRs export as Markdown (`.md`).