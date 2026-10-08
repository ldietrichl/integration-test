package infrastructure.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import config.services.core.SchedulerSettings;
import config.services.rest.RestMtlsConfiguration;
import config.services.rest.SchedulerRestConfiguration;
import config.services.rest.SchedulerTunnelRestConfiguration;
import constants.Endpoints.Scheduler;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.specification.FilterableRequestSpecification;
import java.util.concurrent.TimeUnit;
import steps.rest.scheduler.SchedulerSteps;

/** Captures configuration before framework teardown; root close needs no Environment or Allure test. */
public final class SchedulerRootReadinessProbe implements infrastructure.kubernetes.WorkloadReadinessProbe {
    private final boolean ingress;
    private final String baseUri;
    private final String token;
    private final RestAssuredConfig config;
    private final int timeoutSeconds;
    private final int pollMillis;

    public SchedulerRootReadinessProbe(SchedulerSettings settings) {
        ingress = !settings.tunnelEnabled();
        baseUri = ingress ? settings.baseUri() : "";
        if (ingress) {
            if (!RestMtlsConfiguration.enabled(settings.environment))
                throw new IllegalStateException("ROOT_HTTP_CONFIGURATION: explicit mTLS or an owned tunnel required");
            RestMtlsConfiguration.requireApprovedTarget(baseUri);
        }
        config = (ingress ? SchedulerRestConfiguration.requestConfig(settings, "", baseUri, 5)
                : SchedulerTunnelRestConfiguration.requestConfig(5))
                .redirect(RedirectConfig.redirectConfig().followRedirects(false));
        token = settings.optional("token", "").isBlank() ? "" : settings.required("token");
        timeoutSeconds = bounded(settings, "ingress.ready.timeout.seconds", 120, 10, 600);
        pollMillis = bounded(settings, "ingress.ready.poll.millis", 2000, 250, 10000);
    }
    public boolean ingress() { return ingress; }
    public int timeoutSeconds() { return timeoutSeconds; }

    /** Pure response policy used by offline contract checks too. Never retries 401/403/500 or malformed JSON. */
    public static boolean successfulSample(int status, JsonNode body) {
        if (status == 502 || status == 503 || status == 504) return false;
        if (status != 200) throw new IllegalStateException("ROOT_HTTP_STATUS_" + status);
        if (body == null || !body.path("content").isArray())
            throw new IllegalStateException("ROOT_HTTP_REGISTRY_CONTRACT");
        return true;
    }

    public void verify(String ownedLoopback) {
        String origin = baseUri;
        if (!ingress) {
            var uri = java.net.URI.create(ownedLoopback);
            if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                    || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.getPath().isEmpty())
                throw new IllegalStateException("ROOT_HTTP_OWNED_LOOPBACK_REQUIRED");
            origin = ownedLoopback;
        }
        long started = System.nanoTime();
        long deadline = started + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        int consecutive = 0;
        int attempt = 0;
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("ROOT_HTTP_INTERRUPTED");
            attempt++;
            int status;
            JsonNode body = null;
            try {
                // Clear global logging/Allure filters, cookies and auth: only the captured application token is used.
                var request = (FilterableRequestSpecification) io.restassured.RestAssured.given()
                        .config(config).baseUri(origin).basePath("").port(java.net.URI.create(origin).getPort() < 0
                                ? 443 : java.net.URI.create(origin).getPort());
                request.noFilters();
                request.removeHeader("Authorization");
                request.removeHeader("Cookie");
                request.removeCookies();
                request.auth().none();
                request.contentType("application/json").accept("application/json");
                if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
                var response = request.body("{\"page\":0,\"size\":1}").post(Scheduler.V2_TASKS);
                status = response.statusCode();
                if (status == 200) body = SchedulerSteps.JSON.readTree(response.asByteArray());
            } catch (Exception failure) {
                // Exceptions from HTTP libraries may contain credentials/response content.
                throw new IllegalStateException("ROOT_HTTP_TRANSPORT_OR_JSON: type=" + failure.getClass().getSimpleName());
            }
            System.out.println("[scheduler-root-http] transport=" + (ingress ? "MTLS_INGRESS" : "FABRIC8_LOOPBACK")
                    + "; attempt=" + attempt + "; status=" + status + "; pollSleepMillis=" + pollMillis
                    + "; elapsedMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            consecutive = successfulSample(status, body) ? consecutive + 1 : 0;
            if (consecutive >= 2 && System.nanoTime() < deadline) {
                System.out.println("[scheduler-root-http] RootHttpReady=true; consecutiveRegistryResponses=2"
                        + "; businessRequestReplay=false; responseBodyLogged=false");
                return;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try { TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(pollMillis))); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("ROOT_HTTP_INTERRUPTED");
            }
        }
        throw new IllegalStateException("ROOT_HTTP_TIMEOUT: configuredSeconds=" + timeoutSeconds);
    }
    private static int bounded(SchedulerSettings settings, String key, int fallback, int min, int max) {
        int value = Integer.parseInt(settings.optional(key, Integer.toString(fallback)));
        if (value < min || value > max) throw new IllegalStateException("Root HTTP setting out of bounds: " + key);
        return value;
    }
}
