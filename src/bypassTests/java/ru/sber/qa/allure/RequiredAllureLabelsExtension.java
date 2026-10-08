package ru.sber.qa.allure;

import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.TestResult;
import java.util.Set;

/** Registration metadata only; deliberately no functional listeners, credentials, connections or cleanup hooks. */
public final class RequiredAllureLabelsExtension implements TestLifecycleListener {
    @Override public void beforeTestWrite(TestResult result) {
        String env = System.getProperty("env", "");
        if (!Set.of("dev", "ift", "ift-dm", "lt", "local").contains(env))
            throw new IllegalStateException("Explicit registration environment is required");
        String stage = "ift-dm".equals(env) ? "ift" : "local".equals(env) ? "code" : env;
        CanonicalAllureMetadata.apply(result, env, stage, System.getProperty("splitter.config.load.mode", "rest"));
        result.getLabels().removeIf(label -> Set.of("executionMode", "registrationOnly", "outcomeKind").contains(label.getName()));
        result.getLabels().add(new Label().setName("executionMode").setValue("registration-only"));
        result.getLabels().add(new Label().setName("registrationOnly").setValue("true"));
        result.getLabels().add(new Label().setName("outcomeKind").setValue("NOT_EXECUTED_REGISTRATION"));
        result.setHistoryId("registration-only::" + result.getHistoryId());
        result.setDescription("Только регистрация описания. Сценарий не выполнялся; проверки сервиса, доступ к стенду и сбор ConfigMap не производились.\n\n"
                + (result.getDescription() == null ? "" : result.getDescription()));
    }
}
