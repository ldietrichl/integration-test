package ru.sber.qa.splitter.NewTest;

import flow.Flows;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.BeforeEach;
import util.support.SplitterRuntimeMetadata;

public abstract class AbstractNewSplitterFlowTest extends Flows {

    @BeforeEach
    void writeSplitterRuntimeMetadataToAllure() {
        Allure.addAttachment(
                "Splitter runtime metadata",
                "text/plain",
                SplitterRuntimeMetadata.summary(),
                ".txt");
    }
}
