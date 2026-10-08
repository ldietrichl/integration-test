package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import config.services.container.ContainerDiagnosticsSettings;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.qameta.allure.Allure;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Only allowlisted metadata enters the log and Allure. Raw resources stay out of reports. */
public final class ContainerDiagnosticEvidence {
    private static final Logger LOG = LoggerFactory.getLogger(ContainerDiagnosticEvidence.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ThreadLocal<Run> RUN = ThreadLocal.withInitial(Run::new);
    private static final ThreadLocal<Map<String, Object>> CASE = new ThreadLocal<>();

    private static final class Run {
        final String id = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        final List<Map<String, Object>> cases = new ArrayList<>();
        Path directory;
    }

    private ContainerDiagnosticEvidence() { }
    @FunctionalInterface public interface Action { void run() throws Exception; }

    public static void execute(String id, Action action) {
        Map<String, Object> record = new LinkedHashMap<>();
        CASE.set(record);
        long start = System.nanoTime();
        record.put("case", id);
        record.put("started", Instant.now().toString());
        try {
            ContainerDiagnosticsSettings settings = ContainerDiagnosticsSettings.local();
            record.put("environment", settings.environment);
            record.put("phase", settings.phase());
            stage("START");
            action.run();
            record.put("status", "PASSED");
        } catch (TestAbortedException skipped) {
            record.put("status", "SKIPPED");
            throw skipped;
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            Map<String, Object> diagnostic = classify(failure);
            boolean contractFailure = "CONTRACT_ASSERTION".equals(diagnostic.get("category"));
            record.put("status", contractFailure ? "FAILED" : "BROKEN");
            record.put("failure", diagnostic);
            // Keep business assertions failed; infrastructure errors remain broken, not green/skipped.
            // Do not propagate native messages/causes into Gradle XML or Allure.
            String message = id + " failed at " + record.get("stage")
                    + "; classification=" + diagnostic.get("category")
                    + "; HTTP=" + diagnostic.get("httpCodes") + "; see sanitized diagnostic attachment";
            if (contractFailure) throw new AssertionError(message);
            throw new IllegalStateException(message);
        } finally {
            record.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            record.put("finished", Instant.now().toString());
            RUN.get().cases.add(new LinkedHashMap<>(record));
            String json = encode(record);
            LOG.info("[CONTAINER-DIAGNOSTIC] {}", json);
            Allure.addAttachment(id + " diagnostic", "application/json", json, ".json");
            try {
                Files.writeString(directory().resolve(id + ".json"), json, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
            } catch (Exception failure) {
                LOG.error("[CONTAINER-DIAGNOSTIC] artifact write failed: {}", failure.getClass().getSimpleName());
            }
            CASE.remove();
        }
    }

    public static void stage(String stage) {
        detail("stage", stage);
        LOG.info("[CONTAINER-DIAGNOSTIC] case={} stage={}", CASE.get() == null ? "-" : CASE.get().get("case"), stage);
    }

    public static void detail(String key, Object safeValue) {
        if (CASE.get() != null) CASE.get().put(key, safeValue);
    }

    public static Map<String, Object> classify(Throwable failure) {
        List<Integer> codes = new ArrayList<>();
        List<String> classes = new ArrayList<>();
        List<String> failureCodes = new ArrayList<>();
        List<String> operations = new ArrayList<>();
        StringBuilder hints = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Throwable> pending = new ArrayList<>();
        pending.add(failure);
        for (int index = 0; index < pending.size() && index < 24; index++) {
            Throwable current = pending.get(index);
            if (current == null || !seen.add(current)) continue;
            classes.add(current.getClass().getSimpleName());
            if (current instanceof KubernetesClientException)
                codes.add(((KubernetesClientException) current).getCode());
            if (current instanceof KubernetesDiagnosticException safe) {
                if (safe.httpStatus() > 0 && !codes.contains(safe.httpStatus())) codes.add(safe.httpStatus());
                failureCodes.add(safe.failureCode());
                Map<String, Object> facts = safe.safeDetails();
                operations.add((String) facts.get("operation"));
                for (Object type : (List<?>) facts.get("exceptionTypes"))
                    if (!classes.contains((String) type)) classes.add((String) type);
            }
            if (current.getMessage() != null) hints.append(current.getMessage().toLowerCase(java.util.Locale.ROOT));
            pending.add(current.getCause());
            Collections.addAll(pending, current.getSuppressed());
        }
        String text = hints.toString();
        String category =
                codes.contains(401) || text.contains("auth_required") ? "AUTH_REQUIRED" :
                codes.contains(403) || text.contains("rbac_forbidden") ? "RBAC_FORBIDDEN" :
                text.contains("oc_timeout") ? "TIMEOUT" :
                codes.contains(407) ? "PROXY_AUTH_REQUIRED" :
                codes.contains(404) ? "RESOURCE_NOT_FOUND" :
                text.contains("cascade_from_") ? "CASCADE_BLOCKED" :
                text.contains("ingress_upstream_unavailable") ? "INGRESS_UPSTREAM_UNAVAILABLE" :
                text.contains("no_ready_backend") ? "NO_READY_BACKEND" :
                text.contains("quota_insufficient") ? "QUOTA_INSUFFICIENT" :
                failureCodes.contains("SCHEDULER_REQUEST_CONTRACT")
                        || classes.contains("RequestContractException") ? "LOCAL_REQUEST_CONTRACT" :
                failureCodes.contains("POD_READINESS_TIMEOUT") ? "POD_READINESS_TIMEOUT" :
                failureCodes.contains("READINESS_WAIT_INTERRUPTED") ? "READINESS_WAIT_INTERRUPTED" :
                text.contains("imagepullbackoff") || text.contains("errimagepull")
                        || text.contains("image_pull_failure") ? "IMAGE_PULL_FAILURE" :
                text.contains("no_ready_pod") || text.contains("no ready pod")
                        || text.contains("minimumreplicasunavailable")
                        || text.contains("pod_recreation_timeout") ? "NO_READY_POD" :
                text.contains("trustanchors") || text.contains("java_trust_anchors_empty") ? "JAVA_TRUST_ANCHORS_EMPTY" :
                text.contains("pkix") || text.contains("unable to find valid certification") ? "PKI_CHAIN_UNTRUSTED" :
                text.contains("subject alternative") || text.contains("hostname") ? "TLS_HOSTNAME" :
                classes.stream().anyMatch(c -> c.contains("SSL")) ? "TLS_HANDSHAKE" :
                classes.contains("UnknownHostException") ? "DNS" :
                classes.stream().anyMatch(c -> c.contains("Timeout")) || text.contains("timed out") ? "TIMEOUT" :
                classes.contains("ConnectException") ? "TCP_CONNECT" :
                operations.contains("LOCAL_CONFIGURATION") ? "LOCAL_CONFIGURATION" :
                text.contains("kubeconfig_") || text.contains("diagnostic_config") ? "LOCAL_CONFIGURATION" :
                failure instanceof AssertionError ? "CONTRACT_ASSERTION" : "UNCLASSIFIED";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("httpCodes", codes);
        result.put("exceptionClasses", classes);
        result.put("failureCodes", failureCodes);
        result.put("operations", operations);
        if (codes.contains(401)) result.put("authenticationRejectionReason", "NOT_PROVEN");
        return result;
    }

    public static Path directory() throws Exception {
        Run run = RUN.get();
        if (run.directory == null) {
            ContainerDiagnosticsSettings s = ContainerDiagnosticsSettings.local();
            run.directory = Path.of("build", "diagnostics", "container-package", s.environment, s.phase(), run.id)
                    .toAbsolutePath().normalize();
            Files.createDirectories(run.directory);
        }
        return run.directory;
    }

    /** Full ConfigMap snapshots are private recovery artifacts, never Allure attachments. */
    public static Path privateSnapshot(String name, String json) throws Exception {
        Path folder = Files.createTempDirectory(directory(), "private-recovery-");
        AclFileAttributeView acl = Files.getFileAttributeView(folder, AclFileAttributeView.class);
        if (acl != null) {
            AclEntry owner = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(Set.of(AclEntryPermission.values()))
                    .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build();
            acl.setAcl(List.of(owner));
        } else if (Files.getFileAttributeView(folder, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"));
        } else {
            throw new IllegalStateException("PRIVATE_BACKUP_ACL_UNAVAILABLE: no mutation was authorized");
        }
        Path result = folder.resolve(name + ".json");
        Files.writeString(result, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        LOG.info("[CONTAINER-DIAGNOSTIC] private recovery artifact: {}", result);
        detail("privateRecoveryFile", result.toString());
        return result;
    }

    public static void finish() {
        try {
            Files.writeString(directory().resolve("summary.json"), encode(RUN.get().cases),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (Exception failure) {
            LOG.error("[CONTAINER-DIAGNOSTIC] summary write failed: {}", failure.getClass().getSimpleName());
        } finally { RUN.remove(); CASE.remove(); }
    }

    private static String encode(Object value) {
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception failure) { return "{\"status\":\"SERIALIZATION_FAILED\"}"; }
    }
}
