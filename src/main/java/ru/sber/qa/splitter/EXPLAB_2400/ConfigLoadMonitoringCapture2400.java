package ru.sber.qa.splitter.EXPLAB_2400;

import dto.splitter.config.LoadConfigResponseDto;
import dto.splitter.monitoring.SplitterConfigLoadMonitoringDto;

record ConfigLoadMonitoringCapture2400(
        LoadConfigResponseDto response,
        SplitterConfigLoadMonitoringDto event,
        long startedAtEpochMillis
) {
}
