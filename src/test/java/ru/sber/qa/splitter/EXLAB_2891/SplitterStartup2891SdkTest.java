package ru.sber.qa.splitter.EXLAB_2891;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;
import steps.sdk.splitter.SplitterStartupSteps;

import config.services.splitter.MapperPrecalcProfile;

import util.validation.ExceptionChainAssertions;

import org.junit.jupiter.api.*;
import ru.sber.qa.allure.Regression;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static steps.sdk.splitter.SplitterSdkAccess.*;

/** Isolated Spring binding and the real MAPPER executor factory; does not damage a shared pod. */
@EnabledIfSystemProperty(named = "exlab2891.sdk.enabled", matches = "true")
@DisplayName("EXLAB-2891. SDK: настройки MAPPER при создании контекста")
public class SplitterStartup2891SdkTest extends SplitterStartupSteps {

    @Regression
    @Test @DisplayName("EXLAB-2891-T18/T24. Валидные свойства создают контекст MAPPER")
    void validProperties() throws Throwable { binding(properties()); }

    @Regression
    @ParameterizedTest(name = "Неверное значение {0}")
    @ValueSource(strings = {"quantum", "api-config-load", "rules-config-code",
            "preliminary-calculation-enabled", "all-rule-code-exp-enabled", "empty-objects-response-enabled", "allow-result-without-main"})
    @DisplayName("EXLAB-2891-T19/T21/T24. Неверные типы настроек отклоняются при refresh; исправление восстанавливает старт")
    void invalidProperty(String key) throws Throwable {
        binding(properties());
        Map<String,Object> properties = properties();
        properties.put("splitter.config." + key, "INVALID_2891");
        Throwable error = assertThrows(Throwable.class, () -> binding(properties));
        ExceptionChainAssertions.startupFailure(error, "org.springframework.boot.context.properties.ConfigurationPropertiesBindException");
        String chain = chain(error).replace("-", "").toLowerCase(Locale.ROOT);
        assertTrue(chain.contains(key.replace("-", "")), chain);
        assertTrue(chain.contains("bind") || chain.contains("validat"), chain);
        binding(properties());
    }

    @Regression
    @ParameterizedTest(name = "Невалидные правила: {0}")
    @ValueSource(strings = {"nested-null", "malformed", "enum", "missing"})
    @DisplayName("EXLAB-2891-T20/T21/T24. Фабрика MAPPER отклоняет невалидные правила; исправление успешно")
    void invalidRules(String mutation) throws Throwable {
        String yaml = MapperPrecalcProfile.RULES;
        Path file = directory.resolve("rules.yml");
        Files.writeString(file, yaml);
        assertNotNull(mapperExecutor(file));
        Files.delete(file);
        String invalid = switch (mutation) {
            case "nested-null" -> yaml.replace("param-code: actionType", "param-code: null");
            case "malformed" -> "rules: [";
            case "enum" -> yaml.replace("value-type: INTEGER", "value-type: UNKNOWN_2891");
            default -> yaml;
        };
        if (!mutation.equals("missing")) Files.writeString(file, invalid);
        Throwable failure = assertThrows(Throwable.class, () -> mapperExecutor(file));
        ExceptionChainAssertions.startupFailure(failure,
                "jakarta.validation.ConstraintViolationException", "java.lang.IllegalArgumentException");
        String text = chain(failure);
        if (mutation.equals("nested-null")) {
            assertTrue(text.contains("ConstraintViolationException"), text);
            assertTrue(text.contains("paramCode"), text);
        } else {
            assertTrue(text.contains("rules") || text.contains("config") || text.contains("Config"), text);
            assertFalse(text.contains("NullPointerException"), text);
        }
        Files.writeString(file, yaml);
        assertNotNull(mapperExecutor(file));
    }

    @Regression
    @ParameterizedTest(name = "Пустой Kafka topic: {0}")
    @ValueSource(strings = {"splitting-config", "splitting-config-request", "splitter-monitoring",
            "splitter-reporting", "splitter-config-info", "splitting-request-converted"})
    @DisplayName("EXLAB-2891-T20/T24. Вложенные настройки Kafka валидируются при старте; исправление успешно")
    void invalidKafkaTopic(String topic) throws Throwable {
        Map<String,Object> valid = new LinkedHashMap<>();
        for (String name : List.of("splitting-config", "splitting-config-request", "splitter-monitoring",
                "splitter-reporting", "splitter-config-info", "splitting-request-converted"))
            valid.put("splitter.kafka.topic." + name, "2891-" + name);
        Map<String,Object> invalid = new LinkedHashMap<>(valid);
        binding(valid, "SplitterKafkaCustomProperties");
        invalid.put("splitter.kafka.topic." + topic, "");
        // Explicit blank value, not absence of a setting with a documented default.
        Throwable error = assertThrows(Throwable.class, () -> binding(invalid, "SplitterKafkaCustomProperties"));
        ExceptionChainAssertions.startupFailure(error, "org.springframework.boot.context.properties.ConfigurationPropertiesBindException");
        String text = chain(error).replace("-", "").toLowerCase(Locale.ROOT);
        assertTrue(text.contains(topic.replace("-", "")), text);
        assertTrue(text.contains("validat") || text.contains("bind"), text);
        binding(valid, "SplitterKafkaCustomProperties");
    }
}
