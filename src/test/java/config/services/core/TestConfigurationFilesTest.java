package config.services.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestConfigurationFilesTest {
    @TempDir Path project;

    @Test
    void sourceEnvironmentWinsOverStaleCompiledResource() throws Exception {
        Path source = project.resolve("src/test/resources/test.properties");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "env=ift\n");
        assertEquals("ift", TestConfigurationFiles.load(project, "test.properties", resource("env=dev\n"))
                .getProperty("env"));
    }

    @Test
    void packagedTestsUseClasspathWhenSourceIsAbsent() {
        assertEquals("ift", TestConfigurationFiles.load(project, "test.properties", resource("env=ift\n"))
                .getProperty("env"));
    }

    @Test
    void missingConfigurationDoesNotDefaultToAnotherEnvironment() {
        assertThrows(IllegalStateException.class,
                () -> TestConfigurationFiles.load(project, "test.properties", resource(null)));
    }

    @Test
    @ResourceLock("SYSTEM_PROPERTIES")
    void currentEnvironmentAndOwnerIgnoreStaleJvmSelector() {
        String expected = TestEnvironment.normalize(TestConfigurationFiles.load("test.properties").getProperty("env"));
        String previous = System.getProperty("env");
        try {
            System.setProperty("env", "stale-invalid-environment");
            assertEquals(expected, TestEnvironment.current());
            assertEquals(expected, CustomTestConfigScope.TEST_CONFIG.env());
        } finally {
            if (previous == null) System.clearProperty("env");
            else System.setProperty("env", previous);
        }
    }

    private static ClassLoader resource(String text) {
        return new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                return text == null ? null : new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
            }
        };
    }
}
