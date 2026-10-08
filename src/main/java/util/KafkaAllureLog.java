package util;

import io.qameta.allure.Allure;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class KafkaAllureLog {

    private static final ThreadLocal<Deque<TopicContext>> TOPIC_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<TopicContext> LEGACY_TOPIC_CONTEXT = new ThreadLocal<>();

    private KafkaAllureLog() {
    }

    public static Scope waitingForTopic(String envName, String topic, Duration timeout, String details) {
        TopicContext context = new TopicContext(envName, topic);
        TOPIC_CONTEXT.get().push(context);
        Allure.step(waitingStep(envName, topic, timeout, details));
        return () -> {
            Deque<TopicContext> stack = TOPIC_CONTEXT.get();
            if (!stack.isEmpty() && stack.peek() == context) {
                stack.pop();
            } else {
                stack.remove(context);
            }
            if (stack.isEmpty()) {
                TOPIC_CONTEXT.remove();
            }
        };
    }

    public static Scope waitForTopic(String envName, String topic, Duration timeout, String details) {
        TopicContext context = new TopicContext(envName, topic);
        LEGACY_TOPIC_CONTEXT.set(context);
        Allure.step(waitingStep(envName, topic, timeout, details));
        return () -> {
            if (LEGACY_TOPIC_CONTEXT.get() == context) {
                LEGACY_TOPIC_CONTEXT.remove();
            }
        };
    }

    public static void sendToTopic(String envName, String topic, String key, String details) {
        Allure.step(sendStep(envName, topic, key, details));
    }

    public static String rewriteFrameworkStepName(String stepName) {
        TopicContext context = currentTopicContext();
        if (stepName == null || context == null) {
            return stepName;
        }

        if (stepName.startsWith("Получаем сообщения от всех топиков. Kafka[")) {
            return "Получаем сообщения из топика " + context.topic() + ". Kafka[" + context.envName() + "]";
        }
        if (stepName.startsWith("Отписываемся от всех топиков. Kafka[")) {
            return "Отписываемся от топика " + context.topic() + ". Kafka[" + context.envName() + "]";
        }
        if (stepName.startsWith("Подписываемся на топик ")) {
            return "Подписываемся на топик " + context.topic() + ". Kafka[" + context.envName() + "]";
        }
        return stepName;
    }

    public static void clearLegacyTopicContextAfterStep(String stepName) {
        if (stepName != null && stepName.startsWith("Отписываемся от топика ")) {
            LEGACY_TOPIC_CONTEXT.remove();
        }
    }

    private static TopicContext currentTopicContext() {
        Deque<TopicContext> stack = TOPIC_CONTEXT.get();
        if (!stack.isEmpty()) {
            return stack.peek();
        }
        return LEGACY_TOPIC_CONTEXT.get();
    }

    private static String waitingStep(String envName, String topic, Duration timeout, String details) {
        String result = "Ожидаем Kafka-сообщение из топика " + topic
                + ". Kafka[" + envName + "]"
                + ", timeout=" + timeout;
        return appendDetails(result, details);
    }

    private static String sendStep(String envName, String topic, String key, String details) {
        String result = "Отправляем Kafka-сообщение в топик " + topic
                + ". Kafka[" + envName + "]";
        if (key != null && !key.isBlank()) {
            result += ", key=" + key;
        }
        return appendDetails(result, details);
    }

    private static String appendDetails(String message, String details) {
        if (details == null || details.isBlank()) {
            return message;
        }
        return message + ", " + details;
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private static final class TopicContext {
        private final String envName;
        private final String topic;

        private TopicContext(String envName, String topic) {
            this.envName = Objects.requireNonNull(envName, "envName");
            this.topic = Objects.requireNonNull(topic, "topic");
        }

        private String envName() {
            return envName;
        }

        private String topic() {
            return topic;
        }
    }
}
