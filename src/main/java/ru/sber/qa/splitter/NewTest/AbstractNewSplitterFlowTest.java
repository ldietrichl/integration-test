package ru.sber.qa.splitter.NewTest;

import flow.Flows;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.BeforeEach;
import util.support.SplitterRuntimeMetadata;

public abstract class AbstractNewSplitterFlowTest extends Flows {

    @BeforeEach
    void writeSplitterRuntimeMetadataToAllure() {
        Allure.parameter("splitter.environment", SplitterRuntimeMetadata.environment());
        Allure.parameter("splitter.url", SplitterRuntimeMetadata.splitterBaseUri());
        Allure.parameter("splitter.version", SplitterRuntimeMetadata.version());
        Allure.parameter("splitter.versionUrl", SplitterRuntimeMetadata.versionUrl());
        Allure.parameter("splitter.configUrl", SplitterRuntimeMetadata.configUrl());
        Allure.parameter("splitter.splitUrl", SplitterRuntimeMetadata.splitUrl());
        Allure.parameter("splitter.precalculateUrl", SplitterRuntimeMetadata.precalculateUrl());
    }
}
