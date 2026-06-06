package ua.dmytrolutsiuk.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LenientBeanOutputConverterTest {

    @Test
    void stripsLeadingReasoningPreamble() {
        String text = "I'll review the blueprint systematically against the checklist.\n\n"
                + "**Fix needed**: add a relationship.\n\n"
                + "{\"systemName\": \"UrbanCart\"}";
        assertEquals("{\"systemName\": \"UrbanCart\"}", LenientBeanOutputConverter.extractJsonObject(text));
    }

    @Test
    void stripsTrailingCommentary() {
        String text = "{\"a\": 1}\n\nLet me know if you'd like further changes.";
        assertEquals("{\"a\": 1}", LenientBeanOutputConverter.extractJsonObject(text));
    }

    @Test
    void stripsPreambleAndMarkdownFence() {
        String text = "Here is the corrected blueprint:\n```json\n{\"a\": 1}\n```";
        assertEquals("{\"a\": 1}", LenientBeanOutputConverter.extractJsonObject(text));
    }

    @Test
    void keepsNestedObjects() {
        String json = "{\"a\": {\"b\": {\"c\": 1}}, \"d\": [{\"e\": 2}]}";
        assertEquals(json, LenientBeanOutputConverter.extractJsonObject("preamble " + json + " trailer"));
    }

    @Test
    void ignoresBracesInsideStringValues() {
        String json = "{\"note\": \"this } is not a close { brace\", \"x\": 1}";
        assertEquals(json, LenientBeanOutputConverter.extractJsonObject(json));
    }

    @Test
    void ignoresEscapedQuotesInsideStrings() {
        String json = "{\"note\": \"a quote \\\" then a brace }\", \"x\": 1}";
        assertEquals(json, LenientBeanOutputConverter.extractJsonObject("blah " + json));
    }

    @Test
    void passesThroughWhenNoObjectPresent() {
        String text = "no json here at all";
        assertSame(text, LenientBeanOutputConverter.extractJsonObject(text));
    }

    @Test
    void passesThroughTruncatedObjectSoTheParserStillFails() {
        // A genuinely truncated (unbalanced) object must not be "rescued" — the hard gate should
        // still fail rather than silently shipping a partial blueprint.
        String text = "preamble {\"a\": 1, \"b\": ";
        assertSame(text, LenientBeanOutputConverter.extractJsonObject(text));
    }

    @Test
    void handlesNull() {
        assertNull(LenientBeanOutputConverter.extractJsonObject(null));
    }
}
