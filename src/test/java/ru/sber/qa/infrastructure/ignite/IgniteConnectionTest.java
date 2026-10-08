package ru.sber.qa.infrastructure.ignite;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.ignite.IgniteClientRuntime;
import util.ignite.IgniteConfiguration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Read-only connection diagnostic independent of any service, cache schema or fixture flag. */
public class IgniteConnectionTest {
    @Test
    @DisplayName("Ignite: authentication and cache discovery in the selected environment, without data writes")
    void shouldAuthenticateAndDiscoverCaches() throws Exception {
        var configuration = new IgniteConfiguration();
        Path bundle = configuration.runtimeDirectory();
        boolean supportPresent = Files.isRegularFile(bundle.resolve("IgniteClientSupport.java"));
        boolean probePresent = Files.isRegularFile(bundle.resolve("IgniteConnectionProbe.java"));
        if (supportPresent != probePresent) {
            throw new IllegalStateException("Incomplete common Ignite helper sources in " + bundle);
        }
        Path sourceRoot = supportPresent ? bundle : Path.of("tools/ignite-client");
        var runtime = new IgniteClientRuntime(configuration, List.of(
                sourceRoot.resolve("IgniteClientSupport.java"), sourceRoot.resolve("IgniteConnectionProbe.java")),
                "IgniteConnectionProbe", configuration.outputDirectory(), "IGNITE_RESULT=");
        Path directory = configuration.outputDirectory().resolve("probe-" + UUID.randomUUID());
        Files.createDirectories(directory);
        Path manifest = directory.resolve("probe-manifest.json");
        var json = new ObjectMapper();
        json.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), Map.of(
                "environment", configuration.environment(), "igniteAddresses", configuration.required("addresses"),
                "runtimeSha256", runtime.runtimeSha256));
        System.out.println("Ignite probe: environment=" + configuration.environment()
                + ", runtimeDirectory=" + bundle + ", runtimeSha256=" + runtime.runtimeSha256
                + ", legacyProfile=" + configuration.usesLegacyProfile());
        var result = runtime.call("probe", manifest);
        json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("probe-result.json").toFile(), result);
        Allure.addAttachment("Ignite connection probe", "application/json", result.toString(), ".json");

        assertEquals(configuration.environment(), result.path("environment").asText());
        assertTrue(result.path("authenticated").isBoolean() && result.path("authenticated").booleanValue(),
                "Ignite authentication must succeed");
        assertTrue(result.path("mutations").isBoolean(), "Probe must explicitly declare mutation policy");
        assertFalse(result.path("mutations").booleanValue(), "Connection probe must not write data");
        assertTrue(result.path("cacheCount").isIntegralNumber() && result.path("cacheCount").asInt() >= 0,
                "Cache discovery must complete; an empty cluster is allowed");
    }
}
