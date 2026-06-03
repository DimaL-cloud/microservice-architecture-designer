Read @specs/MVP.md.
New Project page frontend is already implemented. We need to implement generation of questions based on the project information and requirements user provided.
When user clicks Continue button, all the information about the project should be sent to backend. 
Backend should generate questions based on the project information and requirements and send them back to frontend.
LLM should generate questions in same style as structured input.
Following variations of options for structured input:
- Input (text, number, etc.)
- Single select chip options
- Multi select chip options

Each question with chip options should have chip with free text input "Other..." to allow user to provide custom answer if predefined options do not fit.
LLM can also add option 'Decide for me' for questions where user might not be sure about the answer.

In the end, "Save and Generate" button should be present. As for now, it should do nothing.

Use context7 to investigate in separate agent idiomatic approach in Spring AI to make request to LLM for our use case.
Questions should be generated via LLM model user selected for the project.
In separate agent explore best practice Markdown prompt to generate questions.
Also add loading spinner to Continue button while questions are being generated.

For testing use Claude Haiku model.

Scope:
1. Implement backend endpoint to receive project information and requirements, generate questions based on that information using LLM, and send questions back to frontend.
2. Implement frontend page to display generated questions and options.