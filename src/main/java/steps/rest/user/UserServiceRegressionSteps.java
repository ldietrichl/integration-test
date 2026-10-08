package steps.rest.user;

import constants.Endpoints;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;
import java.util.Map;

import static io.qameta.allure.Allure.step;

public class UserServiceRegressionSteps {

    private final RestClient client;
    private final java.net.URI auditTunnel;

    public UserServiceRegressionSteps(RestClient client) {
        this.client = client;
        this.auditTunnel = null;
    }

    /** Scoped routing only for audit lookup; other user-service scenarios retain their existing client. */
    public UserServiceRegressionSteps(RestClient client, String ownedLoopbackUri) {
        this.client = client;
        this.auditTunnel = java.net.URI.create(ownedLoopbackUri);
        if (!"http".equals(auditTunnel.getScheme()) || !"127.0.0.1".equals(auditTunnel.getHost())
                || auditTunnel.getPort() < 1 || auditTunnel.getPort() > 65535
                || auditTunnel.getUserInfo() != null || auditTunnel.getQuery() != null
                || auditTunnel.getFragment() != null || !auditTunnel.getPath().isEmpty())
            throw new IllegalArgumentException("Expected an owned HTTP IPv4 loopback user-service origin");
    }

    private io.restassured.specification.RequestSpecification routeAudit(
            io.restassured.specification.RequestSpecification specification) {
        if (auditTunnel != null) {
            if (!(specification instanceof io.restassured.specification.FilterableRequestSpecification))
                throw new IllegalStateException("A filterable user audit request specification is required");
            var routed = (io.restassured.specification.FilterableRequestSpecification) specification;
            routed.removeHeader("Authorization").removeHeader("Cookie").removeCookies().auth().none();
            routed.baseUri("http://127.0.0.1").port(auditTunnel.getPort()).basePath("");
        }
        return specification;
    }

    public ValidatableResponseWrapper upsert(Map<String, Object> body) {
        return step("Upsert пользователя", () -> client.post(
                specification -> specification.body(body),
                Endpoints.User.USERS));
    }

    public ValidatableResponseWrapper upsertStatusOk(Map<String, Object> body) {
        return step("Проверяем 200 OK для POST /api/v2/users", () -> upsert(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper upsertStatusBadRequest(Map<String, Object> body) {
        return step("Проверяем 400 BAD_REQUEST для POST /api/v2/users", () -> upsert(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public ValidatableResponseWrapper getPermissions(String sub, String controlPointCode, List<String> roles) {
        return step("Получаем /api/v2/users/permissions", () -> client.get(
                specification -> {
                    specification.queryParam("sub", sub);
                    if (controlPointCode != null) {
                        specification.queryParam("control_point_code", controlPointCode);
                    }
                    roles.forEach(role -> specification.queryParam("roles", role));
                    return specification;
                },
                Endpoints.User.PERMISSIONS));
    }

    public ValidatableResponseWrapper getPermissionsStatusOk(String sub, String controlPointCode, List<String> roles) {
        return step("Проверяем 200 OK для /api/v2/users/permissions", () -> getPermissions(sub, controlPointCode, roles)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getPermissionsStatusBadRequest(String sub, String controlPointCode, List<String> roles) {
        return step("Проверяем 400 BAD_REQUEST для /api/v2/users/permissions", () -> getPermissions(sub, controlPointCode, roles)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public ValidatableResponseWrapper getAuditBySub(String sub) {
        return step("Получаем /api/v2/users/audit/{sub}", () -> client.get(
                specification -> routeAudit(specification).pathParam("sub", sub),
                Endpoints.User.AUDIT_BY_SUB));
    }

    public ValidatableResponseWrapper getAuditBySubStatusOk(String sub) {
        return step("Проверяем 200 OK для /api/v2/users/audit/{sub}", () -> getAuditBySub(sub)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getBySubs(List<String> subs) {
        return step("Получаем /api/v2/users/subs", () -> client.get(
                specification -> {
                    for (String sub : subs) {
                        specification.queryParam("subs", sub);
                    }
                    return specification;
                },
                Endpoints.User.BY_SUBS));
    }

    public ValidatableResponseWrapper getBySubsStatusOk(List<String> subs) {
        return step("Проверяем 200 OK для /api/v2/users/subs", () -> getBySubs(subs)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getByRole(String role) {
        return step("Получаем /api/v2/users/by-role", () -> client.get(
                specification -> specification.queryParam("role", role),
                Endpoints.User.BY_ROLE));
    }

    public ValidatableResponseWrapper getByRoleStatusOk(String role) {
        return step("Проверяем 200 OK для /api/v2/users/by-role", () -> getByRole(role)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getByEmployeeIds(List<String> employeeIds) {
        return step("Получаем /api/v2/users/by-employee-ids", () -> client.post(
                specification -> specification.body(Map.of("employeeIds", employeeIds)),
                Endpoints.User.BY_EMPLOYEE_IDS));
    }

    public ValidatableResponseWrapper getByEmployeeIdsStatusOk(List<String> employeeIds) {
        return step("Проверяем 200 OK для /api/v2/users/by-employee-ids", () -> getByEmployeeIds(employeeIds)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getById(String id) {
        return step("Получаем /api/v2/users/{id}", () -> client.get(
                specification -> specification.pathParam("id", id),
                Endpoints.User.USER_BY_ID));
    }

    public ValidatableResponseWrapper getByIdStatusOk(String id) {
        return step("Проверяем 200 OK для /api/v2/users/{id}", () -> getById(id)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }
}
