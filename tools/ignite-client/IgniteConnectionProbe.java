import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import org.apache.ignite.Ignition;

/** Authenticates and requests cache names without reading rows or changing cluster data. */
public final class IgniteConnectionProbe {
    private static final ObjectMapper JSON = new ObjectMapper();

    private IgniteConnectionProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2 || !"probe".equals(arguments[0])) {
            throw new IllegalArgumentException("Expected probe and manifest path");
        }
        JsonNode manifest = JSON.readTree(Path.of(arguments[1]).toFile());
        String environment = manifest.path("environment").asText();
        if (environment.isBlank()) {
            throw new IllegalArgumentException("Ignite probe environment is required");
        }
        try (var client = Ignition.startClient(
                IgniteClientSupport.configuration(manifest.path("igniteAddresses").asText()))) {
            int cacheCount = client.cacheNames().size();
            System.out.println("IGNITE_RESULT=" + JSON.writeValueAsString(Map.of(
                    "environment", environment,
                    "authenticated", true,
                    "mutations", false,
                    "cacheCount", cacheCount)));
        }
    }
}
