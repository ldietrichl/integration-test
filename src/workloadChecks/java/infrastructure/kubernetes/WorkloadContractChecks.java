package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import config.services.container.ContainerServiceKubernetesAccess;
import config.services.container.KubernetesTunnelSettings;
import config.services.core.TestEnvironment;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Loopback API contract checks. No corporate endpoint, credentials or framework Environment. */
public final class WorkloadContractChecks {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static void fails(Runnable action, String label) {
        try { action.run(); } catch (RuntimeException | AssertionError expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static Properties connection() {
        Properties p = new Properties();
        String prefix = "kubernetes." + TestEnvironment.current() + ".";
        Map.of("namespace", "contract", "context", "contract", "api-server", "https://127.0.0.1:1",
                "container-service.ready", "true", "transport", "fabric8", "service", "app",
                "logs.container", "app", "logs.enabled", "false", "permissions.required", "get/list pods")
                .forEach((key, value) -> p.setProperty(prefix + key, value));
        return p;
    }
    private static void stand() {
        String p = "stand." + TestEnvironment.current() + ".";
        Map.of("mutations.enabled", "true", "mutations.configmap.enabled", "true",
                "mutations.restart.enabled", "true", "mutations.pod-delete.enabled", "true",
                "mutations.namespace-confirmation", "contract", "mutations.timeout.seconds", "10",
                "workloads.contract.deployment", "app", "workloads.contract.configmap", "rules")
                .forEach((key, value) -> System.setProperty(p + key, value));
    }
    private static final class Api implements AutoCloseable {
        final HttpServer server;
        final Map<String, ObjectNode> maps = new HashMap<>();
        final ObjectNode deployment;
        final ObjectNode replicaSet;
        ObjectNode pod;
        int revision = 1;
        int deletes;
        int dataWrites;
        String failDataMap;
        boolean failAfterApply;
        boolean unrelated;
        Api() throws Exception {
            maps.put("rules", json("{\"apiVersion\":\"v1\",\"kind\":\"ConfigMap\",\"metadata\":{\"name\":\"rules\",\"namespace\":\"contract\",\"uid\":\"cm-rules\",\"resourceVersion\":\"1\"},\"data\":{\"mode\":\"original\",\"keep\":\"unchanged\"}}"));
            maps.put("service", maps.get("rules").deepCopy());
            ((ObjectNode) maps.get("service").path("metadata")).put("name", "service").put("uid", "cm-service");
            deployment = json("{\"apiVersion\":\"apps/v1\",\"kind\":\"Deployment\",\"metadata\":{\"name\":\"app\",\"namespace\":\"contract\",\"uid\":\"dep\",\"resourceVersion\":\"1\",\"generation\":1},\"spec\":{\"replicas\":1,\"selector\":{\"matchLabels\":{\"app\":\"app\"}},\"template\":{\"metadata\":{\"labels\":{\"app\":\"app\"}},\"spec\":{\"containers\":[{\"name\":\"app\",\"image\":\"test\",\"envFrom\":[{\"configMapRef\":{\"name\":\"rules\"}},{\"configMapRef\":{\"name\":\"service\"}}]}]}}},\"status\":{\"observedGeneration\":1,\"replicas\":1,\"updatedReplicas\":1,\"readyReplicas\":1,\"availableReplicas\":1}}");
            replicaSet = json("{\"apiVersion\":\"apps/v1\",\"kind\":\"ReplicaSet\",\"metadata\":{\"name\":\"app-rs\",\"namespace\":\"contract\",\"uid\":\"rs\",\"ownerReferences\":[{\"kind\":\"Deployment\",\"uid\":\"dep\",\"name\":\"app\",\"controller\":true}]},\"spec\":{\"replicas\":1}}");
            pod = json("{\"apiVersion\":\"v1\",\"kind\":\"Pod\",\"metadata\":{\"name\":\"app-pod\",\"namespace\":\"contract\",\"uid\":\"pod-0\",\"labels\":{\"app\":\"app\"},\"ownerReferences\":[{\"kind\":\"ReplicaSet\",\"uid\":\"rs\",\"name\":\"app-rs\",\"controller\":true}]},\"spec\":{\"containers\":[{\"name\":\"app\",\"image\":\"test\"}]},\"status\":{\"phase\":\"Running\",\"conditions\":[{\"type\":\"Ready\",\"status\":\"True\"}],\"containerStatuses\":[{\"name\":\"app\",\"ready\":true}]}}");
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                int code = 200;
                JsonNode response;
                try {
                    String path = exchange.getRequestURI().getPath();
                    String method = exchange.getRequestMethod();
                    if (path.contains("/configmaps/")) {
                        String name = path.substring(path.lastIndexOf('/') + 1);
                        ObjectNode map = maps.get(name);
                        if ("PATCH".equals(method)) {
                            JsonNode patch = WorkloadJson.JSON.readTree(exchange.getRequestBody());
                            boolean data = false;
                            for (JsonNode op : patch) if (op.path("path").asText().startsWith("/data/")) data = true;
                            if (data && name.equals(failDataMap) && !failAfterApply) {
                                failDataMap = null;
                                throw new IllegalStateException("injected conflict");
                            }
                            ObjectNode next = map.deepCopy();
                            for (JsonNode op : patch) {
                                String pointer = op.path("path").asText();
                                if ("test".equals(op.path("op").asText())) {
                                    if (!next.at(pointer).equals(op.path("value"))) throw new IllegalStateException("CAS conflict");
                                } else {
                                    int slash = pointer.lastIndexOf('/');
                                    ObjectNode parent = (ObjectNode) next.at(pointer.substring(0, slash));
                                    String key = pointer.substring(slash + 1).replace("~1", "/").replace("~0", "~");
                                    if ("remove".equals(op.path("op").asText())) parent.remove(key);
                                    else parent.set(key, op.path("value").deepCopy());
                                }
                            }
                            ((ObjectNode)next.path("metadata")).put("resourceVersion", Integer.toString(++revision));
                            maps.put(name, next);
                            if (data) dataWrites++;
                            map = next;
                            if (data && name.equals(failDataMap) && failAfterApply) {
                                failDataMap = null;
                                throw new IllegalStateException("applied but response failed");
                            }
                        }
                        response = map;
                    } else if (path.endsWith("/deployments/app")) {
                        response = deployment.deepCopy();
                        if (unrelated) ((ObjectNode) response.at("/spec/template/spec/containers/0")).remove("envFrom");
                    } else if (path.endsWith("/services/app")) {
                        response = json("{\"apiVersion\":\"v1\",\"kind\":\"Service\",\"metadata\":{\"name\":\"app\"},\"spec\":{\"selector\":{\"app\":\"app\"},\"ports\":[{\"port\":8080,\"targetPort\":8080}]}}");
                    } else if (path.endsWith("/endpoints/app")) {
                        response = json("{\"apiVersion\":\"v1\",\"kind\":\"Endpoints\",\"metadata\":{\"name\":\"app\"},\"subsets\":[{\"ports\":[{\"port\":8080}],\"addresses\":[{\"ip\":\"127.0.0.1\",\"targetRef\":{\"kind\":\"Pod\",\"uid\":\"" + pod.at("/metadata/uid").asText() + "\"}}]}]}");
                    } else if (path.endsWith("/pods/app-pod")) {
                        if ("DELETE".equals(method)) {
                            deletes++;
                            ((ObjectNode)pod.path("metadata")).put("uid", "pod-" + deletes);
                            response = json("{\"apiVersion\":\"v1\",\"kind\":\"Status\",\"status\":\"Success\",\"code\":200}");
                        } else response = pod;
                    } else {
                        ObjectNode list = json("{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[]}");
                        if (path.endsWith("/replicasets")) {
                            list.put("apiVersion", "apps/v1").put("kind", "ReplicaSetList");
                            list.withArray("items").add(replicaSet);
                        } else if (path.endsWith("/pods")) {
                            list.put("kind", "PodList"); list.withArray("items").add(pod);
                        }
                        response = list;
                    }
                } catch (Exception failure) {
                    code = 409;
                    response = json("{\"apiVersion\":\"v1\",\"kind\":\"Status\",\"status\":\"Failure\",\"reason\":\"Conflict\",\"code\":409}");
                }
                byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, bytes.length);
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.start();
        }
        KubernetesWorkloadControl session() {
            var client = new KubernetesClientBuilder().withConfig(new ConfigBuilder()
                    .withMasterUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .withNamespace("contract").withHttpProxy(null).withHttpsProxy(null)
                    .withOauthToken(null).withUsername(null).withPassword(null)
                    .withRequestTimeout(2000).withRequestRetryBackoffLimit(0).build()).build();
            var control = new KubernetesWorkloadControl(KubernetesTunnelSettings.fromProperties(connection()), "contract", () -> client);
            control.disableAllureAttachments();
            return control;
        }
        String value(String name) { return maps.get(name).at("/data/mode").asText(); }
        @Override public void close() { server.stop(0); }
    }
    private static ObjectNode json(String text) {
        try { return (ObjectNode) WorkloadJson.JSON.readTree(text); }
        catch (Exception failure) { throw new IllegalArgumentException(failure); }
    }
    public static void main(String[] args) throws Exception {
        isolateArtifacts();
        stand();
        WorkloadReadinessChecks.main(new String[0]);
        String readinessPrefix = "stand." + TestEnvironment.current() + ".workloads.contract.readiness.";
        System.setProperty(readinessPrefix + "timeout.seconds", "10");
        System.setProperty(readinessPrefix + "poll.millis", "10");
        try (Api api = new Api(); var control = api.session()) {
            int[] calls = {0};
            control.availabilityProbe(() -> {
                calls[0]++;
                if (api.deletes == 2) {
                    check("original".equals(api.value("rules")), "HTTP restoration check sees original config");
                    check(!api.maps.get("rules").at("/metadata/annotations").isMissingNode(), "HTTP restoration check precedes lease release");
                }
                return true;
            });
            control.awaitAvailability();
            check(api.dataWrites == 0 && api.deletes == 0, "availability preflight performs no mutations");
            control.applyState(List.of(new ConfigMapState("rules", Map.of("mode", "test"))));
            control.restart();
            control.awaitAvailability();
            control.close();
            check(calls[0] == 6 && control.isRestored(), "two samples before, after restart and after restore");
        }
        System.setProperty(readinessPrefix + "timeout.seconds", "1");
        try (Api api = new Api(); var control = api.session()) {
            control.availabilityProbe(() -> false);
            fails(control::awaitAvailability, "unavailable ingress blocks preflight");
            check(api.dataWrites == 0 && api.deletes == 0, "unavailable ingress cannot mutate baseline");
        }
        try (Api api = new Api()) {
            var control = api.session();
            control.availabilityProbe(() -> false);
            control.applyState(List.of(new ConfigMapState("rules", Map.of("mode", "test"))));
            control.restart();
            fails(control::close, "restoration HTTP timeout surfaces");
            check("original".equals(api.value("rules")) && !control.isRestored(), "config restored but availability remains unconfirmed");
            check(!api.maps.get("rules").at("/metadata/annotations").isMissingNode(), "failed restoration retains lease for recovery");
        }
        check(!io.perfeccionista.framework.Environment.existForCurrentThread(), "checks must not create Environment");
        var kubeconfig = Files.createTempFile("workload-contract-", ".yaml");
        try {
            String testCa;
            try (var stream = WorkloadContractChecks.class.getClassLoader()
                    .getResourceAsStream("kubernetes/contract-ca.pem")) {
                check(stream != null, "offline contract CA resource is available");
                testCa = Base64.getEncoder().encodeToString(stream.readAllBytes());
            }
            Files.writeString(kubeconfig, """
                    apiVersion: v1
                    kind: Config
                    current-context: contract
                    clusters:
                    - name: contract
                      cluster:
                        server: https://127.0.0.1:1
                        certificate-authority-data: %s
                    contexts:
                    - name: contract
                      context:
                        cluster: contract
                        namespace: contract
                        user: contract
                    users:
                    - name: contract
                      user:
                        token: offline-test-only
                    """.formatted(testCa));
            Properties nativeProperties = connection();
            nativeProperties.setProperty("kubernetes." + TestEnvironment.current() + ".kubeconfig", kubeconfig.toString());
            try (var client = ContainerServiceKubernetesAccess.ownedNativeWorkloadClient(
                    KubernetesTunnelSettings.fromProperties(nativeProperties))) {
                check("contract".equals(client.getNamespace()), "production client factory works before Environment init");
                check(!client.getConfiguration().isTrustCerts(), "native client keeps TLS validation");
                check(!client.getConfiguration().isDisableHostnameVerification(), "native client keeps hostname validation");
                check(testCa.equals(client.getConfiguration().getCaCertData()), "native client uses explicit offline CA");
            }
        } finally { Files.deleteIfExists(kubeconfig); }
        fails(() -> new KubernetesWorkloadService().open(new WorkloadTarget("contract", "app", 8080, "app")),
                "framework service requires initialized Environment");
        var invalid = new HashMap<String, String>(); invalid.put("mode", "ok"); invalid.put("token", "forbidden");
        fails(() -> new ConfigMapState("rules", invalid), "validate all keys before opening a session");
        var values = new HashMap<>(Map.of("mode", "before"));
        ConfigMapState immutable = new ConfigMapState("rules", values);
        values.put("mode", "after");
        check("before".equals(immutable.data().get("mode")), "state is immutable");
        fails(() -> new WorkloadTarget("../other", "app", 8080, "app"), "invalid profile rejected");
        var references = json("{\"spec\":{\"template\":{\"spec\":{\"initContainers\":[{\"envFrom\":[{\"configMapRef\":{\"name\":\"init\"}}],\"volumeMounts\":[{\"name\":\"mounted\"}]}],\"volumes\":[{\"name\":\"mounted\",\"projected\":{\"sources\":[{\"configMap\":{\"name\":\"projected\"}}]}},{\"name\":\"unused\",\"configMap\":{\"name\":\"wrong\"}}]}}}}");
        check(ConfigMapReferences.names(references).equals(Set.of("init", "projected")), "only consumed projected/init ConfigMaps accepted");
        check(!KubernetesTunnelSettings.fromProperties(connection()).requiredPermissions.contains("create pods/portforward"), "native checks do not need portforward");
        try (Api api = new Api(); var control = api.session()) {
            control.requireStableReadyWorkload("offline");
            check(api.dataWrites == 0 && api.deletes == 0, "readiness is read-only");
            control.applyState(List.of(new ConfigMapState("rules", Map.of("mode", "test", "added", "temporary"))));
            check("test".equals(api.value("rules")), "native CAS patch confirmed");
            control.restart();
            check(api.deletes == 1, "one replacement applies the state");
            control.close();
            check(control.isRestored() && "original".equals(api.value("rules")), "baseline restored without Environment");
            check(api.maps.get("rules").at("/data/added").isMissingNode(), "new key removed during rollback");
            check("unchanged".equals(api.maps.get("rules").at("/data/keep").asText()), "unrelated value preserved");
            check(api.deletes == 2, "restored configuration applied to replacement pod");
            check(api.maps.get("rules").at("/metadata/annotations").isMissingNode(), "lease released");
            int writes = api.dataWrites; control.close();
            check(api.dataWrites == writes && api.deletes == 2, "close is idempotent");
        }
        try (Api api = new Api(); var control = api.session()) {
            api.unrelated = true;
            fails(() -> control.applyState(List.of(new ConfigMapState("rules", Map.of("mode", "test")))), "unreferenced ConfigMap rejected");
            check(api.dataWrites == 0 && api.deletes == 0, "wrong ConfigMap causes no mutations");
        }
        for (boolean afterApply : new boolean[]{false, true}) try (Api api = new Api(); var control = api.session()) {
            api.failDataMap = "service"; api.failAfterApply = afterApply;
            fails(() -> control.applyState(List.of(new ConfigMapState("rules", Map.of("mode", "test")),
                    new ConfigMapState("service", Map.of("mode", "test")))), "partial apply failure surfaced");
            control.close();
            check("original".equals(api.value("rules")) && "original".equals(api.value("service")), "partial/ambiguous apply restored");
            check(api.deletes == 0, "failure before restart does not delete a pod");
        }
        try (Api api = new Api(); var first = api.session(); var second = api.session()) {
            first.applyState(List.of(new ConfigMapState("rules", Map.of("mode", "test"))));
            fails(() -> second.applyState(List.of(new ConfigMapState("service", Map.of("mode", "other")))),
                    "different ConfigMaps on the same workload still serialize");
            second.close();
            check("original".equals(api.value("service")), "competing run did not write data");
            check(!api.maps.get("rules").at("/metadata/annotations").isMissingNode(), "competing close did not release owner");
            first.close();
        }
        try (Api api = new Api()) {
            var control = api.session();
            control.applyState(List.of(new ConfigMapState("rules", Map.of("mode", "test"))));
            ((ObjectNode)api.maps.get("rules").path("data")).put("mode", "external");
            fails(control::close, "external change blocks rollback");
            check("external".equals(api.value("rules")), "external change preserved");
            check(!control.isRestored(), "unconfirmed rollback is not successful");
        }
        check(!io.perfeccionista.framework.Environment.existForCurrentThread(), "native lifecycle stayed independent");
        try (Api api = new Api()) {
            var control = api.session();
            control.applyState(List.of(new ConfigMapState("rules", Map.of("mode", "test"))));
            ((ObjectNode)api.maps.get("rules").at("/metadata/annotations")).put(WorkloadRunLease.KEY, "new-owner");
            fails(control::close, "lost lease blocks rollback before any write");
            check("test".equals(api.value("rules")) && !control.isRestored(), "lost lease preserves current state for recovery");
        }
        System.out.println("Workload contract checks passed: " + checks);
        runScopeChecks();
        ru.sber.qa.splitter.EXPLAB_2690.Explab2690OracleChecks.main(new String[0]);
        scheduler.localchecks.SchedulerIsolationChecks.main(new String[0]);
    }

    private static void isolateArtifacts() {
        var roots = Map.of(
                "allure.results.directory", java.nio.file.Path.of("build", "contract-checks", "allure-results"),
                "stand.artifacts.directory", java.nio.file.Path.of(".workload-recovery", "private", "contract-checks"));
        var targets = new HashMap<String, java.nio.file.Path>();
        for (var entry : roots.entrySet()) {
            var root = entry.getValue().toAbsolutePath().normalize();
            var target = isolatedPath(System.getProperty(entry.getKey(), root.toString()), root);
            targets.put(entry.getKey(), target);
            check(isolatedPath(root.resolve("nested").toString(), root).startsWith(root), "nested contract directory allowed");
            fails(() -> isolatedPath(root.getParent().toString(), root), "parent artifact directory rejected");
            fails(() -> isolatedPath(root + "-other", root), "sibling artifact directory rejected");
            fails(() -> isolatedPath(root.resolve("../escaped").toString(), root), "artifact traversal rejected");
        }
        fails(() -> isolatedPath(roots.get("allure.results.directory").toString(),
                roots.get("stand.artifacts.directory").toAbsolutePath().normalize()), "private journal under build rejected");
        fails(() -> isolatedPath("build/allure-results",
                roots.get("allure.results.directory").toAbsolutePath().normalize()), "business Allure directory rejected");
        targets.forEach((property, target) -> System.setProperty(property, target.toString()));
        System.out.println("Contract Allure isolated under build/contract-checks/allure-results; private journal under .workload-recovery/private/contract-checks.");
    }

    private static java.nio.file.Path isolatedPath(String value, java.nio.file.Path root) {
        var target = java.nio.file.Path.of(value).toAbsolutePath().normalize();
        check(target.startsWith(root), "contract artifacts must stay in their dedicated Allure/private directory");
        return target;
    }

    private static Api runApi;
    public abstract static class RunFixture {
        @org.junit.jupiter.api.BeforeEach void prepare() {
            var steps = new steps.container.KubernetesWorkloadSteps(target -> runApi.session());
            config.extensions.WorkloadRunScopeExtension.ensure(new WorkloadTarget("contract", "app", 8080, "app"),
                    List.of(new ConfigMapState("rules", Map.of("mode", "test"))), steps, null);
        }
    }
    @org.junit.jupiter.api.extension.ExtendWith(config.extensions.WorkloadRunScopeExtension.class)
    @org.junit.jupiter.api.Order(1)
    public static class RunFirst extends RunFixture {
        @org.junit.jupiter.api.Test void businessFailure() { throw new AssertionError("intentional business failure"); }
    }
    @org.junit.jupiter.api.extension.ExtendWith(config.extensions.WorkloadRunScopeExtension.class)
    @org.junit.jupiter.api.Order(2)
    public static class RunSecond extends RunFixture {
        @org.junit.jupiter.api.Test void nextClass() {
            check(runApi.deletes == 1 && "test".equals(runApi.value("rules")), "same session survives another class and a business failure");
        }
    }
    @org.junit.jupiter.api.extension.ExtendWith(config.extensions.WorkloadRunScopeExtension.class)
    public static class RunRestoreConflict extends RunFixture {
        @org.junit.jupiter.api.Test void externalChange() {
            ((ObjectNode) runApi.maps.get("rules").path("data")).put("mode", "external");
        }
    }
    private static void runScopeChecks() throws Exception {
        try (Api api = new Api()) {
            runApi = api;
            var request = org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                    .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(RunFirst.class),
                            org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(RunSecond.class))
                    .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                    .configurationParameter("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer$OrderAnnotation").build();
            var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
            org.junit.platform.launcher.core.LauncherFactory.create().execute(request, listener);
            var summary = listener.getSummary();
            check(summary.getTestsFoundCount() == 2 && summary.getTestsFailedCount() == 1
                    && summary.getTestsSucceededCount() == 1, "root fixture executes both classes including failure");
            check(summary.getContainersFailedCount() == 0, "root cleanup succeeds");
            check(api.deletes == 2 && "original".equals(api.value("rules")), "only two pod replacements across classes");
            check(api.maps.get("rules").at("/metadata/annotations").isMissingNode(), "root released its lease");
        } finally { runApi = null; }
        try (Api api = new Api()) {
            runApi = api;
            var request = org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                    .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(RunRestoreConflict.class)).build();
            var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
            org.junit.platform.launcher.core.LauncherFactory.create().execute(request, listener);
            check(listener.getSummary().getTestsSucceededCount() == 1 && listener.getSummary().getContainersFailedCount() > 0,
                    "root restoration failure makes the JUnit run fail after successful business tests");
            check("external".equals(api.value("rules")) && !api.maps.get("rules").at("/metadata/annotations").isMissingNode(),
                    "root restoration conflict preserves external value and recovery lease");
        } finally { runApi = null; }
        try (Api api = new Api(); var control = api.session()) {
            var steps = new steps.container.KubernetesWorkloadSteps(new KubernetesWorkloadService());
            var original = List.of(new ConfigMapState("rules", Map.of("mode", "original")));
            control.applyState(original);
            steps.ensureState(control, original);
            check(control.configurationWrites() == 0 && api.deletes == 0, "identical state never restarts");
            steps.ensureState(control, List.of(new ConfigMapState("rules", Map.of("mode", "second"))));
            check(api.deletes == 1, "changed mode restarts once");
            steps.ensureState(control, original);
            check(api.deletes == 2, "return to original mode restarts once");
            control.close();
            check(api.deletes == 2 && control.isRestored(), "no redundant restart when final mode already equals baseline");
        }
        try (Api api = new Api(); var control = api.session()) {
            ((ObjectNode) api.maps.get("rules").path("data")).put("SPLITTER_ALLOW_RESULT_WITHOUT_MAIN", "false");
            var resolved = control.environmentState(Map.of("SPLITTER_ALLOW_RESULT_WITHOUT_MAIN", "true"));
            check(resolved.size() == 1 && resolved.get(0).name().equals("rules"), "actual envFrom map selected, not hardcoded service/lib");
            fails(() -> control.environmentState(Map.of("MISSING_SETTING", "true")), "unbound settings cannot be invented");
            check(api.dataWrites == 0, "binding resolution is read-only");
            ((ObjectNode) api.maps.get("service").path("data")).put("SPLITTER_ALLOW_RESULT_WITHOUT_MAIN", "false");
            fails(() -> control.environmentState(Map.of("SPLITTER_ALLOW_RESULT_WITHOUT_MAIN", "true")), "ambiguous envFrom binding fails before writes");
            var container = (ObjectNode) api.deployment.at("/spec/template/spec/containers/0");
            container.putArray("env").addObject().put("name", "SPLITTER_ALLOW_RESULT_WITHOUT_MAIN")
                    .putObject("valueFrom").putObject("configMapKeyRef").put("name", "service").put("key", "mode");
            var direct = control.environmentState(Map.of("SPLITTER_ALLOW_RESULT_WITHOUT_MAIN", "true"));
            check(direct.get(0).name().equals("service") && direct.get(0).data().containsKey("mode"), "explicit env keyRef takes priority over envFrom");
            container.putArray("env").addObject().put("name", "SPLITTER_ALLOW_RESULT_WITHOUT_MAIN").put("value", "false");
            fails(() -> control.environmentState(Map.of("SPLITTER_ALLOW_RESULT_WITHOUT_MAIN", "true")), "literal Deployment env cannot be silently shadowed");
        }
        check(ConfigMapState.equivalent("rules.yml", "# comment\nflag: true\n", "flag: true\n"), "YAML comments do not require restart");
        check(!ConfigMapState.equivalent("rules.yml", "flag: true\n", "flag: false\n"), "YAML flag changes are real changes");
        System.out.println("Run-scope and transition checks passed; cumulative lifecycle checks: " + checks);
    }
}
