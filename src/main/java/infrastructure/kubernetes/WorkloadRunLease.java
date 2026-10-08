package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Cooperative cluster-wide mutex. Never expires or steals another run's ownership. */
public final class WorkloadRunLease implements AutoCloseable {
    public static final String KEY = "scheduler-regression.explab/run-owner";
    private static final String POINTER = "/metadata/annotations/scheduler-regression.explab~1run-owner";
    private final Supplier<JsonNode> read;
    private final Consumer<String> patch;
    private final String owner = UUID.randomUUID().toString();
    private String uid;
    private boolean attempted;
    private boolean acquired;
    private boolean annotationsAbsent;

    public WorkloadRunLease(Supplier<JsonNode> read, Consumer<String> patch) {
        this.read = read;
        this.patch = patch;
    }
    public void acquire() {
        if (attempted) throw new IllegalStateException("RUN_LEASE_ALREADY_ATTEMPTED");
        JsonNode current = read.get();
        if (!current.at(POINTER).isMissingNode())
            throw new IllegalStateException("SCHEDULER_RUN_BUSY: another run or recovery owns the ConfigMap; no jobs/pods changed");
        uid = current.at("/metadata/uid").asText();
        annotationsAbsent = current.at("/metadata/annotations").isMissingNode()
                || current.at("/metadata/annotations").isNull();
        ArrayNode operations = identityTests(current);
        if (annotationsAbsent) {
            ObjectNode annotations = WorkloadJson.JSON.createObjectNode().put(KEY, owner);
            operations.addObject().put("op", "add").put("path", "/metadata/annotations").set("value", annotations);
        } else {
            operations.addObject().put("op", "add").put("path", POINTER).put("value", owner);
        }
        attempted = true; // close must resolve an ambiguous transport failure with a GET.
        patch.accept(operations.toString());
        requireHeld();
        acquired = true;
        System.out.println("[workload-lease] acquired=true; owner=" + owner + "; automaticSteal=false");
    }
    public void requireHeld() {
        JsonNode current = read.get();
        if (!uid.equals(current.at("/metadata/uid").asText()) || !owner.equals(current.at(POINTER).asText()))
            throw new IllegalStateException("SCHEDULER_RUN_LEASE_LOST: no further fixture writes are permitted");
    }
    public boolean acquired() { return acquired; }
    public String owner() { return owner; }
    @Override public void close() {
        if (!attempted) return;
        JsonNode current = read.get();
        if (!uid.equals(current.at("/metadata/uid").asText()))
            throw new IllegalStateException("RUN_LEASE_RESOURCE_REPLACED: manual recovery required");
        JsonNode actual = current.at(POINTER);
        if (actual.isMissingNode()) { attempted = false; acquired = false; return; }
        if (!owner.equals(actual.asText()))
            throw new IllegalStateException("RUN_LEASE_OWNER_CHANGED: another owner's annotation is preserved");
        ArrayNode operations = identityTests(current);
        operations.addObject().put("op", "test").put("path", POINTER).put("value", owner);
        // Remove the annotations object only if this run created it and it still contains only our key.
        String path = annotationsAbsent && current.at("/metadata/annotations").size() == 1
                ? "/metadata/annotations" : POINTER;
        operations.addObject().put("op", "remove").put("path", path);
        patch.accept(operations.toString());
        JsonNode after = read.get();
        if (!uid.equals(after.at("/metadata/uid").asText()) || !after.at(POINTER).isMissingNode())
            throw new IllegalStateException("RUN_LEASE_RELEASE_UNCONFIRMED");
        attempted = false;
        acquired = false;
        System.out.println("[workload-lease] released=true; owner=" + owner);
    }
    private ArrayNode identityTests(JsonNode current) {
        String currentUid = current.at("/metadata/uid").asText();
        String version = current.at("/metadata/resourceVersion").asText();
        if (currentUid.isBlank() || version.isBlank())
            throw new IllegalStateException("RUN_LEASE_IDENTITY_MISSING");
        ArrayNode operations = WorkloadJson.JSON.createArrayNode();
        operations.addObject().put("op", "test").put("path", "/metadata/uid").put("value", currentUid);
        operations.addObject().put("op", "test").put("path", "/metadata/resourceVersion").put("value", version);
        return operations;
    }
}
