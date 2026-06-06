package ua.dmytrolutsiuk.backend.service;

import org.springframework.ai.converter.BeanOutputConverter;

/**
 * A {@link BeanOutputConverter} that tolerates a model wrapping its JSON in surrounding text.
 *
 * <p>Despite the prompts demanding JSON-only output, some models (especially on a complex,
 * reasoning-heavy task like the blueprint review) prepend a chain-of-thought preamble — e.g.
 * {@code "I'll review the blueprint systematically..."} — or append a trailing note before/after the
 * actual object. The stock {@code BeanOutputConverter} strips a wrapping Markdown fence but then
 * hands the whole string to Jackson, which fails on the first non-JSON token
 * ({@code Could not parse the given text to the desired target type}). Because the structured
 * blueprint/blueprint-review calls are hard gates, that failure fails the whole project.
 *
 * <p>This subclass extracts the first balanced top-level JSON object from the response before
 * delegating to the standard parsing. Everything else — including {@link #getFormat()} and
 * {@link #getJsonSchema()}, which carry the schema derived from the bean's
 * {@code @JsonProperty(required = true)} annotations — is inherited unchanged, so schema injection
 * is unaffected. When no balanced object can be found (e.g. genuinely truncated output) the original
 * text is passed through so the delegate still raises its normal error and the hard gate holds.
 */
public class LenientBeanOutputConverter<T> extends BeanOutputConverter<T> {

    public LenientBeanOutputConverter(Class<T> clazz) {
        super(clazz);
    }

    @Override
    public T convert(String text) {
        return super.convert(extractJsonObject(text));
    }

    /**
     * Returns the substring spanning the first complete, brace-balanced JSON object in {@code text}
     * (skipping any leading prose/fence and ignoring any trailing commentary). Braces inside string
     * literals are not counted. If there is no {@code '{'} or no matching close (truncated output),
     * the input is returned unchanged so the caller's parser reports the real failure.
     */
    static String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> depth++;
                case '}' -> {
                    if (--depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
                default -> {
                }
            }
        }
        return text;
    }
}
