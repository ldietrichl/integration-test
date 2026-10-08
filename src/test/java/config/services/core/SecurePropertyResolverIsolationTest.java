package config.services.core;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.jasypt.util.text.BasicTextEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each probe gets its own working directory and JVM, including fresh Owner singleton scopes. */
class SecurePropertyResolverIsolationTest {
    private static final String CLASSPATH_PROPERTY = "property.layout.test.classpath";
    private static final String KEY = "SECURE_PROPERTY_LAYOUT_SYNTHETIC_PASSWORD";
    private static final String LOGIN_KEY = "SECURE_PROPERTY_LAYOUT_SYNTHETIC_LOGIN";
    private static final String FILE_VALUE = "synthetic-file-$-\\-marker";
    private static final String FILE_LOGIN = "synthetic-file-login";
    private static final String EXTERNAL_VALUE = "synthetic-external-marker";
    private static final String LEGACY_VALUE = "synthetic-legacy-marker";
    private static final String TOKEN_PROPERTY = "rest.configuration-service.token";
    private static final String ENCRYPTION_PASSWORD = "encryption.password";
    private static final String SYNTHETIC_ENCRYPTION_PASSWORD = "synthetic-local-encryption-marker";

    @TempDir Path project;

    @Test
    void overrideWinsOverJvmAndEnvironmentForResolverOwnerAndCompositeJaas() throws Exception {
        writeProperties(project.resolve("secure.local.override.properties"),
                KEY, FILE_VALUE, LOGIN_KEY, FILE_LOGIN);
        writeProperties(project.resolve("secure.local.properties"), KEY, LEGACY_VALUE);
        runProbe("override", true);
    }

    @Test
    void legacySecureAndGradleLocalFilesAreNotSecretSources() throws Exception {
        writeProperties(project.resolve("secure.local.properties"), KEY, LEGACY_VALUE);
        writeProperties(project.resolve("gradle.local.properties"), KEY, LEGACY_VALUE);
        runProbe("legacy-only", false);
    }

    @Test
    void separateUserFileSuppliesSecretsToAllNativeConsumers() throws Exception {
        writeProperties(project.resolve("secure.users.local.override.properties"),
                KEY, FILE_VALUE, LOGIN_KEY, FILE_LOGIN);
        runProbe("override", true);
    }

    @Test
    void originalOverrideKeepsPriorityOverSeparateUserFile() throws Exception {
        writeProperties(project.resolve("secure.local.override.properties"),
                KEY, FILE_VALUE, LOGIN_KEY, FILE_LOGIN);
        writeProperties(project.resolve("secure.users.local.override.properties"),
                KEY, LEGACY_VALUE, LOGIN_KEY, LEGACY_VALUE);
        runProbe("override", true);
    }

    @Test
    void missingOverrideCannotBeReplacedByJvmOrEnvironment() throws Exception {
        runProbe("external-only", true);
    }

    @Test
    void encryptedValuesUseOnlyTheOverrideKeyAndPreserveSpecialCharacters() throws Exception {
        writeProperties(project.resolve("secure.local.override.properties"),
                KEY, encryptedValue(), LOGIN_KEY, FILE_LOGIN,
                ENCRYPTION_PASSWORD, SYNTHETIC_ENCRYPTION_PASSWORD);
        runProbe("encrypted", true);
    }

    @Test
    void encryptedValuesFailClosedWithoutAnOverrideEncryptionKey() throws Exception {
        writeProperties(project.resolve("secure.local.override.properties"), KEY, encryptedValue());
        runProbe("encrypted-key-missing", true);
    }

    private void runProbe(String mode, boolean externalValues) throws Exception {
        // Only synthetic inputs are written: no developer secret file is read or modified.
        writeProperties(project.resolve("src/test/resources/test.properties"),
                "env", "ift", TOKEN_PROPERTY, "${" + KEY + "}");
        String configuredClasspath = System.getProperty(CLASSPATH_PROPERTY);
        assertTrue(configuredClasspath != null && !configuredClasspath.isBlank(),
                "propertyLayoutTest must supply property.layout.test.classpath");
        String classpath = String.join(File.pathSeparator,
                Arrays.stream(configuredClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                        .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString()).toList());
        Path executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        List<String> command = new ArrayList<>(List.of(executable.toString(),
                "-Dsecure.placeholders.fail-on-unresolved=true"));
        if (externalValues) {
            command.add("-D" + KEY + "=" + EXTERNAL_VALUE);
            command.add("-D" + LOGIN_KEY + "=" + EXTERNAL_VALUE);
            command.add("-D" + TOKEN_PROPERTY + "=" + EXTERNAL_VALUE);
            command.add("-D" + ENCRYPTION_PASSWORD + "=" + EXTERNAL_VALUE);
        }
        command.addAll(List.of("-cp", classpath, Probe.class.getName(), mode));
        Path output = project.resolve("probe-output.txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile());
        // Avoid inherited launcher arguments or an inherited synthetic test key changing the probe.
        for (String name : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS",
                KEY, LOGIN_KEY, "REST_CONFIGURATION_SERVICE_TOKEN", ENCRYPTION_PASSWORD,
                "ENCRYPTION_PASSWORD")) {
            builder.environment().remove(name);
        }
        if (externalValues) {
            builder.environment().put(KEY, EXTERNAL_VALUE);
            builder.environment().put(LOGIN_KEY, EXTERNAL_VALUE);
            builder.environment().put("REST_CONFIGURATION_SERVICE_TOKEN", EXTERNAL_VALUE);
            builder.environment().put(ENCRYPTION_PASSWORD, EXTERNAL_VALUE);
            builder.environment().put("ENCRYPTION_PASSWORD", EXTERNAL_VALUE);
        }
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Isolated configuration probe timed out");
            String text = Files.readString(output, StandardCharsets.UTF_8);
            // Only controlled type/frame diagnostics are published, never raw child stdout/stderr.
            String diagnostics = text.lines().filter(line -> line.startsWith("PROPERTY_LAYOUT_FAILURE_"))
                    .reduce("", (left, line) -> left + System.lineSeparator() + line);
            assertEquals(0, process.exitValue(), "Isolated configuration policy probe failed: " + mode + diagnostics);
            assertFalse(text.contains(FILE_VALUE) || text.contains(EXTERNAL_VALUE) || text.contains(LEGACY_VALUE)
                            || text.contains(SYNTHETIC_ENCRYPTION_PASSWORD),
                    "Isolated configuration probe exposed a synthetic secret");
            assertTrue(text.contains("PROPERTY_LAYOUT_PROBE_OK"), "Configuration probe did not finish its assertions");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static void writeProperties(Path path, String... pairs) throws Exception {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        for (int i = 0; i < pairs.length; i += 2) properties.setProperty(pairs[i], pairs[i + 1]);
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "Synthetic isolated configuration policy test");
        }
    }

    private static String encryptedValue() {
        BasicTextEncryptor encryptor = new BasicTextEncryptor();
        encryptor.setPassword(SYNTHETIC_ENCRYPTION_PASSWORD);
        return "ENC(" + encryptor.encrypt(FILE_VALUE) + ")";
    }

    /** Invoked by a child JVM only; this helper is deliberately part of the test source set. */
    public static final class Probe {
        public static void main(String[] arguments) {
            try {
                if (arguments.length != 1) throw new AssertionError();
                if (arguments[0].equals("override")) verifyOverride(false);
                else if (arguments[0].equals("encrypted")) verifyOverride(true);
                else if (arguments[0].equals("encrypted-key-missing")) verifyMissingEncryptionKey();
                else if (arguments[0].equals("legacy-only") || arguments[0].equals("external-only")) {
                    verifyMissingOverride();
                } else throw new AssertionError();
                System.out.println("PROPERTY_LAYOUT_PROBE_OK");
            } catch (Throwable failure) {
                // No exception messages; report only types and source filenames/line numbers.
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    System.err.println("PROPERTY_LAYOUT_FAILURE_TYPE: " + cause.getClass().getName());
                    for (StackTraceElement frame : cause.getStackTrace()) {
                        System.err.println("PROPERTY_LAYOUT_FAILURE_AT: " + frame.getClassName() + "."
                                + frame.getMethodName() + "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")");
                    }
                }
                System.exit(1);
            }
        }

        private static void verifyOverride(boolean encrypted) {
            require(FILE_VALUE.equals(SecurePropertyResolver.resolve("${" + KEY + "}")));
            String stored = SecureLocalConfigScope.SECURE_LOCAL_CONFIG.getProperty(KEY);
            require(encrypted ? stored != null && stored.startsWith("ENC(") : FILE_VALUE.equals(stored));
            if (encrypted) require(FILE_VALUE.equals(SecurePropertyResolver.resolve(stored)));
            require(FILE_VALUE.equals(CustomTestConfigScope.TEST_CONFIG.configurationServiceToken()));
            require(FILE_VALUE.equals(TestPropertiesLoader.optional(TOKEN_PROPERTY)));
            String jaas = "example.module required username=\"${" + LOGIN_KEY
                    + "}\" password=\"${" + KEY + "}\";";
            String expected = "example.module required username=\"" + FILE_LOGIN
                    + "\" password=\"" + FILE_VALUE + "\";";
            require(expected.equals(SecurePropertyResolver.resolve(jaas)));
        }

        private static void verifyMissingEncryptionKey() {
            expectMissing(() -> SecurePropertyResolver.resolve("${" + KEY + "}"));
            expectMissing(() -> CustomTestConfigScope.TEST_CONFIG.configurationServiceToken());
            expectMissing(() -> TestPropertiesLoader.optional(TOKEN_PROPERTY));
        }

        private static void verifyMissingOverride() {
            require(SecureLocalConfigScope.SECURE_LOCAL_CONFIG.getProperty(KEY) == null);
            expectMissing(() -> SecurePropertyResolver.resolve("${" + KEY + "}"));
            expectMissing(() -> CustomTestConfigScope.TEST_CONFIG.configurationServiceToken());
            expectMissing(() -> TestPropertiesLoader.optional(TOKEN_PROPERTY));
        }

        private static void expectMissing(Runnable action) {
            try {
                action.run();
            } catch (IllegalStateException expected) {
                String message = expected.getMessage();
                require(message != null && message.contains("secure.local.override.properties"));
                require(!message.contains(FILE_VALUE) && !message.contains(EXTERNAL_VALUE)
                        && !message.contains(LEGACY_VALUE) && !message.contains(SYNTHETIC_ENCRYPTION_PASSWORD));
                return;
            }
            throw new AssertionError();
        }

        private static void require(boolean condition) {
            if (!condition) throw new AssertionError();
        }
    }
}
