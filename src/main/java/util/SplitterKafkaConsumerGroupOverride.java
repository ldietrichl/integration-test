package util;

import io.qameta.allure.Allure;

public final class SplitterKafkaConsumerGroupOverride implements AutoCloseable {

    private static final String KAFKA_CONSUMER_GROUP_PROPERTY_PREFIX = "kafka_consumer.";
    private static final String KAFKA_CONSUMER_GROUP_PROPERTY_SUFFIX = ".group.id";

    private final String propertyName;
    private final String previousValue;
    private final boolean hadPreviousValue;
    private final String currentValue;

    private SplitterKafkaConsumerGroupOverride(String propertyName,
                                               String previousValue,
                                               boolean hadPreviousValue,
                                               String currentValue) {
        this.propertyName = propertyName;
        this.previousValue = previousValue;
        this.hadPreviousValue = hadPreviousValue;
        this.currentValue = currentValue;
    }

    public static SplitterKafkaConsumerGroupOverride apply(String env, String messageKey) {
        String propertyName = kafkaConsumerGroupProperty(env);
        String previousValue = System.getProperty(propertyName);
        boolean hadPreviousValue = previousValue != null;

        if (!uniqueConsumerGroupEnabled()) {
            Allure.parameter("splitter.config.kafka.consumer.group.property", propertyName);
            Allure.parameter("splitter.config.kafka.consumer.group.id", currentGroupId(env));
            return new SplitterKafkaConsumerGroupOverride(propertyName, previousValue, hadPreviousValue, previousValue);
        }

        String groupId = observerGroupId(env, messageKey);
        System.setProperty(propertyName, groupId);
        Allure.parameter("splitter.config.kafka.consumer.group.property", propertyName);
        Allure.parameter("splitter.config.kafka.consumer.group.id", groupId);
        Allure.step("Наблюдаем Kafka topic через consumer group " + groupId);
        return new SplitterKafkaConsumerGroupOverride(propertyName, previousValue, hadPreviousValue, groupId);
    }

    public static String currentGroupId(String env) {
        return System.getProperty(kafkaConsumerGroupProperty(env), "");
    }

    @Override
    public void close() {
        if (propertyName == null || currentValue == null) {
            return;
        }
        if (hadPreviousValue) {
            System.setProperty(propertyName, previousValue);
        } else {
            System.clearProperty(propertyName);
        }
    }

    private static boolean uniqueConsumerGroupEnabled() {
        return SplitterKafkaProperties.bool("splitter.config.kafka.unique.consumer.group.enabled", true);
    }

    private static String observerGroupPrefix() {
        return SplitterKafkaProperties.string("splitter.config.kafka.consumer.group.prefix",
                "integration-test-splitter-config-load");
    }

    private static String kafkaConsumerGroupProperty(String env) {
        return KAFKA_CONSUMER_GROUP_PROPERTY_PREFIX + env + KAFKA_CONSUMER_GROUP_PROPERTY_SUFFIX;
    }

    private static String observerGroupId(String env, String messageKey) {
        return observerGroupPrefix()
                + "-" + safeGroupIdPart(env)
                + "-" + safeGroupIdPart(messageKey);
    }

    private static String safeGroupIdPart(String raw) {
        String value = raw == null ? "unknown" : raw.replaceAll("[^a-zA-Z0-9._-]", "-");
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
