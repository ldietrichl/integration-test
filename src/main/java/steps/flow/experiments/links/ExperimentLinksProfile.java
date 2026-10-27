package steps.flow.experiments.links;

import config.services.core.ExperimentLinksSettings;
import config.services.core.ExperimentLinksKafkaConfiguration;
import config.services.core.StandSettings;
import flow.WorkloadFlow;
import infrastructure.kubernetes.*;
import java.util.Set;
import io.qameta.allure.Allure;

/** Наблюдение существующих настроек; lease, диагностика и восстановление — общий Workload. */
public final class ExperimentLinksProfile implements WorkloadFlow {
    private final ExperimentLinksSettings settings;
    private boolean prepared;
    public ExperimentLinksProfile(ExperimentLinksSettings settings) { this.settings = settings; }
    public void prepare() { prepare(false); }
    public void prepare(boolean kafkaCase) {
        settings.requireIsolated();
        var ingress = WorkloadHttpReadinessProbe.ingressGet(settings.route("direct"), "/api/v1/experiments/formats");
        KubernetesWorkloadControl[] captured = new KubernetesWorkloadControl[1];
        workloadScenarioSteps().prepare(WorkloadTarget.from(new StandSettings(), "experiment"), session -> {
            captured[0] = session;
            var observed = workloadSteps().readConfiguredEnvironment(session, Set.of(
                    "EXPERIMENT_SERVICE_CJ_EXP_LINK_JOB_ENABLED",
                    "EXPERIMENT_SERVICE_UPDATE_LINKS_JOB_EXECUTION_PERIOD_SECONDS",
                    "EXPERIMENT_SERVICE_MARK_TO_STOP_JOB_EXECUTION_PERIOD_SECONDS"));
            if (!"true".equalsIgnoreCase(observed.get("EXPERIMENT_SERVICE_CJ_EXP_LINK_JOB_ENABLED")))
                throw new IllegalStateException("BLOCKED_ENVIRONMENT: обработчик связей должен быть уже включён");
            observedPositive(observed.get("EXPERIMENT_SERVICE_UPDATE_LINKS_JOB_EXECUTION_PERIOD_SECONDS"));
            observedPositive(observed.get("EXPERIMENT_SERVICE_MARK_TO_STOP_JOB_EXECUTION_PERIOD_SECONDS"));
            Allure.addAttachment("Сохраняемые настройки обработчика связей", "text/plain",
                    observed + "\nДанные ConfigMap и размеры чанка/страницы не меняются.\n"
                    + "Подробные события ограниченной задачи проверяются в Kafka, а не по отсутствующим env-привязкам.", ".txt");
            return session.environmentState(observed);
        }, () -> captured[0].serviceHttpSample(origin ->
                WorkloadHttpReadinessProbe.loopbackHealth(origin, settings.readinessPath())) && ingress.ready(),
                WorkloadScenarioEvidence.current(), false);
        prepared = true;
        if (kafkaCase) {
            String key = "EXPERIMENT_SERVICE_KAFKA_EXPLAB_BOOTSTRAP_SERVER";
            var observed = workloadSteps().readConfiguredEnvironment(captured[0], Set.of(key));
            var routing = ExperimentLinksKafkaConfiguration.requireProducerBootstrap(observed.get(key));
            Allure.addAttachment("Kafka ExpLab: профиль и configured producer текущего Ready workload", "text/plain",
                    routing.toString(), ".txt");
        }
    }
    private static int observedPositive(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,8}"))
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: необходим положительный числовой параметр из текущих bindings pod");
        return Integer.parseInt(value);
    }
    public void evidence() { if (prepared) workloadScenarioSteps().collectEvidence(); }
}
