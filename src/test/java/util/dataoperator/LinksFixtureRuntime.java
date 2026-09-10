package util.dataoperator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import util.ignite.IgniteClientRuntime;
import util.ignite.IgniteHelperSession;

/** Adapter from the data-operator storage contract to the shared isolated Ignite runtime. */
public final class LinksFixtureRuntime implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LinksFixtureConfiguration config;
    private final IgniteClientRuntime client;
    private IgniteHelperSession session;
    private Path sessionManifest;
    public final String runtimeSha256;

    public LinksFixtureRuntime(LinksFixtureConfiguration config) throws Exception {
        this.config = config;
        Path bundle = config.runtimeDirectory();
        boolean bundledSchema = Files.isRegularFile(bundle.resolve("LinksCacheSchema.java"));
        boolean bundledTool = Files.isRegularFile(bundle.resolve("LinksCacheTool.java"));
        if (bundledSchema != bundledTool) {
            throw new IllegalStateException("Incomplete data-operator helper sources in " + bundle);
        }
        // Keep exact source bytes/order from old bundles so their recovery fingerprint is unchanged.
        Path sources = bundledSchema ? bundle : Path.of("tools/data-operator-explab-2974");
        client = new IgniteClientRuntime(config.ignite(),
                List.of(sources.resolve("LinksCacheSchema.java"), sources.resolve("LinksCacheTool.java")),
                "LinksCacheTool", config.output(), "FIXTURE_RESULT=");
        runtimeSha256 = client.runtimeSha256;
    }

    /** Authenticates and checks the data-operator storage schema without writing fixture rows. */
    public JsonNode probe() throws Exception {
        Path directory = config.output().resolve("probe-" + UUID.randomUUID());
        Files.createDirectories(directory);
        Path manifest = directory.resolve("probe-manifest.json");
        JSON.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), Map.of(
                "environment", config.environment, "serviceUri", config.serviceUri,
                "igniteAddresses", config.ignite().required("addresses"), "fixtureRuntimeSha256", runtimeSha256));
        JsonNode result = call("probe", manifest);
        JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("probe-result.json").toFile(), result);
        return result;
    }

    public JsonNode call(String mode, Path manifest) throws Exception {
        if (session != null) {
            requireSessionManifest(manifest);
            return session.call(mode);
        }
        return client.call(mode, manifest, config.legacyHelperEnvironment());
    }

    public void startSession(Path manifest) throws Exception {
        if (session != null) throw new IllegalStateException("Fixture helper session already started");
        sessionManifest = manifest.toAbsolutePath().normalize();
        session = client.openSession(sessionManifest, config.legacyHelperEnvironment());
    }

    private void requireSessionManifest(Path manifest) {
        if (!manifest.toAbsolutePath().normalize().equals(sessionManifest)) {
            throw new IllegalArgumentException("Fixture helper session belongs to another manifest");
        }
    }

    /** Recovery remains one-shot; a broken session must be dead before the recovery writer starts. */
    public JsonNode cleanup(Path manifest) throws Exception {
        if (session == null) return call("cleanup", manifest);
        requireSessionManifest(manifest);
        Exception sessionFailure = null;
        if (session.isUsable()) {
            try { return session.call("cleanup"); }
            catch (Exception failure) {
                if (session.isUsable()) throw failure; // Valid command error: keep claim and report it.
                sessionFailure = failure;
            }
        }
        boolean interrupted = Thread.interrupted();
        try {
            session.abort(); // Throws if exit cannot be confirmed. Never overlap two helpers.
            session = null;
            JsonNode result = client.call("cleanup", manifest, config.legacyHelperEnvironment());
            ((com.fasterxml.jackson.databind.node.ObjectNode) result).put("recoveredAfterSessionFailure", true);
            if (sessionFailure instanceof InterruptedException) throw sessionFailure;
            return result;
        } catch (Exception recoveryFailure) {
            if (sessionFailure != null && recoveryFailure != sessionFailure) recoveryFailure.addSuppressed(sessionFailure);
            throw recoveryFailure;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    @Override public void close() throws Exception {
        if (session != null) session.close();
    }
}
