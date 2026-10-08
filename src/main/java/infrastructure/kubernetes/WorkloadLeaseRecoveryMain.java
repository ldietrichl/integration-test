package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import config.services.container.ContainerServiceKubernetesAccess;
import config.services.container.KubernetesTunnelConfigScope;
import config.services.container.KubernetesTunnelSettings;
import config.services.core.SecurePropertyResolver;
import config.services.core.StandSettings;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** CLI adapter for Gradle; uses exactly the common Fabric8 authentication and TLS settings. */
public final class WorkloadLeaseRecoveryMain {
    private WorkloadLeaseRecoveryMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !java.util.Set.of("inspect", "release", "check").contains(args[0]))
            throw new IllegalArgumentException("Expected inspect|release|check deployment configMap[,configMap] planPath confirmStopped");
        String mode = args[0], deployment = resource(args[1]);
        var maps = Arrays.stream(args[2].split(",", -1)).map(WorkloadLeaseRecoveryMain::resource).toList();
        Path planPath = Path.of(args[3]).toAbsolutePath().normalize();
        // Preserve the evidence across clean. Never place recovery files among uploadable raw results.
        if (planPath.startsWith(Path.of("build").toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Recovery plan must be outside build");
        boolean release = mode.equals("release");
        if (release && !"true".equals(args[4])) throw new IllegalStateException("RECOVERY_CONFIRM_ALL_RUNS_STOPPED_REQUIRED");
        if (mode.equals("inspect") && Files.exists(planPath)) throw new IllegalStateException("Plan already exists; use a new recoveryPlan path");
        JsonNode saved = release ? WorkloadJson.JSON.readTree(Files.readString(planPath)) : null;
        StandSettings stand = new StandSettings();
        var settings = KubernetesTunnelSettings.fromProperties(SecurePropertyResolver.resolve(new KubernetesTunnelConfigScope().getProperties()));
        if (release && (!stand.flag("mutations.enabled") || !stand.flag("mutations.configmap.enabled")
                || !stand.environment.equals(settings.environment)
                || !settings.namespace.equals(stand.required("mutations.namespace-confirmation"))))
            throw new IllegalStateException("RECOVERY_MUTATION_PERMISSION_REQUIRED: check stand mutation flags and namespace confirmation");
        try (var api = ContainerServiceKubernetesAccess.ownedNativeWorkloadClient(settings)) {
            ObjectNode scope = WorkloadJson.JSON.createObjectNode()
                    .put("environment", settings.environment).put("namespace", settings.namespace)
                    .put("context", settings.context()).put("deployment", deployment)
                    .put("apiServerSha256", WorkloadLeaseRecovery.hash(WorkloadJson.JSON.getNodeFactory().textNode(api.getMasterUrl().toString())));
            WorkloadLeaseRecovery recovery = new WorkloadLeaseRecovery(
                    () -> tree(api.apps().deployments().inNamespace(settings.namespace).withName(deployment).get()),
                    name -> tree(api.configMaps().inNamespace(settings.namespace).withName(name).get()),
                    (name, patch) -> api.configMaps().inNamespace(settings.namespace).withName(name)
                            .patch(PatchContext.of(PatchType.JSON), patch));
            if (mode.equals("check")) {
                JsonNode plan = recovery.inspect(scope, maps);
                java.util.List<String> busy = new java.util.ArrayList<>();
                for (JsonNode map : plan.path("configMaps")) if (map.has("owner"))
                    busy.add(map.path("name").asText() + " owner=" + map.path("owner").asText());
                if (!busy.isEmpty()) throw new IllegalStateException("WORKLOAD_RUN_BUSY: namespace=" + settings.namespace
                        + "; " + busy + "; inspect with explab2885InspectLease (override target for MAPPER); no automatic unlock");
                System.out.println("[workload-preflight] no current leases; deployment=" + deployment + "; atomic acquisition still required by test");
            } else if (release) {
                recovery.release(saved, scope, maps, true);
                System.out.println("[workload-recovery] completed; plan=" + planPath + "; original configuration NOT restored");
            } else {
                ObjectNode plan = recovery.inspect(scope, maps);
                Files.createDirectories(planPath.getParent());
                Files.writeString(planPath, plan.toPrettyString(), StandardOpenOption.CREATE_NEW);
                System.out.println(plan.toPrettyString());
                System.out.println("[workload-recovery] READ ONLY; plan=" + planPath
                        + "; verify all previous runs stopped before release; no automatic liveness detection");
            }
        } catch (io.fabric8.kubernetes.client.KubernetesClientException failure) {
            throw new IllegalStateException("RECOVERY_KUBERNETES_REQUEST_FAILED: HTTP " + failure.getCode()
                    + "; details suppressed; if release was attempted inspect again");
        }
    }

    private static JsonNode tree(Object object) {
        if (object == null) throw new IllegalStateException("RECOVERY_RESOURCE_NOT_FOUND");
        return WorkloadJson.JSON.valueToTree(object);
    }

    private static String resource(String name) {
        if (!name.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")) throw new IllegalArgumentException("Exact resource name required");
        return name;
    }
}
