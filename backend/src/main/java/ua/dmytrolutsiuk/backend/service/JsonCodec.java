package ua.dmytrolutsiuk.backend.service;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thin wrapper over the shared {@link JsonMapper} for the (de)serialization used by artifact
 * generation: serializing briefs/blueprints/artifacts into prompts and jsonb columns, and reading
 * them back. Keeps all JSON handling on the one (Jackson 3) engine the rest of the app uses.
 */
@Component
public class JsonCodec {

    private final JsonMapper jsonMapper;

    public JsonCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Pretty JSON for embedding in prompts (more readable for the model). */
    public String writePretty(Object value) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize " + typeName(value), e);
        }
    }

    /** Compact JSON for persisting in jsonb columns. */
    public String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize " + typeName(value), e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize " + type.getSimpleName(), e);
        }
    }

    public JsonNode tree(String json) {
        try {
            return jsonMapper.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to parse JSON", e);
        }
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
