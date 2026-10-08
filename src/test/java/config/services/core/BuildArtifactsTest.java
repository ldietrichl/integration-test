package config.services.core;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildArtifactsTest {
    @Test
    void acceptsGeneratedSubdirectoriesInsideBuild() throws Exception {
        assertEquals(Path.of("build/regression-fixtures/dev/run").toFile().getCanonicalFile().toPath(),
                BuildArtifacts.directory("build/regression-fixtures/dev/run"));
    }

    @Test
    void rejectsBuildRootAndPathsEscapingBuild() {
        for (String path : new String[]{"build", "regression-fixtures", "build/../src/test/resources",
                "build-neighbour/results"}) {
            assertThrows(IllegalArgumentException.class, () -> BuildArtifacts.directory(path));
        }
    }
}
