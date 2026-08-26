package ru.sber.qa.splitter.EXPLAB_2400;

import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.LoadConfigResponseDto;
import dto.splitter.monitoring.SplitterConfigLoadMonitoringDto;

import java.util.Set;

import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertNull;
import static util.TestAssertions.assertTrue;

final class SplitterConfigLoadMonitoring2400Assertions {

    private static final Set<String> SUPPORTED_SERVICE_NAMES = Set.of("splitter-service", "splitter--service");

    private SplitterConfigLoadMonitoring2400Assertions() {
    }

    static void assertAcceptedLoadResponse(LoadConfigResponseDto response, long expectedCurrentVersion) {
        String result = normalized(response.getResult());
        assertTrue(Set.of("LOADED", "LOADED_WITH_PRECALC").contains(result),
                "Ожидали успешную загрузку конфигурации, фактически=" + response);
        assertTrue(response.getCurrentConfigVersion() != null,
                "После успешной загрузки currentConfigVersion должен быть заполнен: " + response);
        assertEquals(expectedCurrentVersion, response.getCurrentConfigVersion().longValue(),
                "После успешной загрузки currentConfigVersion должен соответствовать загруженной версии");
        assertNull(response.getResultDetails(), "Для успешной загрузки resultDetails должен быть null");
    }

    static void assertLoadResponse(LoadConfigResponseDto response,
                                   String expectedResult,
                                   long expectedCurrentVersion) {
        assertEquals(expectedResult, normalized(response.getResult()), "Некорректный result ответа loadConfig");
        assertTrue(response.getCurrentConfigVersion() != null,
                "currentConfigVersion ответа loadConfig должен быть заполнен: " + response);
        assertEquals(expectedCurrentVersion, response.getCurrentConfigVersion().longValue(),
                "Некорректный currentConfigVersion ответа loadConfig");
    }

    static void assertLoadResponseDetailsContains(LoadConfigResponseDto response, String expectedPart) {
        assertFalse(response.getResultDetails() == null || response.getResultDetails().isBlank(),
                "Ожидали непустой resultDetails: " + response);
        assertTrue(response.getResultDetails().contains(expectedPart),
                "resultDetails должен содержать '" + expectedPart + "': " + response);
    }

    static void assertLoadedWithPrecalc(SplitterConfigLoadMonitoringDto event,
                                        LoadConfigRequestDto request,
                                        long sinceEpochMillis,
                                        int expectedSoConfigVersion,
                                        ConfigLoadPrecalcCounters2400 expectedCounters) {
        assertCommon(event, request, "LOADED_WITH_PRECALC", sinceEpochMillis, request.getConfigVersion());
        assertNull(event.getResultDetails(), context(event));
        assertTrue(event.getSoConfigVersion() != null, context(event));
        assertEquals(expectedSoConfigVersion, event.getSoConfigVersion().intValue(), context(event));
        assertTrue(event.getNotLinkedObjects() != null, context(event));
        assertEquals(expectedCounters.notLinkedObjects(), event.getNotLinkedObjects().intValue(), context(event));
        assertTrue(event.getTotalObjects() != null, context(event));
        assertEquals(expectedCounters.totalObjects(), event.getTotalObjects().intValue(), context(event));
        assertTrue(event.getLinkedExps() != null,
                "По EXPLAB-2400 поле linkedExps должно присутствовать\n" + context(event));
        assertEquals(expectedCounters.linkedExps(), event.getLinkedExps().intValue(),
                "По EXPLAB-2400 счетчик должен публиковаться в поле linkedExps\n" + context(event));
        assertNull(event.getNotLinkedExps(),
                "Ошибочное поле notLinkedExps не должно публиковаться вместо linkedExps\n" + context(event));
        assertTrue(event.getTotalExps() != null, context(event));
        assertEquals(expectedCounters.totalExps(), event.getTotalExps().intValue(), context(event));
    }

    static void assertOldVersion(SplitterConfigLoadMonitoringDto event,
                                 LoadConfigRequestDto request,
                                 long sinceEpochMillis,
                                 long expectedCurrentVersion) {
        assertCommon(event, request, "NOT_LOADED_OLD_VERSION", sinceEpochMillis, expectedCurrentVersion);
        assertNull(event.getResultDetails(), context(event));
        assertNoPrecalcCounters(event);
    }

    static void assertRequestParamsRejected(SplitterConfigLoadMonitoringDto event,
                                            LoadConfigRequestDto request,
                                            long sinceEpochMillis,
                                            long expectedCurrentVersion) {
        assertCommon(event, request, "REQUEST_PARAMS_WITH_PRECALC_ENABLED", sinceEpochMillis, expectedCurrentVersion);
        assertNull(event.getResultDetails(), context(event));
        assertNoPrecalcCounters(event);
        assertTrue(event.getMessage().contains("REQUEST_PARAMS"), context(event));
    }

    static void assertValidationFailed(SplitterConfigLoadMonitoringDto event,
                                       LoadConfigRequestDto request,
                                       long sinceEpochMillis,
                                       long expectedCurrentVersion,
                                       String expectedDetails) {
        assertCommon(event, request, "VALIDATION_FAILED", sinceEpochMillis, expectedCurrentVersion);
        assertFalse(event.getResultDetails() == null || event.getResultDetails().isBlank(),
                "VALIDATION_FAILED должен содержать resultDetails\n" + context(event));
        assertEquals(expectedDetails, event.getResultDetails(),
                "Monitoring должен передавать ту же детализацию, что вернул loadConfig\n" + context(event));
        assertNoPrecalcCounters(event);
    }

    private static void assertCommon(SplitterConfigLoadMonitoringDto event,
                                     LoadConfigRequestDto request,
                                     String expectedResult,
                                     long sinceEpochMillis,
                                     long expectedCurrentVersion) {
        assertEquals("SPLITTING_CONFIG_LOAD", normalized(event.getFunction()), context(event));
        assertEquals(expectedResult, normalized(event.getResult()), context(event));
        assertEquals("API", normalized(event.getLoadMethod()), context(event));
        assertEquals(request.getMessageId(), event.getRequestIdIn(), context(event));
        assertEquals(request.getSplittingPointCode(), event.getSplittingPoint(), context(event));
        assertTrue(event.getCurrentConfigVersion() != null, context(event));
        assertEquals(expectedCurrentVersion, event.getCurrentConfigVersion().longValue(), context(event));
        assertTrue(event.getNewConfigVersion() != null, context(event));
        assertEquals(request.getConfigVersion().longValue(), event.getNewConfigVersion().longValue(), context(event));
        assertTrue(SUPPORTED_SERVICE_NAMES.contains(event.getService()),
                "Неожиданное service=" + event.getService() + "\n" + context(event));
        assertFalse(event.getMessage() == null || event.getMessage().isBlank(),
                "Monitoring event должен содержать message\n" + context(event));
        assertCompletedTimestamp(event, sinceEpochMillis);
    }

    private static void assertCompletedTimestamp(SplitterConfigLoadMonitoringDto event, long sinceEpochMillis) {
        Long rawTimestamp = event.getCompletedTimestamp();
        assertTrue(rawTimestamp != null && rawTimestamp > 0,
                "completedTimestamp должен быть заполнен\n" + context(event));
        long millis = rawTimestamp < 10_000_000_000L ? rawTimestamp * 1000L : rawTimestamp;
        assertTrue(millis >= sinceEpochMillis - 1_000L,
                "completedTimestamp не должен быть раньше начала проверяемой операции"
                        + "\ncompletedTimestamp=" + rawTimestamp
                        + "\nsince=" + sinceEpochMillis
                        + "\n" + context(event));
    }

    private static void assertNoPrecalcCounters(SplitterConfigLoadMonitoringDto event) {
        assertNull(event.getSoConfigVersion(), context(event));
        assertNull(event.getNotLinkedObjects(), context(event));
        assertNull(event.getTotalObjects(), context(event));
        assertNull(event.getLinkedExps(), context(event));
        assertNull(event.getNotLinkedExps(), context(event));
        assertNull(event.getTotalExps(), context(event));
    }

    private static String normalized(String value) {
        return value == null ? null : value.trim().replaceFirst("^'+", "");
    }

    private static String context(SplitterConfigLoadMonitoringDto event) {
        return "Monitoring event: " + event;
    }
}


