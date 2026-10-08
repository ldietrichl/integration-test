package config.services.core;

import java.io.IOException;
import java.nio.file.Path;

/** Generated evidence belongs to build so Gradle clean can remove it. */
public final class BuildArtifacts {
    private BuildArtifacts() {
    }

    public static Path directory(String configuredPath) {
        try {
            Path root = Path.of("build").toFile().getCanonicalFile().toPath();
            Path output = Path.of(configuredPath).toFile().getCanonicalFile().toPath();
            if (output.equals(root) || !output.startsWith(root)) {
                throw new IllegalArgumentException("Generated test output must be in a subdirectory of build");
            }
            return output;
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot resolve generated test output directory", error);
        }
    }
}
