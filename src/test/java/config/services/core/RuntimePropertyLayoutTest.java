package config.services.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards editable, versioned configuration without opening the developer's secret override. */
class RuntimePropertyLayoutTest {
    private static final Pattern CREDENTIAL_KEY = Pattern.compile(
            "(?i)(?:.*[._-])?(?:password|passwd|pass|token|secret|login|username|api[._-]?key)$"
                    + "|.*sasl[.]jaas[.]config$|tokenName|tokenPassword|allureToken");
    private static final Pattern SECRET_REFERENCE = Pattern.compile("\\$\\{SECURE_[A-Z0-9_]+}");

    @Test
    void runtimeCredentialsAreEmptyOrReferencesToTheSecretOverride() throws Exception {
        List<Path> files;
        try (var resources = Files.list(Path.of("src/test/resources"))) {
            files = new ArrayList<>(resources.filter(path -> path.toString().endsWith(".properties")).toList());
        }
        files.add(Path.of("gradle.properties"));
        // Installation examples must obey the same policy when copied into test.properties.
        files.add(Path.of("docs/services/experiments/EXPLAB-2972/test-properties.example.txt"));
        files.add(Path.of("docs/services/experiments/EXPLAB-2972/launch-plan-properties.example.txt"));
        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            Properties properties = read(file);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key).trim();
                if (!allowedRuntimeValue(file, key, value)) {
                    violations.add(file.getFileName() + ":" + key);
                }
            }
        }
        assertTrue(violations.isEmpty(), "Runtime credentials must be SECURE references; keys: " + violations);
    }

    @Test
    void trackedSecureTemplateContainsNoUsableValues() throws Exception {
        Properties template = read(Path.of("secure.local.properties"));
        List<String> violations = new ArrayList<>();
        for (String key : template.stringPropertyNames()) {
            String value = template.getProperty(key).trim();
            if (!value.isEmpty() && !(value.startsWith("<SET_ME_") && value.endsWith(">"))) {
                violations.add(key);
            }
        }
        assertTrue(violations.isEmpty(), "Tracked secure template contains non-placeholder keys: " + violations);
    }

    @Test
    void secretOverrideRemainsIgnoredAndHasNoExplicitReinclude() throws Exception {
        List<String> rules = Files.readAllLines(Path.of(".gitignore")).stream()
                .map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        assertTrue(rules.contains("secure.local.override.properties")
                        || rules.contains("/secure.local.override.properties"),
                ".gitignore must explicitly exclude the sole local secret file");
        assertFalse(rules.stream().anyMatch(rule -> rule.startsWith("!")
                        && (rule.contains("override.properties") || rule.equals("!*.properties")
                        || rule.equals("!**/*.properties"))),
                ".gitignore must not reinclude the secret override");
    }


    @Test
    void requireTokenPolicyAcceptsOnlyBooleanStandSettings() {
        for (String env : List.of("dev", "ift", "lt", "local")) {
            String key = "stand." + env + ".kubernetes.auth.require-token";
            assertTrue(allowedRuntimeValue(Path.of("stand.properties"), key, "true"));
            assertTrue(allowedRuntimeValue(Path.of("stand.properties"), key, "false"));
            assertFalse(allowedRuntimeValue(Path.of("stand.properties"), key, "token-value"));
            assertFalse(allowedRuntimeValue(Path.of("stand.properties"), key, ""));
            assertFalse(allowedRuntimeValue(Path.of("test.properties"), key, "true"));
        }
    }

    @Test
    void actualCredentialsStillRejectLiteralValuesIncludingBooleans() {
        for (String key : List.of("rest.gateway.token", "password", "allureToken",
                "stand.dev.kubernetes.auth.token", "custom.require-token")) {
            assertFalse(allowedRuntimeValue(Path.of("stand.properties"), key, "true"));
            assertFalse(allowedRuntimeValue(Path.of("stand.properties"), key, "false"));
            assertFalse(allowedRuntimeValue(Path.of("stand.properties"), key, "literal-value"));
            assertTrue(allowedRuntimeValue(Path.of("stand.properties"), key, ""));
            assertTrue(allowedRuntimeValue(Path.of("stand.properties"), key, "${SECURE_TEST_TOKEN}"));
        }
    }

    private static boolean allowedRuntimeValue(Path file, String key, String value) {
        // This exact setting is a security policy, never an authentication credential.
        if (file.getFileName().toString().equals("stand.properties")
                && key.matches("stand[.][a-zA-Z0-9_-]+[.]kubernetes[.]auth[.]require-token")) {
            return value.equals("true") || value.equals("false");
        }
        return !CREDENTIAL_KEY.matcher(key).matches()
                || value.isEmpty() || SECRET_REFERENCE.matcher(value).matches();
    }

    private static Properties read(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
