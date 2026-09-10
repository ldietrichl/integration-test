package util.ignite;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure configuration checks with synthetic inputs; no files, service settings or Ignite connections are read. */
class IgniteConfigurationTest {
    @ParameterizedTest
    @CsvSource({"DEV, dev", "ift, ift", "EIFt, ift", "ift_ds, ift", "EIFT-DS, ift"})
    void selectsOneNormalizedEnvironmentForAddressesAndIdentity(String selected, String expected) {
        Map<String, String> settings = canonical("dev");
        settings.putAll(canonical("ift"));

        IgniteConfiguration configuration = configuration(selected, settings);

        assertEquals(expected, configuration.environment());
        assertEquals(expected + ".example.invalid:10800", configuration.required("addresses"));
        assertEquals(Map.of("IGNITE_CLIENT_USERNAME", expected + "-test-user",
                "IGNITE_CLIENT_PASSWORD", expected + "-synthetic-password",
                "IGNITE_CLIENT_SSL_ENABLED", "false"), configuration.helperEnvironment());
    }

    @ParameterizedTest
    @CsvSource({"0, jvm-synthetic", "1, environment-synthetic", "2, secure-synthetic", "3, resource-synthetic"})
    void prioritizesJvmThenEnvironmentThenSecureThenResource(int firstSource, String expected) {
        Map<String, String> resource = canonical("dev");
        resource.put("ignite.dev.password", "resource-synthetic");
        EnvironmentProperties properties = properties(
                firstSource == 0 ? Map.of("ignite.dev.password", "jvm-synthetic") : Map.of(),
                firstSource <= 1 ? Map.of("IGNITE_DEV_PASSWORD", "environment-synthetic") : Map.of(),
                firstSource <= 2 ? Map.of("ignite.dev.password", "secure-synthetic") : Map.of(), resource);

        IgniteConfiguration configuration = new IgniteConfiguration("dev", properties);

        assertEquals(expected, configuration.helperEnvironment().get("IGNITE_CLIENT_PASSWORD"));
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "2", "3"})
    void anExplicitBlankMasksEveryLowerPrioritySource(int firstSource) {
        Map<String, String> resource = Map.of("ignite.dev.password",
                firstSource == 3 ? "   " : "resource-synthetic");
        Map<String, String> secure = firstSource > 2 ? Map.of()
                : Map.of("ignite.dev.password", firstSource == 2 ? "   " : "secure-synthetic");
        Map<String, String> environment = firstSource > 1 ? Map.of()
                : Map.of("IGNITE_DEV_PASSWORD", firstSource == 1 ? "   " : "environment-synthetic");
        Map<String, String> system = firstSource == 0 ? Map.of("ignite.dev.password", "   ") : Map.of();

        assertNull(properties(system, environment, secure, resource).optional("ignite.dev.password"));
    }

    @Test
    void explicitlyClearingBothCredentialsDoesNotReintroduceThemFromAnotherSource() {
        Map<String, String> resource = canonical("dev");
        resource.putAll(legacy("dev"));
        EnvironmentProperties properties = properties(
                Map.of("ignite.dev.username", "", "ignite.dev.password", ""),
                Map.of("IGNITE_DEV_USERNAME", "environment-test-user", "IGNITE_DEV_PASSWORD", "environment-synthetic"),
                Map.of(), resource);

        IgniteConfiguration configuration = new IgniteConfiguration("dev", properties);

        assertEquals(Map.of("IGNITE_CLIENT_SSL_ENABLED", "false"), configuration.helperEnvironment());
    }

    @Test
    void aBlankCanonicalAddressFailsInsteadOfAdoptingLegacyOrLowerPriorityAddress() {
        Map<String, String> resource = canonical("dev");
        resource.putAll(legacy("dev"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new IgniteConfiguration("dev", properties(
                        Map.of("ignite.dev.addresses", " "), Map.of(), Map.of(), resource)));

        assertTrue(failure.getMessage().contains("ignite.dev.addresses"));
    }

    @Test
    void aPartlyMigratedCanonicalIdentityCannotBorrowTheLegacyPassword() {
        Map<String, String> settings = legacy("dev");
        settings.putAll(canonical("dev"));
        settings.remove("ignite.dev.password");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> configuration("dev", settings));

        assertTrue(failure.getMessage().contains("both Ignite username and password"));
        assertFalse(failure.getMessage().contains("legacy-synthetic-password"));
    }

    @Test
    void addingOnlyACanonicalRuntimeDoesNotSilentlyMixItWithALegacyConnection() {
        Map<String, String> settings = legacy("dev");
        settings.put("ignite.dev.runtime.directory", "tools/ignite-client/corporate-runtimes/new-runtime");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> configuration("dev", settings));

        assertTrue(failure.getMessage().contains("ignite.dev.addresses"));
    }

    @Test
    void canonicalAnonymousTlsDoesNotImportLegacyIdentityOrStores() {
        Map<String, String> settings = legacy("dev");
        settings.put("ignite.dev.addresses", "canonical.example.invalid:10800");
        settings.put("ignite.dev.ssl.enabled", "true");
        settings.put("ignite.dev.runtime.directory", "tools/ignite-client/corporate-runtimes/new-runtime");

        IgniteConfiguration configuration = configuration("dev", settings);

        assertFalse(configuration.usesLegacyProfile());
        assertEquals(Map.of("IGNITE_CLIENT_SSL_ENABLED", "true"), configuration.helperEnvironment());
    }

    @Test
    void completeLegacyProfilePreservesIdentityStoresAndOriginalRuntimeSelection() {
        Map<String, String> settings = legacy("dev");
        // Canonical IFT settings do not activate canonical mode for DEV.
        settings.putAll(canonical("ift"));

        IgniteConfiguration configuration = configuration("dev", settings);

        assertTrue(configuration.usesLegacyProfile());
        assertEquals("legacy-dev.example.invalid:10800", configuration.required("addresses"));
        assertEquals(absolute("tools/data-operator-explab-2974/corporate-runtimes/original-runtime"),
                configuration.runtimeDirectory());
        assertEquals(Map.of(
                "IGNITE_CLIENT_USERNAME", "legacy-test-user",
                "IGNITE_CLIENT_PASSWORD", "legacy-synthetic-password",
                "IGNITE_CLIENT_SSL_ENABLED", "true",
                "IGNITE_CLIENT_SSL_KEY_STORE_PATH", "synthetic/dev-client.p12",
                "IGNITE_CLIENT_SSL_KEY_STORE_TYPE", "PKCS12",
                "IGNITE_CLIENT_SSL_KEY_STORE_PASSWORD", "synthetic-key-password",
                "IGNITE_CLIENT_SSL_TRUST_STORE_PATH", "synthetic/dev-trust.jks",
                "IGNITE_CLIENT_SSL_TRUST_STORE_TYPE", "JKS",
                "IGNITE_CLIENT_SSL_TRUST_STORE_PASSWORD", "synthetic-trust-password"),
                configuration.helperEnvironment());
    }

    @Test
    void legacyProfileWithoutRuntimeKeepsItsHistoricalBundleDefault() {
        Map<String, String> settings = legacy("dev");
        settings.remove("links.fixture.dev.runtime.directory");

        assertEquals(absolute("tools/data-operator-explab-2974"), configuration("dev", settings).runtimeDirectory());
    }

    @Test
    void canonicalProfileRequiresAnExplicitRuntimeEvenWhenLegacyRuntimeExists() {
        Map<String, String> settings = legacy("dev");
        settings.putAll(canonical("dev"));
        settings.remove("ignite.dev.runtime.directory");
        IgniteConfiguration configuration = configuration("dev", settings);

        IllegalStateException failure = assertThrows(IllegalStateException.class, configuration::runtimeDirectory);

        assertTrue(failure.getMessage().contains("ignite.dev.runtime.directory"));
    }

    @Test
    void selectedIftTlsStoresUseTheChildProtocolNamesWithoutDevValues() {
        Map<String, String> settings = canonical("dev");
        settings.putAll(canonical("ift"));
        addCanonicalStores(settings, "dev");
        addCanonicalStores(settings, "ift");
        EnvironmentProperties properties = properties(Map.of(),
                Map.of("IGNITE_IFT_SSL_KEY_STORE_PATH", "synthetic/ift-from-env.p12"), Map.of(), settings);

        IgniteConfiguration configuration = new IgniteConfiguration("eift-ds", properties);

        assertEquals(Map.of(
                "IGNITE_CLIENT_USERNAME", "ift-test-user",
                "IGNITE_CLIENT_PASSWORD", "ift-synthetic-password",
                "IGNITE_CLIENT_SSL_ENABLED", "true",
                "IGNITE_CLIENT_SSL_KEY_STORE_PATH", "synthetic/ift-from-env.p12",
                "IGNITE_CLIENT_SSL_KEY_STORE_TYPE", "PKCS12",
                "IGNITE_CLIENT_SSL_KEY_STORE_PASSWORD", "ift-synthetic-key-password",
                "IGNITE_CLIENT_SSL_TRUST_STORE_PATH", "synthetic/ift-trust.jks",
                "IGNITE_CLIENT_SSL_TRUST_STORE_TYPE", "JKS",
                "IGNITE_CLIENT_SSL_TRUST_STORE_PASSWORD", "ift-synthetic-trust-password"),
                configuration.helperEnvironment());
    }

    @Test
    void disablingTlsOmitsPreviouslyConfiguredStoresFromTheChildEnvironment() {
        Map<String, String> settings = canonical("dev");
        addCanonicalStores(settings, "dev");
        settings.put("ignite.dev.ssl.enabled", "false");

        IgniteConfiguration configuration = configuration("dev", settings);

        assertEquals(Map.of("IGNITE_CLIENT_USERNAME", "dev-test-user",
                "IGNITE_CLIENT_PASSWORD", "dev-synthetic-password",
                "IGNITE_CLIENT_SSL_ENABLED", "false"), configuration.helperEnvironment());
    }

    @Test
    void missingIftConfigurationNeverFallsBackToAWorkingDevProfile() {
        Map<String, String> settings = canonical("dev");
        settings.putAll(legacy("dev"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> configuration("ift", settings));

        assertTrue(failure.getMessage().contains("links.fixture.ift.ignite.addresses"));
    }

    private static IgniteConfiguration configuration(String environment, Map<String, String> resource) {
        return new IgniteConfiguration(environment, properties(Map.of(), Map.of(), Map.of(), resource));
    }

    private static EnvironmentProperties properties(Map<String, String> system, Map<String, String> environment,
            Map<String, String> secure, Map<String, String> resource) {
        Properties values = new Properties();
        values.putAll(resource);
        return new EnvironmentProperties(system::get, environment::get, secure::get, values, Function.identity());
    }

    private static Map<String, String> canonical(String environment) {
        String prefix = "ignite." + environment + ".";
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "addresses", environment + ".example.invalid:10800");
        values.put(prefix + "username", environment + "-test-user");
        values.put(prefix + "password", environment + "-synthetic-password");
        values.put(prefix + "ssl.enabled", "false");
        values.put(prefix + "runtime.directory", "tools/ignite-client/corporate-runtimes/" + environment + "-runtime");
        return values;
    }

    private static void addCanonicalStores(Map<String, String> values, String environment) {
        String prefix = "ignite." + environment + ".";
        values.put(prefix + "ssl.enabled", "true");
        values.put(prefix + "ssl.key-store.path", "synthetic/" + environment + "-client.p12");
        values.put(prefix + "ssl.key-store.type", "PKCS12");
        values.put(prefix + "ssl.key-store.password", environment + "-synthetic-key-password");
        values.put(prefix + "ssl.trust-store.path", "synthetic/" + environment + "-trust.jks");
        values.put(prefix + "ssl.trust-store.type", "JKS");
        values.put(prefix + "ssl.trust-store.password", environment + "-synthetic-trust-password");
    }

    private static Map<String, String> legacy(String environment) {
        String prefix = "links.fixture." + environment + ".";
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "ignite.addresses", "legacy-" + environment + ".example.invalid:10800");
        values.put(prefix + "ignite.username", "legacy-test-user");
        values.put(prefix + "ignite.password", "legacy-synthetic-password");
        values.put(prefix + "ignite.ssl.enabled", "true");
        values.put(prefix + "ignite.ssl.keyStore", "synthetic/" + environment + "-client.p12");
        values.put(prefix + "ignite.ssl.keyStoreType", "PKCS12");
        values.put(prefix + "ignite.ssl.keyStorePassword", "synthetic-key-password");
        values.put(prefix + "ignite.ssl.trustStore", "synthetic/" + environment + "-trust.jks");
        values.put(prefix + "ignite.ssl.trustStoreType", "JKS");
        values.put(prefix + "ignite.ssl.trustStorePassword", "synthetic-trust-password");
        values.put(prefix + "runtime.directory", "tools/data-operator-explab-2974/corporate-runtimes/original-runtime");
        return values;
    }

    private static Path absolute(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }
}
