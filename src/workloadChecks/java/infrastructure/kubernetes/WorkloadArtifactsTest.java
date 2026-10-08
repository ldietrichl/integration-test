package infrastructure.kubernetes;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorkloadArtifactsTest {
    @Test void refusesBuildAndOutsidePrivateRoot() {
        String old = System.getProperty("stand.artifacts.directory");
        try {
            for (String path : new String[]{"build/stand-artifacts", "src/test/resources/private"}) {
                System.setProperty("stand.artifacts.directory", path);
                var error = assertThrows(IllegalStateException.class, () -> new WorkloadArtifacts("dev", () -> false));
                assertTrue(error.getMessage().contains("PRIVATE_ARTIFACT_PATH_OUTSIDE_ROOT"));
                assertTrue(error.getMessage().contains("phase=RESOLVE_PATH"));
                assertNull(error.getCause());
            }
        } finally { reset(old); }
    }

    @Test void journalIsOutsideBuildAndPreservesPreimage() throws Exception {
        String old = System.getProperty("stand.artifacts.directory");
        Path root = Path.of(".workload-recovery/private/check-" + UUID.randomUUID()).toAbsolutePath().normalize();
        try {
            System.setProperty("stand.artifacts.directory", root.toString());
            var artifacts = new WorkloadArtifacts("dev", () -> false);
            var before = WorkloadJson.JSON.createObjectNode().put("owner", "run-1").put("value", "original");
            Path file = artifacts.event("WRITE_INTENT", before);
            assertTrue(file.startsWith(root));
            assertFalse(file.startsWith(Path.of("build").toAbsolutePath().normalize()));
            assertEquals("original", WorkloadJson.JSON.readTree(Files.readString(file)).at("/details/value").asText());
            Path confirmed = artifacts.event("WRITE_CONFIRMED", before);
            assertNotEquals(file, confirmed);
            assertEquals(2, WorkloadJson.JSON.readTree(Files.readString(confirmed)).path("sequence").asInt());
            assertThrows(IllegalStateException.class, () -> artifacts.saveText("../escaped", "value"));
            assertThrows(IllegalStateException.class, () -> artifacts.saveText(file.getFileName().toString(), "overwrite"));
        } finally {
            reset(old);
            if (Files.exists(root)) try (var paths = Files.walk(root)) {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
    private static void reset(String value) {
        if (value == null) System.clearProperty("stand.artifacts.directory"); else System.setProperty("stand.artifacts.directory", value);
    }
}
