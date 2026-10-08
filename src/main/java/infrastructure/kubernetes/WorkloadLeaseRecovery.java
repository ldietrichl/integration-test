package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Explicit orphan recovery. Does not infer liveness, steal leases, restore data or restart pods. */
public final class WorkloadLeaseRecovery {
    private static final String OWNER = "/metadata/annotations/scheduler-regression.explab~1run-owner";
    private final Supplier<JsonNode> deployment;
    private final Function<String, JsonNode> configMap;
    private final BiConsumer<String, String> patch;

    public WorkloadLeaseRecovery(Supplier<JsonNode> deployment, Function<String, JsonNode> configMap,
                                 BiConsumer<String, String> patch) {
        this.deployment = deployment;
        this.configMap = configMap;
        this.patch = patch;
    }

    /** No writes. Plan contains identities and hashes only, never ConfigMap values. */
    public ObjectNode inspect(JsonNode scope, List<String> names) {
        if (names.isEmpty() || names.size() != new TreeSet<>(names).size())
            throw new IllegalArgumentException("RECOVERY_EXACT_UNIQUE_CONFIGMAPS_REQUIRED");
        JsonNode dep = deployment.get();
        String uid = required(dep, "/metadata/uid");
        var references = ConfigMapReferences.names(dep);
        ObjectNode plan = WorkloadJson.JSON.createObjectNode();
        plan.put("schema", 1);
        plan.set("scope", scope.deepCopy());
        plan.put("deploymentUid", uid);
        plan.put("deploymentSpecSha256", hash(dep.path("spec")));
        ArrayNode maps = plan.putArray("configMaps");
        for (String name : new TreeSet<>(names)) {
            if (!references.contains(name)) throw new IllegalStateException("RECOVERY_CONFIGMAP_NOT_CONSUMED: " + name);
            JsonNode cm = configMap.apply(name);
            ObjectNode entry = maps.addObject().put("name", name);
            entry.put("uid", required(cm, "/metadata/uid"));
            entry.put("resourceVersion", required(cm, "/metadata/resourceVersion"));
            entry.put("contentSha256", contentHash(cm));
            if (!cm.at(OWNER).isMissingNode()) entry.put("owner", required(cm, OWNER));
        }
        return plan;
    }

    public void release(JsonNode plan, JsonNode scope, List<String> names, boolean confirmedStopped) {
        if (!confirmedStopped) throw new IllegalStateException("RECOVERY_CONFIRM_ALL_RUNS_STOPPED_REQUIRED");
        // Preflight every target before touching any lock. Kubernetes has no multi-object transaction.
        if (!plan.equals(inspect(scope, names))) throw new IllegalStateException("RECOVERY_PLAN_CHANGED: inspect again; no locks removed");
        for (JsonNode entry : plan.path("configMaps")) {
            if (!entry.has("owner")) continue;
            String name = entry.path("name").asText();
            JsonNode dep = deployment.get();
            if (!plan.path("deploymentUid").asText().equals(required(dep, "/metadata/uid"))
                    || !plan.path("deploymentSpecSha256").asText().equals(hash(dep.path("spec"))))
                throw new IllegalStateException("RECOVERY_DEPLOYMENT_CHANGED: remaining locks preserved");
            ArrayNode ops = WorkloadJson.JSON.createArrayNode();
            test(ops, "/metadata/uid", entry.path("uid"));
            test(ops, "/metadata/resourceVersion", entry.path("resourceVersion"));
            test(ops, OWNER, entry.path("owner"));
            ops.addObject().put("op", "remove").put("path", OWNER);
            try {
                patch.accept(name, ops.toString());
            } catch (RuntimeException failure) {
                // Never blindly retry an ambiguous PATCH or disclose response bodies with config data.
                throw new IllegalStateException("RECOVERY_PATCH_UNCONFIRMED: " + name + "; inspect again before any retry");
            }
            JsonNode after = configMap.apply(name);
            if (!entry.path("uid").asText().equals(required(after, "/metadata/uid"))
                    || !after.at(OWNER).isMissingNode()
                    || !entry.path("contentSha256").asText().equals(contentHash(after)))
                throw new IllegalStateException("RECOVERY_RELEASE_UNCONFIRMED: " + name + "; inspect again");
            System.out.println("[workload-recovery] released=" + name + "; owner=" + entry.path("owner").asText()
                    + "; configurationRestored=false; podRestarted=false");
        }
    }

    private static void test(ArrayNode ops, String path, JsonNode value) {
        ops.addObject().put("op", "test").put("path", path).set("value", value);
    }

    private static String required(JsonNode node, String pointer) {
        JsonNode value = node.at(pointer);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalStateException("RECOVERY_IDENTITY_MISSING: " + pointer);
        return value.asText();
    }

    private static String contentHash(JsonNode cm) {
        ObjectNode content = WorkloadJson.JSON.createObjectNode();
        for (String key : List.of("data", "binaryData", "immutable")) if (cm.has(key)) content.set(key, cm.get(key));
        return hash(content);
    }

    static String hash(JsonNode node) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(node).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = WorkloadJson.JSON.createObjectNode();
            TreeSet<String> keys = new TreeSet<>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> out.set(key, canonical(node.get(key))));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = WorkloadJson.JSON.createArrayNode();
            node.forEach(value -> out.add(canonical(value)));
            return out;
        }
        return node;
    }
}
