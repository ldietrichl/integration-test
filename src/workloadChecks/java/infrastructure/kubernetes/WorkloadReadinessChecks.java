package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.restassured.config.RestAssuredConfig;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorkloadReadinessChecks {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static void fails(Runnable task, String code) {
        try { task.run(); } catch (KubernetesDiagnosticException failure) {
            check(code.equals(failure.failureCode()), "exact failure code: " + code); return;
        }
        throw new AssertionError("Expected " + code);
    }
    private static ObjectNode json(String value) throws Exception {
        return (ObjectNode) WorkloadJson.JSON.readTree(value);
    }
    public static void main(String[] args) throws Exception {
        var service = json("{\"spec\":{\"selector\":{\"app\":\"app\"},\"ports\":[{\"port\":8080,\"targetPort\":9090}]}}");
        var pod = json("{\"metadata\":{\"uid\":\"new\",\"labels\":{\"app\":\"app\"}},\"spec\":{\"containers\":[{\"ports\":[{\"name\":\"http\",\"containerPort\":9090}]}]}}");
        var endpoints = json("{\"subsets\":[{\"ports\":[{\"port\":9090}],\"addresses\":[{\"targetRef\":{\"kind\":\"Pod\",\"uid\":\"new\"}}]}]}");
        check(ServiceEndpointReadiness.ready(service, endpoints, List.of(pod), 8080), "current endpoint is ready");
        check(!ServiceEndpointReadiness.ready(service, null, List.of(pod), 8080), "missing endpoints wait");
        ((ObjectNode) endpoints.at("/subsets/0/addresses/0/targetRef")).put("uid", "old");
        check(!ServiceEndpointReadiness.ready(service, endpoints, List.of(pod), 8080), "stale pod UID rejected");
        ((ObjectNode) endpoints.at("/subsets/0/addresses/0/targetRef")).put("uid", "new");
        ((ObjectNode) endpoints.at("/subsets/0/ports/0")).put("port", 8080);
        check(!ServiceEndpointReadiness.ready(service, endpoints, List.of(pod), 8080), "wrong target port rejected");
        ((ObjectNode) endpoints.at("/subsets/0/ports/0")).put("port", 9090);
        ((ObjectNode) service.at("/spec/ports/0")).put("targetPort", "http");
        check(ServiceEndpointReadiness.ready(service, endpoints, List.of(pod), 8080), "named target port resolved");
        ((ObjectNode) endpoints.at("/subsets/0")).putArray("notReadyAddresses").addObject();
        check(!ServiceEndpointReadiness.ready(service, endpoints, List.of(pod), 8080), "not ready endpoint rejected");
        ((ObjectNode) endpoints.at("/subsets/0")).remove("notReadyAddresses");
        ((ObjectNode) pod.at("/metadata/labels")).put("app", "other");
        fails(() -> ServiceEndpointReadiness.ready(service, endpoints, List.of(pod), 8080), "WORKLOAD_READINESS_SERVICE_SELECTOR");
        for (int status : new int[]{502, 503, 504}) check(!WorkloadHttpReadinessProbe.successfulSample(status), "transient status");
        for (int status : new int[]{301, 401, 403, 404, 500})
            fails(() -> WorkloadHttpReadinessProbe.successfulSample(status), "WORKLOAD_READINESS_HTTP_" + status);
        AtomicInteger calls = new AtomicInteger();
        WorkloadReadinessWait.await(() -> calls.incrementAndGet() != 2, 1, 10);
        check(calls.get() == 4, "transient failure resets successful sample count");
        fails(() -> WorkloadReadinessWait.await(() -> false, 1, 100), "WORKLOAD_READINESS_TIMEOUT");
        Thread.currentThread().interrupt();
        try {
            fails(() -> WorkloadReadinessWait.await(() -> true, 1, 10), "WORKLOAD_READINESS_INTERRUPTED");
            check(Thread.currentThread().isInterrupted(), "interrupt preserved");
        } finally { Thread.interrupted(); }
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger gets = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        server.createContext("/version", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) writes.incrementAndGet();
            int status = gets.incrementAndGet() <= 2 ? 503 : 200;
            exchange.sendResponseHeaders(status, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            var probe = new WorkloadHttpReadinessProbe("http://127.0.0.1:" + server.getAddress().getPort(),
                    "/version", new RestAssuredConfig());
            WorkloadReadinessWait.await(probe::ready, 10, 10);
            check(gets.get() == 4 && writes.get() == 0, "HTTP readiness retries GET only");
        } finally { server.stop(0); }
        check("BLOCKED_ENVIRONMENT".equals(infrastructure.scheduler.SchedulerOutcomeClassification.classify(
                "broken", "WORKLOAD_READINESS_TIMEOUT")), "readiness failure is environment blocked");
        check("ASSERTION_FAILURE".equals(infrastructure.scheduler.SchedulerOutcomeClassification.classify(
                "failed", "no MAIN contract")), "business failures remain assertions");
        System.out.println("Workload readiness checks passed: " + checks);
    }
}
