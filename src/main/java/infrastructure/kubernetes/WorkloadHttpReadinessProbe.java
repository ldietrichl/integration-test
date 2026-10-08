package infrastructure.kubernetes;

import config.services.core.TestEnvironment;
import config.services.rest.RestMtlsConfiguration;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.FilterableRequestSpecification;
import java.net.URI;

/** Captures strict TLS configuration once, before any mutation; no framework lifecycle dependency. */
public final class WorkloadHttpReadinessProbe implements WorkloadAvailabilityProbe {
    private final String baseUri;
    private final String path;
    private final RestAssuredConfig config;

    public static WorkloadHttpReadinessProbe ingressGet(String baseUri, String path) {
        if (!RestMtlsConfiguration.enabled(TestEnvironment.current()))
            throw WorkloadReadinessWait.failure("WORKLOAD_READINESS_MTLS_REQUIRED");
        RestMtlsConfiguration.requireApprovedTarget(baseUri);
        return new WorkloadHttpReadinessProbe(baseUri, path, RestMtlsConfiguration.apply(new RestAssuredConfig()));
    }

    WorkloadHttpReadinessProbe(String baseUri, String path, RestAssuredConfig config) {
        URI uri = URI.create(baseUri);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || !uri.getPath().isEmpty()
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
            throw new IllegalArgumentException("WORKLOAD_READINESS_ORIGIN");
        if (path == null || !path.matches("/[A-Za-z0-9_/-]+") || path.startsWith("//") || path.contains(".."))
            throw new IllegalArgumentException("WORKLOAD_READINESS_PATH");
        this.baseUri = baseUri;
        this.path = path;
        this.config = config.redirect(RedirectConfig.redirectConfig().followRedirects(false))
                .httpClient(HttpClientConfig.httpClientConfig().setParam("http.connection.timeout", 2000)
                        .setParam("http.socket.timeout", 2000)
                        .setParam("http.connection-manager.timeout", 2000L));
    }

    public static boolean successfulSample(int status) {
        if (status == 502 || status == 503 || status == 504) return false;
        if (status != 200) throw WorkloadReadinessWait.failure("WORKLOAD_READINESS_HTTP_" + status);
        return true;
    }

    @Override public boolean ready() {
        int status;
        try {
            var request = (FilterableRequestSpecification) io.restassured.RestAssured.given()
                    .config(config).baseUri(baseUri).basePath("")
                    .port(URI.create(baseUri).getPort() < 0
                            ? ("https".equals(URI.create(baseUri).getScheme()) ? 443 : 80) : URI.create(baseUri).getPort());
            request.noFilters();
            request.removeHeader("Authorization");
            request.removeHeader("Cookie");
            request.removeCookies();
            request.auth().none();
            status = request.get(path).statusCode();
        } catch (Exception failure) {
            throw new KubernetesDiagnosticException("WORKLOAD_READINESS_HTTP_TRANSPORT", "READ_ONLY_GET", failure);
        }
        System.out.println("[workload-http-ready] method=GET; status=" + status + "; businessRequestReplay=false");
        return successfulSample(status);
    }
}
