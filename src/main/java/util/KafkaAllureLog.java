package util;

import io.qameta.allure.Allure;

import java.time.Duration;

public final class KafkaAllureLog {

    private KafkaAllureLog() {
    }

    public static void waitForTopic(String envName, String topic, Duration timeout, String details) {
        String message = "Ожидаем Kafka-сообщение из топика " + topic
                + ". Kafka[" + envName + "]"
                + ", timeout=" + timeout;
        if (details != null && !details.isBlank()) {
            message += ", " + details;
        }
        Allure.step(message);
    }
}
