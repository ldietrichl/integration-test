package config.services.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads the editable project resource first, including direct IDEA test launches. */
public final class TestConfigurationFiles {
    private TestConfigurationFiles() { }

    public static Properties load(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = TestConfigurationFiles.class.getClassLoader();
        return load(Path.of(""), resource, loader);
    }

    static Properties load(Path projectDirectory, String resource, ClassLoader loader) {
        Path source = projectDirectory.resolve("src/test/resources").resolve(resource);
        try (InputStream input = Files.isRegularFile(source)
                ? Files.newInputStream(source) : loader.getResourceAsStream(resource)) {
            if (input == null) {
                // Попробовать загрузить из classpath (IDEA, gradle test)
                InputStream cpInput = loader.getResourceAsStream(resource);
                if (cpInput != null) {
                    Properties properties = new Properties();
                    properties.load(cpInput);
                    return properties;
                }
                throw new IllegalStateException("Test configuration is missing: " + source + " and classpath resource not found: " + resource);
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read test configuration: " + source, error);
        }
    }
}
