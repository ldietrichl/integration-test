package config.services.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

class RestEndpointResolverTest {
    @TempDir Path project;

    @Test
    void currentEnvironmentAndAllRestUrisMustBeValid() {
        RestEndpointResolver.validateCurrentEnvironment();

        assertTrue(Set.of("dev", "ift", "ift-dm", "lt", "local")
                .contains(RestEndpointResolver.currentEnvironment()));
        for (RestServiceEndpoint endpoint : RestServiceEndpoint.values()) {
            String uri = RestEndpointResolver.baseUri(endpoint);
            assertFalse(uri.isBlank());
            assertTrue(uri.startsWith("http://") || uri.startsWith("https://"));
            assertFalse(uri.endsWith("/"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fileRoutingSupportsGatewayAndSeparateServicesAndIgnoresExternalOverrides(boolean separateServices) throws Exception {
        Path properties = project.resolve("src/test/resources/test.properties");
        Files.createDirectories(properties.getParent());
        Files.writeString(properties, "env=ift\nrest.ift.gateway.base-uri=https://gateway.example.invalid/\n"
                + (separateServices ? "rest.ift.experiments.base-uri=https://experiment.example.invalid/\n"
                        + "rest.ift.splitter.base-uri=https://splitter.example.invalid/\n"
                        + "rest.ift.splitter-reactions.base-uri=https://reactions.example.invalid/\n" : ""));
        String runtimeClasspath = System.getProperty("property.layout.test.classpath");
        assertTrue(runtimeClasspath != null && !runtimeClasspath.isBlank(),
                "propertyLayoutTest must supply property.layout.test.classpath");
        String classpath = String.join(File.pathSeparator, Arrays.stream(
                        runtimeClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString()).toList());
        Path executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Path output = project.resolve("routing-probe.txt");
        ProcessBuilder builder = new ProcessBuilder(executable.toString(), "-Denv=dev",
                "-Drest.ift.gateway.base-uri=https://stale-jvm.example.invalid",
                "-Drest.ift.experiments.base-uri=https://stale-jvm.example.invalid",
                "-cp", classpath, Probe.class.getName(), Boolean.toString(separateServices))
                .directory(project.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        for (String name : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
            builder.environment().remove(name);
        }
        builder.environment().put("ENV", "dev");
        builder.environment().put("REST_IFT_GATEWAY_BASE_URI", "https://stale-env.example.invalid");
        builder.environment().put("REST_IFT_EXPERIMENTS_BASE_URI", "https://stale-env.example.invalid");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Isolated REST routing probe timed out");
            assertEquals(0, process.exitValue(), "Isolated REST routing probe failed: " + Files.readString(output));
            assertTrue(Files.readString(output).contains("REST_ROUTING_PROBE_OK"));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    /** Fresh JVM prevents static property caches from reading the developer's live configuration. */
    public static final class Probe {
        public static void main(String[] arguments) {
            boolean separate = Boolean.parseBoolean(arguments[0]);
            String gateway = "https://gateway.example.invalid";
            assertEquals("ift", RestEndpointResolver.currentEnvironment());
            RestEndpointResolver.validateCurrentEnvironment();
            assertEquals(gateway, RestEndpointResolver.baseUri(RestServiceEndpoint.EXPLAB_GATEWAY));
            for (RestServiceEndpoint endpoint : RestServiceEndpoint.values()) {
                String expected = switch (endpoint) {
                    case EXPERIMENTS -> separate ? "https://experiment.example.invalid" : gateway;
                    case SPLITTER, SPLITTER_MAPPER -> separate ? "https://splitter.example.invalid" : gateway;
                    case SPLITTER_REACTIONS -> separate ? "https://reactions.example.invalid" : gateway;
                    default -> gateway;
                };
                assertEquals(expected, RestEndpointResolver.baseUri(endpoint), endpoint.name());
            }
            System.out.println("REST_ROUTING_PROBE_OK");
        }
    }
}
