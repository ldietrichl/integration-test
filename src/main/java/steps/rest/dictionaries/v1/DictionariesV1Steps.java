package steps.rest.dictionaries.v1;

import constants.Endpoints;
import dto.dictionaries.request.ExpressionParameterDictReqDto;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static io.qameta.allure.Allure.step;

public class DictionariesV1Steps {

    private final RestClient client;
    private final java.net.URI tunnelEndpoint;

    public DictionariesV1Steps(RestClient client) {
        this.client = client;
        this.tunnelEndpoint = null;
    }

    /** Despite the legacy class name, expression-parameter-dict is the existing V2 operation. */
    public DictionariesV1Steps(RestClient client, String ownedLoopbackUri) {
        this.client = client;
        this.tunnelEndpoint = java.net.URI.create(ownedLoopbackUri);
        if (!"http".equals(tunnelEndpoint.getScheme()) || !"127.0.0.1".equals(tunnelEndpoint.getHost())
                || tunnelEndpoint.getPort() < 1 || tunnelEndpoint.getPort() > 65535
                || tunnelEndpoint.getUserInfo() != null || tunnelEndpoint.getQuery() != null
                || tunnelEndpoint.getFragment() != null || !tunnelEndpoint.getPath().isEmpty()) {
            throw new IllegalArgumentException("Expected an owned HTTP IPv4 loopback dictionary origin");
        }
    }

    private RequestSpecification route(RequestSpecification spec) {
        if (tunnelEndpoint != null) {
            if (!(spec instanceof FilterableRequestSpecification)) {
                throw new IllegalStateException(
                        "Dictionary tunnel requires a filterable request specification to clear inherited credentials");
            }
            FilterableRequestSpecification routed = (FilterableRequestSpecification) spec;
            // Do not forward scheduler/gateway credentials to a different backend.
            routed.removeHeader("Authorization").removeHeader("Cookie").removeCookies().auth().none();
            routed.baseUri("http://127.0.0.1").port(tunnelEndpoint.getPort()).basePath("");
        }
        return spec;
    }

    public ValidatableResponseWrapper getSplittingPoints() {
        return step("Read the V2 splitting-point dictionary", () -> client.get(
                this::route, "/api/v2/dictionaries/splitting-points"));
    }

    public ValidatableResponseWrapper getExpressionParameterDict(ExpressionParameterDictReqDto body) {
        return step("Получаем справочник параметров выражений", () -> client.post(
                spec -> route(spec).body(body),
                Endpoints.DictionariesV2.V2_EXPRESSION_PARAMETER_DICT));
    }

    public ValidatableResponseWrapper getExpressionParameterDictStatusOk(ExpressionParameterDictReqDto body) {
        return step("Проверяем, что справочник параметров выражений вернул 200 OK", () ->
                getExpressionParameterDict(body).should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }
}
