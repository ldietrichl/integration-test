package steps.rest.experiments.links;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.services.core.ExperimentLinksSettings;
import io.qameta.allure.Allure;
import io.restassured.http.Method;
import ru.sber.qa.services.rest.RestClient;

/** Actual HTTP responses only. Raw bodies allow testing the boundary before DTO deserialization. */
public final class ExperimentLinksRestSteps {
    public static final String UPDATE = "/api/v1/experiments/cj_links/update";
    public static final String TASKS = UPDATE + "/status";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient client;
    public ExperimentLinksRestSteps(RestClient client) { this.client = client; }
    public record Reply(int status, String text, JsonNode json) { }
    public Reply update(Object body, String route) throws Exception {
        return request("POST", UPDATE, JSON.writeValueAsString(body), route, "application/json");
    }
    public Reply updateWithoutBody(String route) throws Exception {
        return request("POST", UPDATE, null, route, "application/json");
    }
    public Reply tasks(String route) throws Exception {
        return request("GET", TASKS, null, route, "application/json");
    }
    public Reply request(String method, String path, String body, String route, String contentType) throws Exception {
        if (!path.startsWith("/api/")) throw new IllegalArgumentException("Business API path required");
        ExperimentLinksSettings settings = new ExperimentLinksSettings();
        return Allure.step("Выполнить HTTP " + method + " " + path + " [" + route + "]", () -> {
            if (body != null) Allure.addAttachment("Тело HTTP-запроса", "application/json", body, ".json");
            var response = client.request(Method.valueOf(method), spec -> {
                spec.baseUri(settings.route(route));
                if (route.equals("campaign")) ((io.restassured.specification.FilterableRequestSpecification) spec).removeHeader("Authorization");
                spec.contentType(contentType);
                if (body != null) spec.body(body);
                return spec;
            }, path).toResponse();
            String text = response.asString();
            Allure.addAttachment("Ответ HTTP " + response.statusCode(), "text/plain", text, ".txt");
            JsonNode json;
            try { json = text.isBlank() ? JSON.nullNode() : JSON.readTree(text); }
            catch (Exception notJson) { json = JSON.getNodeFactory().textNode(text); }
            return new Reply(response.statusCode(), text, json);
        });
    }
}
