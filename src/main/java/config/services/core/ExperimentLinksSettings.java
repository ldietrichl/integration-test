package config.services.core;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** Настройки набора ИФТ из штатного файла стенда. */
public final class ExperimentLinksSettings {
    private final Properties values = TestConfigurationFiles.load("stand.properties");
    private static final String PREFIX = "experiment.links.ift.";
    private Integer observedPageSize;
    public void requireIft() {
        if (!"ift".equals(TestEnvironment.current()))
            throw new IllegalStateException("EXPLAB-3109 требует env=ift в test.properties");
    }
    public String value(String key, String fallback) {
        requireIft();
        return values.getProperty(PREFIX + key, fallback).trim();
    }
    public String required(String key) {
        String value = value(key, "");
        if (value.isBlank() || value.contains("<SET_ME") || value.contains("${"))
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: заполните " + PREFIX + key + " в stand.properties");
        return value;
    }
    private int positive(String key, String fallback) {
        int value = Integer.parseInt(value(key, fallback));
        if (value < 1) throw new IllegalArgumentException(PREFIX + key + " должен быть положительным");
        return value;
    }
    public Duration timeout() { return Duration.ofSeconds(positive("timeout.seconds", "120")); }
    public Duration quietWindow() { return Duration.ofSeconds(positive("quiet.seconds", "3")); }
    public void recordObservedPageSize(int pageSize) {
        requireIft();
        if (pageSize < 1) throw new IllegalArgumentException("Размер страницы стенда должен быть положительным");
        if (observedPageSize != null && observedPageSize != pageSize)
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: размер страницы изменился между чтениями конфигурации стенда");
        observedPageSize = pageSize;
    }
    public int pageSize() {
        requireIft();
        if (observedPageSize == null)
            throw new IllegalStateException("Размер страницы ещё не наблюдался в фактическом запросе Campaign Service");
        return observedPageSize;
    }
    public List<Long> campaignIds() {
        String configured = value("campaign.ids", "");
        if (configured.isBlank()) return List.of();
        List<Long> ids = Arrays.stream(configured.split(","))
                .map(String::trim).map(Long::valueOf).distinct().toList();
        if (ids.isEmpty() || ids.stream().anyMatch(id -> id <= 0))
            throw new IllegalArgumentException("campaign.ids: требуются реальные положительные ID кампаний ИФТ");
        return ids;
    }
    public String route(String route) {
        requireIft();
        return switch (route) {
            case "direct" -> RestEndpointResolver.baseUri(RestServiceEndpoint.EXPERIMENTS);
            case "campaign" -> RestEndpointResolver.requiredBaseUri("campaigns");
            default -> throw new IllegalArgumentException("Неподдерживаемый маршрут EXPLAB-3109: " + route);
        };
    }
    public String readinessPath() { return new StandSettings().required("workloads.experiment.readiness.path"); }
    public String kafkaProfile() {
        String profile = required("kafka.profile");
        if (!profile.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Некорректный Kafka profile");
        return profile;
    }
    public String topic() { return required("topic"); }
    public String database() { return value("database", "explab"); }
    public String schema() {
        String schema = value("schema", "experiments");
        if (!schema.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Некорректная схема БД");
        return schema;
    }
    public void requireIsolated() {
        if (!"true".equals(required("isolated")))
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: требуется подтверждённая изоляция очереди ИФТ; "
                    + PREFIX + "isolated=false");
    }
}
