package steps.reporting;

import io.qameta.allure.Allure;
import java.util.function.Supplier;

/** Named business steps; no synthetic fixtures, status rewriting or replacement assertions. */
public final class ReportingSteps {
    private ReportingSteps() { }
    public static <T> T step(String name, Supplier<T> action) {
        if (Allure.getLifecycle().getCurrentTestCaseOrStep().isEmpty()) return action.get();
        return Allure.step(name, () -> action.get());
    }
    public static void step(String name, Runnable action) {
        if (Allure.getLifecycle().getCurrentTestCaseOrStep().isEmpty()) action.run();
        else Allure.step(name, () -> action.run());
    }
}
