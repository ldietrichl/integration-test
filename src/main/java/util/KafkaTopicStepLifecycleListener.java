package util;

import io.qameta.allure.listener.StepLifecycleListener;
import io.qameta.allure.model.StepResult;

public final class KafkaTopicStepLifecycleListener implements StepLifecycleListener {

    @Override
    public void beforeStepStart(StepResult result) {
        if (result == null) {
            return;
        }
        result.setName(KafkaAllureLog.rewriteFrameworkStepName(result.getName()));
    }

    @Override
    public void afterStepStop(StepResult result) {
        if (result == null) {
            return;
        }
        KafkaAllureLog.clearLegacyTopicContextAfterStep(result.getName());
    }
}
