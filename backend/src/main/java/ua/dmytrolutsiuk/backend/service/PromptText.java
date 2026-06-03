package ua.dmytrolutsiuk.backend.service;

/**
 * Helpers for building the user-message envelope and cleaning raw LLM artifact output.
 */
public final class PromptText {

    private PromptText() {
    }

    /** Wraps {@code content} in {@code <name> ... </name>} tags, matching the prompt contracts. */
    public static String tag(String name, String content) {
        return "<" + name + ">\n" + content + "\n</" + name + ">";
    }

    /**
     * Defensive cleanup of raw artifact output: removes a stray Markdown code fence the model may
     * have wrapped the whole artifact in (e.g. ```mermaid ... ``` or ``` ... ```). Diagram/SDD/ADR
     * content is stored and validated directly, so a wrapping fence would corrupt it.
     */
    public static String stripFences(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.strip();
        }
        return trimmed;
    }
}
