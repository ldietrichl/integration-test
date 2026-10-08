package util.ignite;

import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentPropertiesTest {
    @Test
    @ResourceLock("SYSTEM_PROPERTIES")
    void testPropertiesPreserveGeneratedFixtureSwitchesWithoutAcceptingConnectionOverrides() {
        Map<String,String> changes = Map.of(
                "data-operator.fixture.dev.enabled", "true",
                "data-operator.fixture.dev.output.directory", "build/generated-fixture-output",
                "links.fixture.dev.run-id", "synthetic-run-id",
                "ignite.dev.addresses", "jvm.example.invalid:10800",
                "ignite.dev.password", "jvm-synthetic-password",
                "links.fixture.dev.ignite.password", "jvm-legacy-synthetic-password");
        Map<String,String> before = new java.util.HashMap<>();
        changes.forEach((key,value) -> { before.put(key, System.getProperty(key)); System.setProperty(key, value); });
        try {
            Properties source = new Properties();
            source.setProperty("data-operator.fixture.dev.enabled", "false");
            source.setProperty("ignite.dev.addresses", "file.example.invalid:10800");
            source.setProperty("ignite.dev.password", "file-synthetic-password");
            var policy = new EnvironmentProperties(source, Function.identity(), true);
            assertEquals("true", policy.optional("data-operator.fixture.dev.enabled"));
            assertEquals("build/generated-fixture-output", policy.optional("data-operator.fixture.dev.output.directory"));
            assertEquals("synthetic-run-id", policy.optional("links.fixture.dev.run-id"));
            assertEquals("file.example.invalid:10800", policy.optional("ignite.dev.addresses"));
            assertEquals("file-synthetic-password", policy.optional("ignite.dev.password"));
            assertNull(policy.optional("links.fixture.dev.ignite.password"));
            var connection = new EnvironmentProperties(source, Function.identity(), false);
            assertEquals("false", connection.optional("data-operator.fixture.dev.enabled"));
            assertNull(connection.optional("data-operator.fixture.dev.output.directory"));
        } finally {
            before.forEach((key,value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key,value); });
        }
    }
}
