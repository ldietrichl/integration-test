package util.dataoperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import request.dataoperator.v2.DataOperatorLinksInvalidRequests.InvalidRequest;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Per-invocation evidence, independent of the console logger and assertion outcome. */
public final class LinksHttpEvidence implements BeforeEachCallback {
    private String uniqueId;

    @Override
    public void beforeEach(ExtensionContext context) {
        uniqueId = context.getUniqueId();
    }

    public void record(InvalidRequest request, ValidatableResponseWrapper wrapper) {
        record(request.scenario(), request.variant(), request.body(), request.withoutBody(), wrapper);
    }

    public void record(String scenario, String variant, String body, boolean withoutBody, ValidatableResponseWrapper wrapper) {
        var response = wrapper.toResponse();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", 1);
        evidence.put("uniqueId", uniqueId);
        evidence.put("scenario", scenario);
        evidence.put("variant", variant);
        evidence.put("withoutBody", withoutBody);
        evidence.put("requestBody", body);
        evidence.put("httpStatus", response.statusCode());
        evidence.put("contentType", response.getContentType());
        evidence.put("responseBodyText", response.asString());
        try {
            String json = new ObjectMapper().writeValueAsString(evidence);
            Allure.addAttachment("Links HTTP exchange", "application/json", json, ".json");
            String output = System.getProperty("links.evidence.directory");
            if (output != null) {
                Path directory = Path.of(output);
                Files.createDirectories(directory);
                String file = UUID.nameUUIDFromBytes(uniqueId.getBytes(StandardCharsets.UTF_8)) + ".json";
                Files.writeString(directory.resolve(file), json, StandardCharsets.UTF_8);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot write structured HTTP evidence", error);
        }
    }
}
