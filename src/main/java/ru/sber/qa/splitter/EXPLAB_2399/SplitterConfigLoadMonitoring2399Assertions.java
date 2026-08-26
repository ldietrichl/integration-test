package ru.sber.qa.splitter.EXPLAB_2399;

import com.fasterxml.jackson.databind.JsonNode;
import dto.splitter.config.LoadConfigRequestDto;

import java.util.List;

import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertNotNull;
import static util.TestAssertions.assertTrue;

final class SplitterConfigLoadMonitoring2399Assertions {

    private SplitterConfigLoadMonitoring2399Assertions() {
    }

    static void assertStatus(JsonNode statusMessage,
                             LoadConfigRequestDto config,
                             String expectedStatus) {
        assertEquals("STATUS", value(statusMessage, "messageType"), statusMessage.toPrettyString());
        assertEquals(expectedStatus, normalized(statusMessage, "status"), statusMessage.toPrettyString());
        assertEquals(config.getMessageId(), value(statusMessage, "configMessageId"), statusMessage.toPrettyString());
        assertEquals(String.valueOf(config.getConfigVersion()), value(statusMessage, "newConfigVersion"), statusMessage.toPrettyString());
        assertEquals(config.getSplittingPointCode(), value(statusMessage, "splittingPointCode"), statusMessage.toPrettyString());
        assertPresent(statusMessage, "messageId");
        assertPresent(statusMessage, "currentConfigVersion");
    }

    static void assertStatusDescContains(JsonNode statusMessage, String expectedText) {
        String statusDesc = value(statusMessage, "statusDesc");
        assertNotNull(statusDesc, "statusDesc должен быть заполнен\n" + statusMessage.toPrettyString());
        assertTrue(statusDesc.contains(expectedText),
                "statusDesc должен содержать: " + expectedText + "\n" + statusMessage.toPrettyString());
    }

    static void assertLoadedWithPrecalcMonitoring(JsonNode monitoringMessage,
                                                  LoadConfigRequestDto config) {
        assertMonitoringCommon(monitoringMessage, config, "LOADED_WITH_PRECALC");
        assertEquals("KAFKA", value(monitoringMessage, "loadMethod"), monitoringMessage.toPrettyString());
        assertPresentAny(monitoringMessage, "splittingPointCode", "splittingPoing");
        assertPresent(monitoringMessage, "completedTimestamp");
        assertPresent(monitoringMessage, "soConfigVersion");
        assertNumericField(monitoringMessage, "notLinkedObjects");
        assertNumericField(monitoringMessage, "totalObjects");
        assertNumericField(monitoringMessage, "linkedExps");
        assertNumericField(monitoringMessage, "totalExps");
    }

    static void assertOldVersionMonitoring(JsonNode monitoringMessage,
                                           LoadConfigRequestDto config) {
        assertMonitoringCommon(monitoringMessage, config, "NOT_LOADED_OLD_VERSION");
        assertEquals("KAFKA", value(monitoringMessage, "loadMethod"), monitoringMessage.toPrettyString());
        assertContainsAny(monitoringMessage, "resultDetails", List.of("младше текущей", "не актуальной версии", "старее текущей"));
    }

    static void assertRequestParamsWithPrecalcMonitoring(JsonNode monitoringMessage,
                                                         LoadConfigRequestDto config) {
        assertMonitoringCommon(monitoringMessage, config, "REQUEST_PARAMS_WITH_PRECALC_ENABLED");
        assertEquals("KAFKA", value(monitoringMessage, "loadMethod"), monitoringMessage.toPrettyString());
        assertContainsAny(monitoringMessage, "resultDetails", List.of("REQUEST_PARAMS", "параметрами запроса", "предрасчет"));
    }

    static void assertValidationFailedMonitoring(JsonNode monitoringMessage,
                                                 LoadConfigRequestDto config,
                                                 String expectedDetailsText) {
        assertMonitoringCommon(monitoringMessage, config, "VALIDATION_FAILED");
        assertEquals("KAFKA", value(monitoringMessage, "loadMethod"), monitoringMessage.toPrettyString());
        assertContainsAny(monitoringMessage, "resultDetails", List.of(expectedDetailsText));
    }

    static void assertInvalidMessageMonitoring(JsonNode monitoringMessage, String messageId) {
        assertEquals("SPLITTING_CONFIG_LOAD", value(monitoringMessage, "function"), monitoringMessage.toPrettyString());
        assertEquals("VALIDATION_FAILED", normalized(monitoringMessage, "result"), monitoringMessage.toPrettyString());
        assertEquals("KAFKA", value(monitoringMessage, "loadMethod"), monitoringMessage.toPrettyString());
        assertEquals("splitter-service", value(monitoringMessage, "service"), monitoringMessage.toPrettyString());
        assertTrue(messageId.equals(value(monitoringMessage, "messageId"))
                        || messageId.equals(value(monitoringMessage, "requestIdIn")),
                "В мониторинге не найден исходный messageId/requestIdIn=" + messageId
                        + "\n" + monitoringMessage.toPrettyString());
        assertPresent(monitoringMessage, "resultDetails");
        assertKafkaMetadata(monitoringMessage);
    }

    private static void assertMonitoringCommon(JsonNode monitoringMessage,
                                               LoadConfigRequestDto config,
                                               String expectedResult) {
        assertEquals("SPLITTING_CONFIG_LOAD", value(monitoringMessage, "function"), monitoringMessage.toPrettyString());
        assertEquals(expectedResult, normalized(monitoringMessage, "result"), monitoringMessage.toPrettyString());
        assertEquals(config.getMessageId(), valueAny(monitoringMessage, "messageId", "requestIdIn"), monitoringMessage.toPrettyString());
        assertEquals(String.valueOf(config.getConfigVersion()), value(monitoringMessage, "newConfigVersion"), monitoringMessage.toPrettyString());
        assertEquals(config.getSplittingPointCode(), valueAny(monitoringMessage, "splittingPointCode", "splittingPoing"), monitoringMessage.toPrettyString());
        assertEquals("splitter-service", value(monitoringMessage, "service"), monitoringMessage.toPrettyString());
        assertPresent(monitoringMessage, "currentConfigVersion");
        assertKafkaMetadata(monitoringMessage);
    }

    private static void assertKafkaMetadata(JsonNode monitoringMessage) {
        assertPresent(monitoringMessage, "kafkaDtIn");
        assertPresent(monitoringMessage, "kafkaPartitionIn");
        assertPresent(monitoringMessage, "kafkaOffsetIn");
    }

    private static void assertNumericField(JsonNode node, String field) {
        assertPresent(node, field);
        JsonNode value = node.path(field);
        assertTrue(value.isNumber() || value.asText().matches("-?\\d+"),
                field + " должен быть числом\n" + node.toPrettyString());
    }

    private static void assertContainsAny(JsonNode node, String field, List<String> expectedParts) {
        String actual = value(node, field);
        assertNotNull(actual, field + " должен быть заполнен\n" + node.toPrettyString());
        boolean found = expectedParts.stream().anyMatch(actual::contains);
        assertTrue(found, field + " должен содержать одно из значений " + expectedParts
                + "\nactual=" + actual
                + "\n" + node.toPrettyString());
    }

    private static void assertPresent(JsonNode node, String field) {
        assertFalse(node.path(field).isMissingNode() || node.path(field).isNull(),
                "Ожидали поле " + field + "\n" + node.toPrettyString());
    }

    private static void assertPresentAny(JsonNode node, String... fields) {
        assertNotNull(valueAny(node, fields),
                "Ожидали одно из полей " + List.of(fields) + "\n" + node.toPrettyString());
    }

    private static String value(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String valueAny(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = value(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String normalized(JsonNode node, String field) {
        String value = value(node, field);
        return value == null ? null : value.trim().replaceFirst("^'+", "");
    }
}


