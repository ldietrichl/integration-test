package ru.sber.qa.allure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportExclusionsSelectionTest {
    @TempDir Path project;

    @Test
    void directIdeaLaunchDoesNotReadPreviousGradleExclusions() throws Exception {
        previousGradleRun();
        assertEquals(Set.of(), RequiredAllureLabelsExtension.loadExcludedTestNames(project, null));
    }

    @Test
    void explicitStageExclusionsAreNotCombinedWithAnotherStage() throws Exception {
        previousGradleRun();
        Files.writeString(project.resolve("current-stage.txt"), "# selected stage\ncurrent.Excluded.method\n");
        assertEquals(Set.of("current.Excluded.method"),
                RequiredAllureLabelsExtension.loadExcludedTestNames(project, "current-stage.txt"));
    }

    @Test
    void missingExplicitFileDoesNotFallBackToPreviousGradleRun() throws Exception {
        previousGradleRun();
        assertThrows(IllegalStateException.class,
                () -> RequiredAllureLabelsExtension.loadExcludedTestNames(project, "missing-stage.txt"));
    }

    private void previousGradleRun() throws Exception {
        Path stale = project.resolve("build/report-eligibility/excluded-tests.txt");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "previous.Excluded.method\n");
    }
}
