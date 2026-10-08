package util.splittercheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

public final class SplitterResponseSnapshot {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String sourceClassName;
    private final String rawBody;
    private final JsonNode jsonBody;
    private final Throwable extractionError;
    private final Throwable parsingError;

    public SplitterResponseSnapshot(String sourceClassName,
                                    String rawBody,
                                    JsonNode jsonBody,
                                    Throwable extractionError,
                                    Throwable parsingError) {
        this.sourceClassName = sourceClassName;
        this.rawBody = rawBody;
        this.jsonBody = jsonBody;
        this.extractionError = extractionError;
        this.parsingError = parsingError;
    }

    public String getSourceClassName() {
        return sourceClassName;
    }

    public String getRawBody() {
        return rawBody;
    }

    public JsonNode getJsonBody() {
        return jsonBody == null ? MissingNode.getInstance() : jsonBody;
    }

    public Throwable getExtractionError() {
        return extractionError;
    }

    public Throwable getParsingError() {
        return parsingError;
    }

    public boolean hasExtractedBody() {
        return rawBody != null;
    }

    public boolean hasBody() {
        return rawBody != null && !rawBody.isBlank();
    }

    public boolean hasJsonBody() {
        return jsonBody != null;
    }

    public boolean isEmptyBody() {
        return rawBody != null && rawBody.isBlank();
    }

    public JsonNode requireJsonBody(String messagePrefix) {
        if (hasJsonBody()) {
            return jsonBody;
        }
        if (extractionError != null) {
            throw new AssertionError(messagePrefix
                    + ". Не удалось извлечь body из response wrapper типа " + sourceClassName, extractionError);
        }
        if (rawBody == null) {
            throw new AssertionError(messagePrefix + ". Body отсутствует и не был извлечен из response wrapper типа " + sourceClassName);
        }
        if (rawBody.isBlank()) {
            throw new AssertionError(messagePrefix + ". Body пустой");
        }
        throw new AssertionError(messagePrefix + ". Body не является корректным JSON:\n" + rawBody, parsingError);
    }

    public String prettyBody() {
        if (hasJsonBody()) {
            try {
                return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonBody);
            } catch (Exception ignored) {
                return String.valueOf(jsonBody);
            }
        }
        if (rawBody == null) {
            return "<body was not extracted>";
        }
        return rawBody.isBlank() ? "<empty body>" : rawBody;
    }
}
