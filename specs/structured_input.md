Implement page for creating project. 
Before implementation explore which information would be useful to generate good production-ready microservice architecture in separate agent. 
Only project name, description and LLM are required fields.
You can use following variations of options for structured input:
- Input (text, number, etc.)
- Single select chip options
- Multi select chip options

Each question with chip options should have chip with free text input "Other..." to allow user to provide custom answer if predefined options do not fit.

You can also add option 'Decide for me' for questions where user might not be sure about the answer.
This option will allow the LLM to decide on behalf of the user based on the provided project information and requirements.

In the end ability to pick LLM model should be present and Continue button. Continue button should do nothing as for now. Implement only frontend 