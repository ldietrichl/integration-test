package util.support;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public final class SplitterRuntimeMetadataChecks {
    public static void main(String[] args) {
        String before = System.getProperty("splitter.local.splitting-point");
        List<String> requested = new ArrayList<>();
        for (String point : List.of("MAPPER", "REACTIONS", "MAPPER")) {
            String route = "/" + point.toLowerCase(java.util.Locale.ROOT) + "/";
            String summary = SplitterRuntimeMetadata.summary(point, url -> {
                requested.add(url);
                assertTrue(url.contains(route), "Version endpoint must belong to the explicit service");
                return point + "-version";
            });
            assertTrue(summary.contains("splitter.splittingPoint=" + point));
            assertTrue(summary.contains("splitter.version=" + point + "-version"));
            for (String line : summary.split("\\R")) {
                if (line.matches("splitter\\.(version|config|split|precalculate)Url=.*"))
                    assertTrue(line.contains(route), line);
            }
            assertEquals(before, System.getProperty("splitter.local.splitting-point"), "No global point mutation");
        }
        assertEquals(3, requested.size());
        assertNotEquals(requested.get(0), requested.get(1));
        assertThrows(IllegalArgumentException.class, () -> SplitterRuntimeMetadata.summary("UNKNOWN", url -> "unused"));
        System.out.println("Splitter metadata checks: explicit per-service routes passed (no HTTP requests)");
    }
}
