package infrastructure.kubernetes;

import config.services.container.KubernetesTunnelSettings;
import config.services.container.ContainerServiceKubernetesAccess;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.service.Service;
import io.perfeccionista.framework.service.ServiceConfiguration;
import ru.sber.qa.containers.services.ContainerService;
import ru.sber.qa.containers.client.ContainerServiceClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Framework-owned, lazy infrastructure. No pod restart, exec, patch or database mutation. */
public final class KubernetesTunnelService implements Service, AutoCloseable {
    private Environment environment;
    private ContainerServiceClient frameworkClient;
    private TunnelSession nativeSession;
    private TunnelSession cliSession;
    private TunnelSession dictionarySession;
    private TunnelSession userSession;
    private Process pendingCliProcess;
    private Thread shutdownHook;
    private Config nativeConfiguration;
    private OcServiceLogs.Target logTarget;
    private Instant scenarioStarted;
    private String scenarioId = "scheduler";
    private String logPreparationFailure;
    private Map<String, Object> selectionEvidence = Map.of("status", "NOT_ATTEMPTED");
    private Map<String, Object> readinessEvidence = Map.of("status", "NOT_ATTEMPTED");
    private final Deque<String> cliMessages = new ArrayDeque<>();
    private static final Pattern FORWARDING =
            Pattern.compile("^Forwarding from 127\\.0\\.0\\.1:(\\d+) -> (\\d+).*$");

    public record Target(String namespace, String service, String pod,
                         int servicePort, int targetPort, List<String> images, String podUid) {
        public Map<String, Object> description() {
            return Map.of("namespace", namespace, "service", service, "pod", pod,
                    "servicePort", servicePort, "targetPort", targetPort, "images", images, "podUid", podUid);
        }
    }

    @Override
    public void init(Environment environment) {
        this.environment = environment;
        Environment.addAfterAllHook(this::close);
    }

    @Override
    public void init(Environment environment, ServiceConfiguration configuration) { init(environment); }

    public KubernetesTunnelSettings settings() { return KubernetesTunnelSettings.from(environment); }

    public synchronized void beginScenario(String id) {
        scenarioId = id;
        scenarioStarted = Instant.now();
        logTarget = null;
        logPreparationFailure = null;
        nativeConfiguration = null;
        selectionEvidence = Map.of("status", "NOT_ATTEMPTED");
        readinessEvidence = Map.of("status", "NOT_ATTEMPTED");
    }

    public synchronized OcServiceLogs.Capture captureScenarioLogs() {
        if (logPreparationFailure != null)
            return new OcServiceLogs.Capture(Map.of("scenario", scenarioId,
                    "status", "TARGET_DISCOVERY_FAILED", "failureType", logPreparationFailure), "");
        if (logTarget == null && settings().logsEnabled) {
            try { target(); }
            catch (RuntimeException failure) {
                logPreparationFailure = safeCauseCode(failure);
                return new OcServiceLogs.Capture(Map.of("scenario", scenarioId,
                        "status", "TARGET_DISCOVERY_FAILED", "failureType", logPreparationFailure), "");
            }
        }
        OcServiceLogs.Capture capture = OcServiceLogs.capture(settings(), frameworkClient(), client(), logTarget,
                scenarioId, scenarioStarted, Instant.now());
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(
                ContainerServiceKubernetesAccess.safeDiagnostics(settings(), client()));
        metadata.putAll(capture.metadata());
        return new OcServiceLogs.Capture(metadata, capture.text());
    }

    public synchronized Map<String, Object> tlsDiagnostics() {
        Config configuration = nativeConfiguration;
        String source = "framework KubernetesClient.getConfiguration";
        if (configuration == null) {
            configuration = settings().nativeClientConfiguration();
            source = "native stand CA overlay plus kubeconfig; no effective native client configuration captured";
        }
        return KubernetesTlsDiagnostics.snapshot(configuration, source);
    }

    private KubernetesClient client() {
        return ContainerServiceKubernetesAccess.client(settings());
    }

    private ContainerServiceClient frameworkClient() {
        if (frameworkClient == null)
            frameworkClient = ContainerServiceKubernetesAccess.frameworkClient(settings());
        return frameworkClient;
    }

    public synchronized boolean reusesFrameworkClients() {
        KubernetesClient first = client();
        return first == client();
    }

    public synchronized Target target() {
        KubernetesTunnelSettings settings = settings();
        selectionEvidence = Map.of("status", "DISCOVERY_STARTED", "capturedAtUtc", Instant.now().toString());
        try {
            KubernetesClient client = client();
            nativeConfiguration = client.getConfiguration();
            var service = client.services().inNamespace(settings.namespace).withName(settings.service).get();
            if (service == null || service.getSpec() == null)
                throw new IllegalStateException("Service was not found in the explicitly selected namespace");
            Map<String, String> selector = service.getSpec().getSelector();
            if (selector == null || selector.isEmpty())
                throw new IllegalStateException("Service must have a pod selector; ExternalName/endpoints-only services are not supported");
            ServicePort servicePort = service.getSpec().getPorts().stream()
                    .filter(p -> p.getPort() != null && p.getPort() == settings.servicePort)
                    .findFirst().orElseThrow(() -> new IllegalStateException("Configured service port does not exist"));
            if (servicePort.getProtocol() != null && !"TCP".equals(servicePort.getProtocol()))
                throw new IllegalStateException("Only TCP service ports are supported");
            List<Pod> candidates = client.pods().inNamespace(settings.namespace).withLabels(selector).list().getItems();
            selectionEvidence = KubernetesPodDiagnostics.selection(settings.namespace, settings.service, selector, candidates);
            Pod pod = candidates.stream()
                    .filter(candidate -> KubernetesPodDiagnostics.rejectionReasons(candidate, selector).isEmpty())
                    .sorted(Comparator.comparing(p -> p.getMetadata().getName()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No Ready pod matches the service selector"));
            int targetPort = resolvePort(servicePort, pod);
            if (settings.logsEnabled && pod.getMetadata().getUid() != null) {
                int restarts = -1;
                if (pod.getStatus().getContainerStatuses() != null) {
                    for (var state : pod.getStatus().getContainerStatuses()) {
                        if (settings.logsContainer.equals(state.getName()) && state.getRestartCount() != null)
                            restarts = state.getRestartCount();
                    }
                }
                logTarget = new OcServiceLogs.Target(pod.getMetadata().getName(),
                        pod.getMetadata().getUid(), settings.logsContainer, targetPort, restarts);
            }
            List<String> images = pod.getSpec().getContainers().stream()
                    .map(c -> c.getImage()).filter(Objects::nonNull).toList();
            return new Target(settings.namespace, settings.service, pod.getMetadata().getName(),
                    settings.servicePort, targetPort, images, Objects.requireNonNull(pod.getMetadata().getUid()));
        } catch (Exception failure) {
            KubernetesDiagnosticException safe = (KubernetesDiagnosticException) safeFailure("service/pod discovery", failure);
            Map<String, Object> evidence = new java.util.LinkedHashMap<>(selectionEvidence);
            evidence.put("failure", safe.safeDetails());
            selectionEvidence = evidence;
            throw safe;
        }
    }

    public synchronized Map<String, Object> selectionDiagnostics() {
        return new java.util.LinkedHashMap<>(selectionEvidence);
    }

    public synchronized Map<String, Object> readinessDiagnostics() {
        return new java.util.LinkedHashMap<>(readinessEvidence);
    }

    /** Readiness reads only. Never retries a port-forward or a scheduler HTTP request. */
    public synchronized Target awaitDiagnosticReadyTarget() {
        KubernetesTunnelSettings settings = settings();
        long started = System.nanoTime();
        long budget = TimeUnit.SECONDS.toNanos(settings.diagnosticReadyTimeoutSeconds);
        List<Map<String, Object>> attempts = new ArrayList<>();
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("startedAtUtc", Instant.now().toString());
        report.put("timeoutSeconds", settings.diagnosticReadyTimeoutSeconds);
        report.put("pollMillis", settings.diagnosticReadyPollMillis);
        report.put("requiredConsecutiveSameUid", 2);
        report.put("apiRequestTimeoutMillis", settings.requestTimeoutMillis);
        report.put("deadlineScope", "POLL_LOOP; in-flight API reads retain their configured request timeout");
        report.put("mutationsDispatched", false);
        report.put("attempts", attempts);
        readinessEvidence = report;
        String previousUid = null;
        int consecutive = 0;
        try {
            while (System.nanoTime() - started < budget) {
                if (Thread.currentThread().isInterrupted())
                    throw new KubernetesDiagnosticException("READINESS_WAIT_INTERRUPTED", "SERVICE_POD_READINESS", null);
                Target observed = null;
                Map<String, Object> attempt = new java.util.LinkedHashMap<>();
                attempt.put("attempt", attempts.size() + 1);
                attempt.put("observedAtUtc", Instant.now().toString());
                try {
                    observed = target();
                    consecutive = Objects.equals(previousUid, observed.podUid()) ? consecutive + 1 : 1;
                    previousUid = observed.podUid();
                    attempt.put("status", "READY_SAMPLE");
                    attempt.put("pod", observed.pod()); attempt.put("podUid", observed.podUid());
                } catch (KubernetesDiagnosticException failure) {
                    attempt.putAll(failure.safeDetails()); attempt.put("status", failure.failureCode());
                    previousUid = null; consecutive = 0;
                    if (!"NO_READY_POD".equals(failure.failureCode())) {
                        attempts.add(attempt);
                        throw failure;
                    }
                }
                attempt.put("consecutiveReadySamples", consecutive);
                attempt.put("elapsedMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                attempt.put("selection", selectionDiagnostics()); attempts.add(attempt);
                System.out.println("[workload-readiness] attempt=" + attempts.size()
                        + "; elapsedMillis=" + attempt.get("elapsedMillis") + "; status=" + attempt.get("status")
                        + "; consecutiveReadySamples=" + consecutive);
                if (observed != null && consecutive >= 2 && System.nanoTime() - started < budget) {
                    report.put("status", "READY"); report.put("target", observed.description());
                    return observed;
                }
                long remaining = budget - (System.nanoTime() - started);
                if (remaining <= 0) break;
                try { TimeUnit.NANOSECONDS.sleep(Math.min(remaining,
                        TimeUnit.MILLISECONDS.toNanos(settings.diagnosticReadyPollMillis))); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new KubernetesDiagnosticException("READINESS_WAIT_INTERRUPTED", "SERVICE_POD_READINESS", interrupted);
                }
            }
            throw new KubernetesDiagnosticException("POD_READINESS_TIMEOUT", "SERVICE_POD_READINESS", null);
        } catch (RuntimeException failure) {
            report.put("status", "FAILED"); report.put("failure", KubernetesPodDiagnostics.unavailable(failure));
            throw failure;
        } finally {
            report.put("finishedAtUtc", Instant.now().toString());
            report.put("elapsedMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    /** Selected API transport. Native diagnostics still call openNative() explicitly. */
    public synchronized TunnelSession openConfigured() {
        return switch (settings().transport) {
            case "oc" -> openCli();
            case "fabric8" -> openNative();
            default -> throw new IllegalStateException("Unsupported configured Kubernetes transport");
        };
    }

    public synchronized TunnelSession openNative() { return openNative(settings().localPort); }

    public synchronized TunnelSession openNative(int localPort) {
        if (nativeSession != null && !nativeSession.isClosed()) {
            if (!nativeSession.isAlive()) throw new IllegalStateException("Native tunnel was lost; no automatic request replay is allowed");
            if (localPort != 0 && nativeSession.localPort() != localPort)
                throw new IllegalStateException("A different owned native listener is already active");
            return nativeSession;
        }
        requireFreePort(localPort);
        KubernetesTunnelSettings settings = settings();
        Target target = target();
        KubernetesClient client = client();
        AtomicBoolean abandoned = new AtomicBoolean();
        AtomicReference<LocalPortForward> opened = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "scheduler-fabric8-port-forward");
            thread.setDaemon(true);
            return thread;
        });
        Future<LocalPortForward> future = executor.submit(() -> {
            LocalPortForward forward = client.pods().inNamespace(target.namespace()).withName(target.pod())
                    .portForward(target.targetPort(), loopback(), localPort);
            opened.set(forward);
            if (abandoned.get()) {
                forward.close();
                throw new IllegalStateException("Native tunnel startup was cancelled");
            }
            return forward;
        });
        try {
            LocalPortForward forward = future.get(settings.startupSeconds, TimeUnit.SECONDS);
            if (!forward.isAlive()) throw new IllegalStateException("Native port-forward closed during startup");
            if (!awaitAcceptingConnections(forward.getLocalPort(), settings.startupSeconds))
                throw new IllegalStateException("Native port-forward listener did not accept a loopback connection");
            nativeSession = new TunnelSession("fabric8", forward.getLocalPort(), forward, forward::isAlive, target.description());
            registerShutdownHook();
            return nativeSession;
        } catch (Exception failure) {
            abandoned.set(true);
            future.cancel(true);
            LocalPortForward forward = opened.get();
            if (forward != null) try { forward.close(); } catch (Exception ignored) { }
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw safeFailure("native port-forward startup", failure);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Explicit CLI transport, also used as an independent control; never an automatic fallback. */
    public synchronized TunnelSession openCli() {
        if (cliSession != null && !cliSession.isClosed()) {
            if (!cliSession.isAlive()) throw new IllegalStateException("CLI tunnel was lost");
            return cliSession;
        }
        cliSession = startCli(settings(), true);
        return cliSession;
    }

    /** Explicit dependency tunnel, owned by the same registered framework service. */
    public synchronized TunnelSession openDictionaryCli() {
        if (dictionarySession != null && !dictionarySession.isClosed()) {
            if (!dictionarySession.isAlive()) throw new IllegalStateException("Dictionary CLI tunnel was lost");
            return dictionarySession;
        }
        dictionarySession = startCli(settings().dictionarySettings(), false);
        return dictionarySession;
    }

    /** User-service dependency tunnel, owned and closed by the same scenario service. */
    public synchronized TunnelSession openUserCli() {
        if (userSession != null && !userSession.isClosed()) {
            if (!userSession.isAlive()) throw new IllegalStateException("User-service CLI tunnel was lost");
            return userSession;
        }
        userSession = startCli(settings().userSettings(), false);
        return userSession;
    }

    private TunnelSession startCli(KubernetesTunnelSettings settings, boolean schedulerLogs) {
        requireFreePort(settings.localPort);
        settings.clientConfiguration(); // Explicit context/cluster/TLS guard, without constructing a KubernetesClient.
        OcServiceLogs.Target selected = null;
        if (schedulerLogs && settings.logsEnabled) {
            try {
                selected = OcServiceLogs.discover(settings, client());
                logTarget = selected;
            } catch (RuntimeException failure) {
                // Log-source discovery must not replace the original API test result.
                // Preserve the proven service/ port-forward, but do not attach another pod's logs.
                String reason = failure.getMessage();
                String classified = safeCauseCode(failure);
                logPreparationFailure = !"UNCLASSIFIED".equals(classified) ? classified
                        : reason != null && reason.matches("OC_[A-Z_]+")
                        ? reason : failure.getClass().getSimpleName();
            }
        }
        String resource = selected == null ? "service/" + settings.service : "pod/" + selected.pod();
        int forwardedPort = selected == null ? settings.servicePort : selected.port();
        List<String> arguments = List.of(settings.ocExecutable,
                "--kubeconfig", settings.kubeconfig().toString(),
                "--context", settings.context(), "-n", settings.namespace,
                "port-forward", "--address", "127.0.0.1",
                resource,
                (settings.localPort == 0 ? "" : Integer.toString(settings.localPort)) + ":" + forwardedPort);
        Process process;
        try { process = new ProcessBuilder(arguments).redirectErrorStream(true).start(); }
        catch (Exception failure) { throw safeFailure("oc executable startup; check kubernetes.oc.executable", failure); }
        pendingCliProcess = process;
        registerShutdownHook();
        CompletableFuture<Integer> listening = new CompletableFuture<>();
        // Attempt-local allowlisted code: never infer this from messages retained by an older tunnel.
        AtomicReference<String> startupFailureCode = new AtomicReference<>("UNCLASSIFIED");
        Thread reader = new Thread(() -> {
            try (BufferedReader output = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = output.readLine()) != null) {
                    String code = new OcReadOnlyCommand.Result(1, false, false, 0, line).failureCode();
                    if ("AUTH_REQUIRED".equals(code)) startupFailureCode.set(code);
                    else if ("RBAC_FORBIDDEN".equals(code)) startupFailureCode.compareAndSet("UNCLASSIFIED", code);
                    synchronized (cliMessages) {
                        if (cliMessages.size() == 80) cliMessages.removeFirst();
                        cliMessages.addLast(redact(line));
                    }
                    Matcher matcher = FORWARDING.matcher(line.trim());
                    if (matcher.matches()) listening.complete(Integer.parseInt(matcher.group(1)));
                }
                listening.completeExceptionally(new IllegalStateException("oc exited before its listener was ready"));
            } catch (Exception failure) {
                listening.completeExceptionally(new IllegalStateException("Cannot read oc readiness output"));
            }
        }, "scheduler-oc-port-forward-output");
        reader.setDaemon(true);
        reader.start();
        try {
            int port = listening.get(settings.startupSeconds, TimeUnit.SECONDS);
            if (!process.isAlive()) throw new IllegalStateException("oc exited after reporting readiness");
            TunnelSession session = new TunnelSession("oc", port, () -> stop(process), process::isAlive);
            pendingCliProcess = null;
            return session;
        } catch (Exception failure) {
            stop(process);
            pendingCliProcess = null;
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            String code = startupFailureCode.get();
            Exception classified = "UNCLASSIFIED".equals(code) ? failure
                    : new IllegalStateException("OC_PORT_FORWARD_" + code, failure);
            throw safeFailure("oc port-forward startup; see sanitized CLI output", classified);
        }
    }

    public String cliOutput() {
        synchronized (cliMessages) { return String.join("\n", cliMessages); }
    }

    public static boolean acceptsConnections(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean awaitAcceptingConnections(int port, int timeoutSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (acceptsConnections(port)) return true;
            try { Thread.sleep(50); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public static boolean awaitListenerClosed(int port, int timeoutSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (acceptsConnections(port)) {
            if (System.nanoTime() >= deadline) return false;
            try { Thread.sleep(50); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    public static void requireFreePort(int port) {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid local port");
        if (port == 0) return;
        try (ServerSocket guard = new ServerSocket()) {
            guard.setReuseAddress(false);
            guard.bind(new InetSocketAddress("127.0.0.1", port));
        } catch (Exception occupied) {
            throw new IllegalStateException("PORT_IN_USE: local port " + port + "; no existing listener was stopped");
        }
    }

    @Override
    public void afterTest() { close(); }

    @Override
    public synchronized void close() {
        List<RuntimeException> problems = new ArrayList<>();
        for (TunnelSession session : new TunnelSession[]{nativeSession, cliSession, dictionarySession, userSession}) {
            if (session == null) continue;
            try { session.close(); } catch (RuntimeException failure) { problems.add(failure); }
        }
        nativeSession = null;
        cliSession = null;
        dictionarySession = null;
        userSession = null;
        nativeConfiguration = null;
        logTarget = null;
        scenarioStarted = null;
        logPreparationFailure = null;
        if (pendingCliProcess != null) {
            try { stop(pendingCliProcess); } catch (RuntimeException failure) { problems.add(failure); }
            pendingCliProcess = null;
        }
        frameworkClient = null;
        if (shutdownHook != null) {
            if (Thread.currentThread() != shutdownHook) {
                try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                catch (IllegalStateException ignored) { }
            }
            shutdownHook = null;
        }
        if (!problems.isEmpty()) {
            IllegalStateException failure = new IllegalStateException("Owned infrastructure cleanup failed");
            problems.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private void registerShutdownHook() {
        if (shutdownHook != null) return;
        shutdownHook = new Thread(() -> {
            try { close(); } catch (RuntimeException ignored) { }
        }, "scheduler-owned-tunnel-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private static boolean ready(Pod pod) {
        return pod.getMetadata() != null && pod.getMetadata().getDeletionTimestamp() == null
                && pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())
                && pod.getStatus().getConditions() != null
                && pod.getStatus().getConditions().stream().anyMatch(c ->
                        "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }

    private static int resolvePort(ServicePort servicePort, Pod pod) {
        IntOrString target = servicePort.getTargetPort();
        if (target == null) return servicePort.getPort();
        if (target.getIntVal() != null) return target.getIntVal();
        List<Integer> ports = pod.getSpec().getContainers().stream()
                .filter(c -> c.getPorts() != null)
                .flatMap(c -> c.getPorts().stream())
                .filter(p -> Objects.equals(target.getStrVal(), p.getName()))
                .map(ContainerPort::getContainerPort).filter(Objects::nonNull).distinct().toList();
        if (ports.size() != 1) throw new IllegalStateException("Named targetPort is absent or ambiguous in the selected pod");
        return ports.get(0);
    }

    private static InetAddress loopback() {
        try { return InetAddress.getByName("127.0.0.1"); }
        catch (Exception impossible) { throw new IllegalStateException("IPv4 loopback is unavailable"); }
    }

    private static void stop(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(3, TimeUnit.SECONDS))
                    throw new IllegalStateException("Owned oc process did not terminate");
            }
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private IllegalStateException safeFailure(String stage, Exception failure) {
        return new KubernetesDiagnosticException(safeCauseCode(failure), stage, failure);
    }

    /** Report only allowlisted classifications, never arbitrary cause messages or kubeconfig data. */
    private String safeCauseCode(Throwable failure) {
        String kubernetesCode = ContainerServiceKubernetesAccess.failureCode(settings(), failure);
        if (!Set.of("CONTAINER_SERVICE_FAILURE", "TRANSPORT_UNCLASSIFIED").contains(kubernetesCode))
            return kubernetesCode;
        String classified = (String) ContainerDiagnosticEvidence.classify(failure).get("category");
        if (!"UNCLASSIFIED".equals(classified) && !"CONTRACT_ASSERTION".equals(classified))
            return classified;
        String code = "UNCLASSIFIED";
        Throwable cause = failure;
        for (int i = 0; cause != null && i < 8; i++, cause = cause.getCause()) {
            if (cause instanceof KubernetesClientException) {
                int status = ((KubernetesClientException) cause).getCode();
                if (status == 401) return "AUTH_REQUIRED";
                if (status == 403) return "RBAC_FORBIDDEN";
            }
            if ("OC_PORT_FORWARD_AUTH_REQUIRED".equals(cause.getMessage())) return "AUTH_REQUIRED";
            if ("OC_PORT_FORWARD_RBAC_FORBIDDEN".equals(cause.getMessage())) return "RBAC_FORBIDDEN";
            if (cause instanceof javax.net.ssl.SSLException) code = "JAVA_TLS_FAILURE";
            if (cause instanceof java.security.cert.CertPathBuilderException)
                code = "JAVA_PKIX_PATH_BUILDING_FAILED";
            if (cause instanceof java.security.InvalidAlgorithmParameterException) {
                String message = cause.getMessage();
                if (message != null && message.contains("trustAnchors") && message.contains("non-empty"))
                    return "JAVA_TRUST_ANCHORS_EMPTY";
                code = "JAVA_ALGORITHM_PARAMETERS";
            }
        }
        return code;
    }

    private static String redact(String line) {
        return line.replaceAll("(?i)(Bearer\\s+)[^\\s\\\"']+", "$1<redacted>")
                .replaceAll("sha256~[A-Za-z0-9_-]+", "<redacted-token>")
                .replaceAll("(?i)((?:token|password|client-key-data)\\s*[=:]\\s*)[^\\s]+", "$1<redacted>");
    }
}
