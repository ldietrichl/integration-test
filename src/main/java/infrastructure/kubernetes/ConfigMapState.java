package infrastructure.kubernetes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Exact ConfigMap data values; document resources are explicit full-key replacements. */
public record ConfigMapState(String name, Map<String, String> data) {
    public static boolean equivalent(String key, String left, String right) {
        if (Objects.equals(left, right)) return true;
        if (left == null || right == null || !(key.endsWith(".yml") || key.endsWith(".yaml"))) return false;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()
                            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
            mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            var first = mapper.readTree(left);
            var second = mapper.readTree(right);
            return first != null && first.isObject() && first.equals(second);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Invalid YAML in managed ConfigMap key: " + key);
        }
    }
    public ConfigMapState {
        if (name == null || !name.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?"))
            throw new IllegalArgumentException("Exact ConfigMap name required");
        Objects.requireNonNull(data, "ConfigMap data");
        if (data.isEmpty() || data.size() > 16) throw new IllegalArgumentException("1..16 ConfigMap keys required");
        data = Map.copyOf(data);
        data.forEach((key, value) -> {
            if (!key.matches("[A-Za-z0-9._-]+")
                    || key.toLowerCase(java.util.Locale.ROOT).matches(".*(password|secret|token|credential|private|keystore).*"))
                throw new IllegalArgumentException("Non-secret ConfigMap data key required");
            if (value.getBytes(StandardCharsets.UTF_8).length > 262144)
                throw new IllegalArgumentException("ConfigMap value exceeds 256 KiB");
        });
    }

    public static ConfigMapState resource(String name, String key, String resource) {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalArgumentException("ConfigMap resource not found: " + resource);
            return new ConfigMapState(name, Map.of(key, new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read ConfigMap resource: " + resource, failure);
        }
    }
}
