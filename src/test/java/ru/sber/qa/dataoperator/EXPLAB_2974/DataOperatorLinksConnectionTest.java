package ru.sber.qa.dataoperator.EXPLAB_2974;

import io.qameta.allure.Allure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.dataoperator.LinksFixtureConfiguration;
import util.dataoperator.LinksFixtureRuntime;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Optional read-only diagnostic; not counted among the 173 endpoint scenarios. */
public class DataOperatorLinksConnectionTest {
    @Test
    @DisplayName("EXPLAB-2974. Вход в Ignite, чтение кэшей и совместимость формата без записи данных")
    void shouldConnectAndReadFixtureSchema() throws Exception {
        var config = new LinksFixtureConfiguration();
        var runtime = new LinksFixtureRuntime(config);
        System.out.println("EXPLAB-2974 probe started: environment=" + config.environment
                + ", runtimeSha256=" + runtime.runtimeSha256
                + ", runtimeDirectory=" + config.runtimeDirectory()
                + ", sslEnabled=" + config.ignite().required("ssl.enabled"));
        var result = runtime.probe();
        Allure.addAttachment("Ignite fixture compatibility probe", "application/json", result.toString(), ".json");

        // Check the child-process result explicitly; the project's source scanner must see verification here.
        assertEquals("explab-2974-storage-v1", result.path("storageContract").asText(), "Unexpected storage contract");
        assertTrue(result.path("schemaCompatible").isBoolean()
                && result.path("schemaCompatible").booleanValue(), "Storage schema was not verified");
        assertTrue(result.path("mutations").isBoolean(), "Probe must explicitly report whether it mutated data");
        assertFalse(result.path("mutations").booleanValue(), "Connection diagnostic must not write fixture data");
        var cacheReads = result.path("cacheReadVerified");
        assertTrue(cacheReads.isArray(), "Probe must report verified cache reads");
        Set<String> actualCaches = new HashSet<>();
        cacheReads.forEach(cache -> {
            assertTrue(cache.isTextual(), "Verified cache names must be strings");
            actualCaches.add(cache.textValue());
        });
        Set<String> expectedCaches = Set.of("splitting_object_cache", "splitting_field_cache",
                "actualization_cache", "param_cache", "data_source_cache");
        assertEquals(expectedCaches.size(), cacheReads.size(), "Each required cache must be verified exactly once");
        assertEquals(expectedCaches, actualCaches, "Probe did not verify all five service caches");
        System.out.println("Ignite authentication, cache reads and storage schema verified. No fixture data written.");
    }
}
