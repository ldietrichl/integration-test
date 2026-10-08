package steps.rest.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import constants.Endpoints.Scheduler;
import io.restassured.http.Method;
import io.restassured.response.Response;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.qameta.allure.Allure.step;

/** Reusable scheduler operations through the existing Platform V AT REST client. */
public final class SchedulerSteps {
    public static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient client;
    private final java.net.URI tunnelEndpoint;
    private record RequestEvidence(String method, String path, String requestId, java.time.Instant started,
                                   java.time.Instant finished, String transport,
                                   Map<String, Boolean> applicationIdentityPresence,
                                   Map<String, Object> outgoingRequest) {}
    private static final ThreadLocal<RequestEvidence> LAST_REQUEST = new ThreadLocal<>();

    private io.restassured.specification.RequestSpecification instrument(
            io.restassured.specification.RequestSpecification spec, String method, String path) {
        return instrument(spec, method, path, null);
    }

    private io.restassured.specification.RequestSpecification instrument(
            io.restassured.specification.RequestSpecification spec, String method, String path,
            Object expectedRegistryFixture) {
        LAST_REQUEST.remove();
        String requestId = java.util.UUID.randomUUID().toString();
        spec.header("X-Request-ID", requestId);
        spec.filter((request, response, context) -> {
            java.time.Instant started = java.time.Instant.now();
            String transport = tunnelEndpoint == null ? "CONFIGURED_INGRESS" : "FABRIC8_LOOPBACK";
            Map<String, Boolean> presence = Map.of(
                    "authorizationHeaderPresent", request.getHeaders().hasHeaderWithName("Authorization"),
                    "cookieHeaderPresent", request.getHeaders().hasHeaderWithName("Cookie"),
                    "requestCookiesPresent", request.getCookies().size() > 0);
            Map<String, Object> outgoing = SchedulerRequestDiagnostics.inspect(request, expectedRegistryFixture);
            LAST_REQUEST.set(new RequestEvidence(method, path, requestId, started, null, transport, presence, outgoing));
            if (expectedRegistryFixture != null) {
                Map<String, Object> safe = new LinkedHashMap<>(outgoing);
                safe.put("requestId", requestId); safe.put("transport", transport);
                safe.put("method", method); safe.put("path", path);
                io.qameta.allure.Allure.addAttachment("Scheduler outgoing JSON contract",
                        "application/json", JSON.valueToTree(safe).toPrettyString(), ".json");
                System.out.println("[scheduler-http-request] transport=" + transport + "; requestId=" + requestId
                        + "; contentType=" + outgoing.get("contentType") + "; accept=" + outgoing.get("accept")
                        + "; acceptHeaderCount=" + outgoing.get("acceptHeaderCount")
                        + "; requestContractSatisfied=" + outgoing.get("requestContractSatisfied")
                        + "; failureReasons=" + outgoing.get("failureReasons")
                        + "; bodyValuesOmitted=true");
                SchedulerRequestDiagnostics.requireRegistryContract(outgoing);
            }
            var result = context.next(request, response);
            LAST_REQUEST.set(new RequestEvidence(method, path, requestId, started, java.time.Instant.now(), transport, presence, outgoing));
            System.out.println("[scheduler-http] method=" + method + "; path=" + path
                    + "; requestId=" + requestId + "; status=" + result.statusCode());
            if (result.statusCode() >= 400) {
                // No response body, challenge realm, tokens or cookie values enter this evidence.
                String challenge = result.getHeader("WWW-Authenticate");
                String scheme = challenge == null ? "NONE"
                        : challenge.matches("(?is)^Basic(?:\\s.*)?$") ? "BASIC"
                        : challenge.matches("(?is)^Bearer(?:\\s.*)?$") ? "BEARER" : "OTHER";
                String contentType = result.getContentType();
                String media = contentType == null ? "NONE"
                        : contentType.toLowerCase(java.util.Locale.ROOT).contains("json") ? "JSON"
                        : contentType.toLowerCase(java.util.Locale.ROOT).contains("html") ? "HTML" : "OTHER";
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("requestId", requestId);
                evidence.put("method", method);
                evidence.put("path", path);
                evidence.put("transport", transport);
                evidence.put("httpStatus", result.statusCode());
                evidence.put("applicationIdentityPresence", presence);
                evidence.put("wwwAuthenticateScheme", scheme);
                evidence.put("responseMediaType", media);
                evidence.put("setCookiePresent", result.getHeader("Set-Cookie") != null);
                evidence.put("responseRequestIdMatches", requestId.equals(result.getHeader("X-Request-ID")));
                evidence.put("serverHeaderPresent", result.getHeader("Server") != null);
                evidence.put("responseBodyLogged", false);
                evidence.put("automaticRetryPerformed", false);
                io.qameta.allure.Allure.addAttachment("Scheduler HTTP failure boundary",
                        "application/json", JSON.valueToTree(evidence).toPrettyString(), ".json");
            }
            return result;
        });
        return spec;
    }

    public SchedulerSteps(RestClient client) {
        this.client = client;
        String endpoint = infrastructure.kubernetes.SchedulerRegressionSession.schedulerEndpoint();
        this.tunnelEndpoint = endpoint == null ? null : java.net.URI.create(endpoint);
    }

    /** Per-instance routing for an owned diagnostic tunnel; never mutates shared properties. */
    public SchedulerSteps(RestClient client, String baseUri) {
        this.client = client;
        this.tunnelEndpoint = java.net.URI.create(baseUri);
        if (!"http".equals(tunnelEndpoint.getScheme()) || !"127.0.0.1".equals(tunnelEndpoint.getHost())
                || tunnelEndpoint.getPort() < 1 || tunnelEndpoint.getPort() > 65535
                || tunnelEndpoint.getUserInfo() != null || tunnelEndpoint.getQuery() != null
                || tunnelEndpoint.getFragment() != null || !tunnelEndpoint.getPath().isEmpty()) {
            throw new IllegalArgumentException("Expected an owned HTTP IPv4 loopback tunnel origin");
        }
    }

    private io.restassured.specification.RequestSpecification route(
            io.restassured.specification.RequestSpecification specification) {
        if (tunnelEndpoint != null)
            specification.baseUri("http://127.0.0.1").port(tunnelEndpoint.getPort()).basePath("");
        return specification;
    }

    public ValidatableResponseWrapper call(String method, String path, Object body) {
        return call(method, path, body, null);
    }

    public ValidatableResponseWrapper call(String method, String path, Object body, Integer readTimeoutSeconds) {
        return call(method, path, body, readTimeoutSeconds, false);
    }

    private ValidatableResponseWrapper call(String method, String path, Object body,
                                            Integer readTimeoutSeconds, boolean registryContract) {
        return step("Scheduler " + method + " " + path, () -> client.request(Method.valueOf(method), spec -> {
            instrument(route(spec), method, path, registryContract ? body : null);
            if (readTimeoutSeconds != null) {
                if (tunnelEndpoint == null) {
                    var settings = new config.services.core.SchedulerSettings();
                    String baseUri = settings.baseUri();
                    spec.config(config.services.rest.SchedulerRestConfiguration.requestConfig(
                            settings, "", baseUri, readTimeoutSeconds));
                } else {
                    spec.config(config.services.rest.SchedulerTunnelRestConfiguration
                            .requestConfig(readTimeoutSeconds));
                }
            }
            if (body != null) {
                // Do not rely on a default service annotation for JSON media types.
                spec.contentType(io.restassured.http.ContentType.JSON)
                        .accept("application/json").body(body);
            }
            return spec;
        }, path));
    }

    private ValidatableResponseWrapper captureCreated(ValidatableResponseWrapper result, Object request) {
        int status = result.toResponse().statusCode();
        if (status >= 200 && status < 300) {
            try {
                JsonNode id = JSON.readTree(result.toResponse().asByteArray()).path("id");
                if (id.isIntegralNumber() && id.canConvertToLong() && id.longValue() > 0)
                    infrastructure.scheduler.SchedulerFixtureLedger.observeCreate(request, id.longValue());
            } catch (java.io.IOException malformedResponse) {
                // Leave wire-contract assertions authoritative; never print the body or fabricate ownership.
                System.out.println("[scheduler-fixtures] createResponseIdUnavailable=true");
            }
        }
        return result;
    }

    public ValidatableResponseWrapper createV2(Object body) {
        return captureCreated(call("POST", Scheduler.V2_TASK, body), body);
    }

    public ValidatableResponseWrapper createV1(Object body) {
        return captureCreated(call("POST", Scheduler.V1_TASK, body), body);
    }

    public ValidatableResponseWrapper registry(Object body) {
        return call("POST", Scheduler.V2_TASKS, body);
    }

    /** Application-anonymous request; OpenShift tunnel authentication is unchanged.
     * Strip cookies at dispatch, including cookies supplied by a client specification.
     * No anonymous request is retried with credentials.
     */
    public ValidatableResponseWrapper registryWithoutIdentity(Object body) {
        return withoutIdentity(Scheduler.V2_TASKS, body);
    }

    public ValidatableResponseWrapper createV2WithoutIdentity(Object body) {
        return captureCreated(withoutIdentity(Scheduler.V2_TASK, body), body);
    }

    private ValidatableResponseWrapper withoutIdentity(String path, Object body) {
        return step("Scheduler POST without application identity " + path, () -> client.request(
                Method.POST, specification -> {
                    instrument(route(specification), "POST", path);
                    specification.auth().none();
                    specification.filter((request, response, context) -> {
                        request.removeHeader("Authorization");
                        request.removeCookies();
                        request.removeHeader("Cookie");
                        return context.next(request, response);
                    });
                    return specification.body(body);
                }, path));
    }

    public ValidatableResponseWrapper registryWithIdentity(Object body, String accessToken) {
        return step("Scheduler: registry as the resolved application user", () -> client.request(
                Method.POST, specification -> {
                    instrument(route(specification), "POST", Scheduler.V2_TASKS);
                    specification.auth().none();
                    specification.filter((request, response, context) -> {
                        request.removeHeader("Authorization");
                        request.removeHeader("Cookie");
                        request.removeCookies();
                        request.header("Authorization", "Bearer " + accessToken);
                        return context.next(request, response);
                    });
                    return specification.body(body);
                }, Scheduler.V2_TASKS));
    }

    public ValidatableResponseWrapper history(Object body) {
        return call("POST", Scheduler.V1_HISTORY, body);
    }

    public ValidatableResponseWrapper getV1(long id) {
        return call("GET", Scheduler.V1_TASK + "/" + id, null);
    }

    public ValidatableResponseWrapper byObject(Object body) {
        return call("POST", Scheduler.V1_OBJECTS, body);
    }

    public ValidatableResponseWrapper deleteV1(List<Long> ids) {
        return call("POST", Scheduler.V1_DELETE, Map.of("ids", ids));
    }

    public ValidatableResponseWrapper deletePlanned(String type, long objectId) {
        return call("POST", Scheduler.V2_DELETE, Map.of("objectType", type, "objectId", objectId));
    }

    public ValidatableResponseWrapper deletePlanned(String type, long objectId, int readTimeoutSeconds) {
        return call("POST", Scheduler.V2_DELETE, Map.of("objectType", type, "objectId", objectId), readTimeoutSeconds);
    }

    /** Metrics use text exposition; business requests keep the project JSON defaults. */
    public ValidatableResponseWrapper getText(String path) {
        return step("Scheduler GET text " + path, () -> client.request(
                Method.GET, spec -> instrument(route(spec), "GET", path).accept("text/plain"), path));
    }

    public static JsonNode expect(ValidatableResponseWrapper response, int status) {
        Response raw = response.toResponse();
        int actual = raw.statusCode();
        if (actual != status) captureHttpFailure(status, raw);
        try {
            response.should(RestMatchers.haveStatusCode(status));
        } catch (AssertionError failure) {
            throw new AssertionError("Scheduler HTTP status: expected " + status + ", received " + actual, failure);
        }
        String body = raw.asString();
        try {
            return body.isBlank() ? JSON.nullNode() : JSON.readTree(body);
        } catch (Exception exception) {
            throw new AssertionError("Response is not JSON", exception);
        }
    }

    /** Only this bounded, read-only readiness probe is retried; business calls are never replayed. */
    public void awaitIngressReady(String phase) {
        var settings = new config.services.core.SchedulerSettings();
        if (tunnelEndpoint != null || settings.tunnelEnabled())
            throw new IllegalStateException("INGRESS_READINESS_ROUTE: an owned tunnel cannot prove mTLS ingress readiness");
        config.services.rest.RestMtlsConfiguration.requireApprovedTarget(settings.baseUri());
        int timeout = boundedSetting(settings, "ingress.ready.timeout.seconds", 120, 10, 600);
        int interval = boundedSetting(settings, "ingress.ready.poll.millis", 2000, 250, 10000);
        step("Wait for the scheduler registry through its actual mTLS ingress: " + phase, () -> {
            long started = System.nanoTime();
            long deadline = started + java.util.concurrent.TimeUnit.SECONDS.toNanos(timeout);
            int attempt = 0;
            int consecutive = 0;
            Response last = null;
            while (System.nanoTime() < deadline) {
                if (Thread.currentThread().isInterrupted())
                    throw new IllegalStateException("INGRESS_READINESS_INTERRUPTED");
                attempt++;
                // POST /tasks is a registry read, not POST /task (creation).
                var response = call("POST", Scheduler.V2_TASKS, Map.of("page", 0, "size", 1), 5);
                last = response.toResponse();
                int status = last.statusCode();
                System.out.println("[scheduler-ingress] phase=" + phase + "; attempt=" + attempt
                        + "; status=" + status + "; pollSleepMillis=" + interval
                        + "; remainingMillis=" + Math.max(0, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))
                        + "; elapsedMillis="
                        + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                if (status == 200) {
                    JsonNode json = expect(response, 200);
                    if (!json.path("content").isArray())
                        throw new AssertionError("INGRESS_READINESS_CONTRACT: HTTP 200 is not a registry response");
                    if (++consecutive == 2) {
                        io.qameta.allure.Allure.addAttachment("Scheduler ingress ready",
                                "phase=" + phase + "\nattempts=" + attempt
                                        + "\nconsecutiveRegistryResponses=2\nbusinessRequestReplay=false");
                        return;
                    }
                } else {
                    consecutive = 0;
                    if (status != 502 && status != 503 && status != 504) {
                        expect(response, 200); // TLS/auth/validation/service errors are not treated as warming up.
                    }
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try {
                    java.util.concurrent.TimeUnit.NANOSECONDS.sleep(Math.min(remaining,
                            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(interval)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("INGRESS_READINESS_INTERRUPTED", interrupted);
                }
            }
            if (last != null) captureHttpFailure(200, last);
            throw new AssertionError("INGRESS_READINESS_TIMEOUT: phase=" + phase + "; attempts=" + attempt
                    + "; configuredSeconds=" + timeout + "; two stable registry responses were not observed");
        });
    }

    private static int boundedSetting(config.services.core.SchedulerSettings settings,
                                      String key, int fallback, int min, int max) {
        int value;
        try { value = Integer.parseInt(settings.optional(key, Integer.toString(fallback))); }
        catch (NumberFormatException invalid) { throw new IllegalStateException("Invalid scheduler setting: " + key); }
        if (value < min || value > max)
            throw new IllegalStateException("Scheduler setting out of bounds: " + key);
        return value;
    }

    private static void captureHttpFailure(int expected, Response response) {
        RequestEvidence request = LAST_REQUEST.get();
        try {
            Map<String, Object> http = httpEvidence(expected, response, request);
            String payload = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(http);
            System.out.println("[scheduler-http-diagnostic] " + payload);
            io.qameta.allure.Allure.addAttachment("Scheduler HTTP " + response.statusCode() + " diagnostic",
                    "application/json", payload, ".json");
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() >= 500) {
                captureWorkloadSnapshot(request, response);
                if (response.statusCode() == 401 || response.statusCode() == 403) captureControlRead();
            }
        } catch (RuntimeException | java.io.IOException diagnosticFailure) {
            System.out.println("[scheduler-http-diagnostic] unavailableType="
                    + diagnosticFailure.getClass().getSimpleName() + "; primaryAssertionPreserved=true");
        } finally {
            if (request == null) LAST_REQUEST.remove(); else LAST_REQUEST.set(request);
        }
    }

    /** One valid read for comparison, never an authenticated retry of the failed operation. */
    private static void captureControlRead() {
        try {
            var scheduler = new flow.SchedulerInfrastructureFlow().restCustomSteps().schedulerSteps();
            var result = scheduler.call("POST", Scheduler.V2_TASKS, Map.of("page", 0, "size", 1), 5)
                    .toResponse();
            RequestEvidence control = LAST_REQUEST.get();
            String text = "controlMethod=POST\ncontrolPath=" + Scheduler.V2_TASKS
                    + "\ncontrolStatus=" + result.statusCode()
                    + "\ncontrolRequestId=" + (control == null ? "unavailable" : control.requestId())
                    + "\nrequestBodyAndCredentialsOmitted=true\nprimaryFailureUnchanged=true"
                    + "\nnote=Ordinary configured application identity; not an anonymous-auth retry.";
            System.out.println("[scheduler-http-control] " + text.replace('\n', ';'));
            io.qameta.allure.Allure.addAttachment("Independent valid registry control after HTTP failure", text);
        } catch (RuntimeException failure) {
            System.out.println("[scheduler-http-control] unavailableType=" + failure.getClass().getSimpleName()
                    + "; primaryFailureUnchanged=true");
        }
    }

    private static void captureWorkloadSnapshot(RequestEvidence request, Response response) {
        try {
            var environment = io.perfeccionista.framework.Environment.getForCurrentThread();
            var settings = config.services.container.KubernetesTunnelSettings.from(environment);
            var api = config.services.container.ContainerServiceKubernetesAccess.client(settings);
            String deployment = new config.services.core.StandSettings().required("workloads.scheduler.deployment");
            String since = (request == null ? java.time.Instant.now().minusSeconds(120)
                    : request.started().minusSeconds(15)).toString();
            var service = api.services().inNamespace(settings.namespace).withName(settings.service).get();
            Map<String, String> selector = service == null || service.getSpec() == null
                    || service.getSpec().getSelector() == null ? Map.of() : service.getSpec().getSelector();
            List<io.fabric8.kubernetes.api.model.Pod> observedPods = new java.util.ArrayList<>();
            List<Map<String, Object>> pods = new java.util.ArrayList<>();
            int logPods = 0;
            for (var pod : api.pods().inNamespace(settings.namespace).list().getItems()) {
                if (pod.getMetadata() == null) continue;
                String name = pod.getMetadata().getName();
                if (name == null || !(name.startsWith(deployment + "-")
                        || infrastructure.kubernetes.KubernetesPodDiagnostics.matchesSelector(pod, selector))) continue;
                observedPods.add(pod);
                if (pods.size() < 30)
                    pods.add(infrastructure.kubernetes.KubernetesPodDiagnostics.describe(pod, selector));
                if (settings.logsEnabled && logPods++ < 2) {
                    try {
                    String logs = api.pods().inNamespace(settings.namespace).withName(name)
                            .inContainer(settings.logsContainer).usingTimestamps()
                            .sinceTime(since).tailingLines(1500).getLog();
                    String highlights = requestLogHighlights(logs, request, response);
                    io.qameta.allure.Allure.addAttachment("Scheduler exception starts and causes - " + name,
                            "text/plain", redactAndLimit("sinceUtc=" + since + "\n" + highlights, 16384), ".log");
                    io.qameta.allure.Allure.addAttachment("Scheduler bounded log window after HTTP failure - " + name,
                            "text/plain", redactAndLimit("sinceUtc=" + since + "\nmaxLines=1500\n" + logs, 65536), ".log");
                    } catch (RuntimeException logFailure) {
                        io.qameta.allure.Allure.addAttachment("Scheduler pod log unavailable - " + name,
                                JSON.valueToTree(infrastructure.kubernetes.KubernetesPodDiagnostics
                                        .unavailable(logFailure)).toPrettyString());
                    }
                }
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("capturedAtUtc", java.time.Instant.now().toString());
            snapshot.put("service", settings.service);
            snapshot.put("serviceSelector", selector);
            snapshot.put("podScope", "SERVICE_SELECTOR_OR_DEPLOYMENT_NAME_PREFIX; not HTTP backend identity");
            snapshot.put("podCount", observedPods.size());
            snapshot.put("pods", pods);
            snapshot.put("podsTruncated", observedPods.size() > 30);
            snapshot.put("controlPlane", infrastructure.kubernetes.KubernetesPodDiagnostics.controlPlane(
                    api, settings.namespace, settings.service, deployment, observedPods));
            String payload = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
            System.out.println("[scheduler-workload-diagnostic] " + payload);
            io.qameta.allure.Allure.addAttachment("Scheduler workload snapshot after HTTP failure",
                    "application/json", payload, ".json");
        } catch (RuntimeException | java.io.IOException failure) {
            String unavailable = "type=" + failure.getClass().getSimpleName()
                    + ", message suppressed, primary HTTP assertion preserved";
            System.out.println("[scheduler-workload-diagnostic] unavailable: " + unavailable);
            io.qameta.allure.Allure.addAttachment("Scheduler workload snapshot unavailable", unavailable);
        }
    }

    /** Time proximity alone is not causality. Mark skew-margin lines separately. */
    private static String requestLogHighlights(String logs, RequestEvidence request, Response response) {
        java.time.Instant started = request == null ? java.time.Instant.now().minusSeconds(120) : request.started();
        java.time.Instant finished = request == null || request.finished() == null ? java.time.Instant.now() : request.finished();
        java.time.Instant from = started.minusSeconds(3);
        java.time.Instant to = finished.plusSeconds(3);
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        if (request != null) ids.add(request.requestId());
        for (String header : List.of("X-Request-ID", "X-Correlation-ID", "X-B3-TraceId")) {
            String id = response.getHeader(header);
            if (id != null && id.matches("[a-fA-F0-9-]{16,64}")) ids.add(id);
        }
        String traceparent = response.getHeader("traceparent");
        if (traceparent != null && traceparent.matches("[a-fA-F0-9]{2}-[a-fA-F0-9]{32}-[a-fA-F0-9]{16}-[a-fA-F0-9]{2}"))
            ids.add(traceparent.split("-")[1]);
        java.util.List<String> window = new java.util.ArrayList<>();
        java.util.List<java.time.Instant> times = new java.util.ArrayList<>();
        int unparsed = 0;
        for (String line : logs.lines().toList()) {
            int separator = line.indexOf(' ');
            if (separator <= 0) { unparsed++; continue; }
            try {
                java.time.Instant time = java.time.Instant.parse(line.substring(0, separator));
                if (!time.isBefore(from) && !time.isAfter(to)) { window.add(line); times.add(time); }
            } catch (java.time.format.DateTimeParseException invalid) { unparsed++; }
        }
        java.util.SortedSet<Integer> indexes = new java.util.TreeSet<>();
        boolean matchedId = false;
        for (int i = 0; i < window.size(); i++) {
            String line = window.get(i);
            if (ids.stream().noneMatch(line::contains)) continue;
            matchedId = true;
            for (int j = Math.max(0, i - 2); j < Math.min(window.size(), i + 9); j++) indexes.add(j);
        }
        if (!matchedId) {
            for (int i = 0; i < window.size(); i++) {
                String line = window.get(i);
                boolean exception = java.util.regex.Pattern.compile(
                        "\\b[\\w.$]*(?:Exception|Error)(?:\\$[\\w$]+)*\\s*:").matcher(line).find();
                boolean lifecycle = line.contains("Commencing graceful shutdown")
                        || line.contains("Graceful shutdown complete")
                        || line.contains("Closing JPA EntityManagerFactory");
                if (line.contains(" ERROR ") || line.contains(" WARN ") || exception || lifecycle
                        || line.contains("Caused by:") || line.contains("Suppressed:")) {
                    for (int j = Math.max(0, i - 1); j < Math.min(window.size(), i + 7); j++) indexes.add(j);
                }
            }
        }
        StringBuilder evidence = new StringBuilder("correlation=")
                .append(matchedId ? "REQUEST_OR_RESPONSE_ID_IN_TIME_WINDOW" : "TIME_WINDOW_ONLY")
                .append("\ncausalityProvenByTime=false\nresponseIdPropagationVerified=false")
                .append("\nclientRequestId=").append(request == null ? "unavailable" : request.requestId())
                .append("\nrequestStartedUtc=").append(started).append("\nrequestFinishedUtc=").append(finished)
                .append("\nwindowFromUtc=").append(from).append("\nwindowToUtc=").append(to)
                .append("\nclockSkewAllowanceSeconds=3\nwindowLines=").append(window.size())
                .append("\nunparsedTimestampLines=").append(unparsed)
                .append("\nmatchingIdContextIsNotNecessarilyTheSameRequest=true")
                .append("\nnote=Skew-margin and adjacent context may belong to other requests; no component is blamed automatically.\n");
        java.util.List<Integer> ordered = new java.util.ArrayList<>(indexes);
        // Select closest evidence rather than the oldest or newest unrelated exception.
        ordered.sort(java.util.Comparator.comparingLong(index ->
                distanceMillis(times.get(index), started, finished)));
        java.util.List<Integer> selected = new java.util.ArrayList<>(ordered.subList(0, Math.min(80, ordered.size())));
        java.util.Collections.sort(selected);
        for (int index : selected) {
            java.time.Instant time = times.get(index);
            boolean within = !time.isBefore(started) && !time.isAfter(finished);
            boolean exactId = ids.stream().anyMatch(window.get(index)::contains);
            evidence.append(exactId ? "[ID_MATCH] " : within ? "[REQUEST_INTERVAL_TIME_ONLY] "
                    : "[SKEW_MARGIN_POSSIBLY_NEIGHBOR] ").append(window.get(index)).append('\n');
        }
        return evidence.toString();
    }

    private static long distanceMillis(java.time.Instant time, java.time.Instant from, java.time.Instant to) {
        if (time.isBefore(from)) return java.time.Duration.between(time, from).toMillis();
        if (time.isAfter(to)) return java.time.Duration.between(to, time).toMillis();
        return 0;
    }

    private static Map<String, Object> httpEvidence(int expected, Response response, RequestEvidence request) {
        Map<String, Object> http = new LinkedHashMap<>();
        http.put("expectedStatus", expected);
        http.put("actualStatus", response.statusCode());
        http.put("contentType", String.valueOf(response.contentType())); // Legacy response field.
        http.put("responseContentType", String.valueOf(response.contentType()));
        if (request != null) {
            http.put("method", request.method());
            http.put("path", request.path());
            http.put("transport", request.transport());
            http.put("clientRequestId", request.requestId());
            http.put("requestStartedUtc", request.started().toString());
            http.put("requestFinishedUtc", request.finished() == null ? "unavailable" : request.finished().toString());
            http.put("applicationIdentityPresence", request.applicationIdentityPresence());
            http.put("outgoingRequest", request.outgoingRequest());
            http.put("applicationPrincipalEqualityProven", false);
            if (request.finished() != null)
                http.put("elapsedMillis", java.time.Duration.between(request.started(), request.finished()).toMillis());
        }
        for (String name : List.of("X-Request-ID", "X-Correlation-ID", "traceparent", "X-B3-TraceId",
                "Server", "Via", "WWW-Authenticate")) {
            String value = response.getHeader(name);
            if (value != null && !value.isBlank()) http.put(name, redactAndLimit(value, 512));
        }
        http.put("body", redactAndLimit(response.asString(), 4096));
        return http;
    }

    /** Read-only diagnostic. Never validates early or automatically replays the failed request. */
    public Map<String, Object> diagnosticRegistry(Object body, int expected, String label) {
        Response response = call("POST", Scheduler.V2_TASKS, body, 10, true).toResponse();
        RequestEvidence request = LAST_REQUEST.get();
        Map<String, Object> evidence = httpEvidence(expected, response, request);
        evidence.put("label", label);
        evidence.put("mutationDispatched", false);
        boolean contentArray = false;
        try { contentArray = JSON.readTree(response.asString()).path("content").isArray(); }
        catch (java.io.IOException | RuntimeException ignored) { }
        evidence.put("registryContentArray", contentArray);
        // Attach every outcome, including valid controls. No credential-bearing request spec is serialized.
        io.qameta.allure.Allure.addAttachment("HTTP diagnostic: " + label,
                "application/json", JSON.valueToTree(evidence).toPrettyString(), ".json");
        System.out.println("[scheduler-http-probe] label=" + label + "; expected=" + expected
                + "; actual=" + response.statusCode() + "; requestId="
                + (request == null ? "unavailable" : request.requestId()));
        if (expected != 200 || response.statusCode() != 200) captureWorkloadSnapshot(request, response);
        return evidence;
    }

    private static String redactAndLimit(String value, int limit) {
        if (value == null) return "";
        String names = "authorization|cookie|set-cookie|token|access_token|refresh_token|password"
                + "|client-secret|client_secret|private-key|private_key|client-key-data|client-certificate-data"
                + "|jsessionid|session[-_]?id|session[-_]?login|employee[-_]?id|client[-_]?ip|fio|email"
                + "|created_by_fio|created_by_email_sigma|employeeId|lastName|firstName|patronymic";
        String safe = value
                .replaceAll("(?s)-----BEGIN [^-]+-----.*?-----END [^-]+-----", "<PEM omitted>")
                .replaceAll("(?i)(\"(?:" + names + ")\"\\s*:\\s*)\"[^\"]*\"", "$1\"<redacted>\"")
                .replaceAll("(?i)((?:" + names + ")\\s*[:=]\\s*)\"[^\"]*\"", "$1<redacted>")
                .replaceAll("(?i)((?:" + names + ")\\s*[:=]\\s*)'[^']*'", "$1<redacted>")
                .replaceAll("(?i)((?:" + names + ")\\s*[:=]\\s*)[^\\s,;]+", "$1<redacted>")
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*", "Bearer <redacted>");
        if (safe.length() <= limit) return safe;
        int half = limit / 2;
        return safe.substring(0, half) + "\n...[middle omitted]...\n" + safe.substring(safe.length() - half);
    }
}
