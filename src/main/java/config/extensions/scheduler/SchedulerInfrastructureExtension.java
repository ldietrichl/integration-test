package config.extensions.scheduler;

import config.services.container.KubeconfigContainerServiceConfiguration;
import infrastructure.kubernetes.KubernetesTunnelService;
import io.perfeccionista.framework.Environment;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import steps.container.KubernetesTunnelSteps;

/** Owns tunnel and Fabric8-client lifecycle without replacing a primary service/test failure. */
public final class SchedulerInfrastructureExtension implements BeforeEachCallback, AfterEachCallback {
    @Override public void beforeEach(ExtensionContext context) {
        if (Environment.existForCurrentThread())
            Environment.getForCurrentThread().getOptionalService(KubernetesTunnelService.class)
                    .ifPresent(service -> new KubernetesTunnelSteps(service).beginScenario(context.getUniqueId()));
    }

    @Override public void afterEach(ExtensionContext context) {
        Throwable lifecycleFailure = null;
        try {
            if (Environment.existForCurrentThread())
                Environment.getForCurrentThread().getOptionalService(KubernetesTunnelService.class).ifPresent(service -> {
                    KubernetesTunnelSteps steps = new KubernetesTunnelSteps(service);
                    try { steps.attachScenarioLogs(); } finally { steps.closeAll(); }
                });
        } catch (RuntimeException | Error failure) {
            lifecycleFailure = failure;
            throw failure;
        } finally {
            try {
                KubeconfigContainerServiceConfiguration.closeOwnedClients();
            } catch (RuntimeException closeFailure) {
                Throwable primary = lifecycleFailure != null
                        ? lifecycleFailure : context.getExecutionException().orElse(null);
                if (primary != null) {
                    primary.addSuppressed(closeFailure);
                    KubernetesTunnelSteps.evidence("Suppressed ContainerService cleanup failure",
                            java.util.Map.of("failureType", closeFailure.getClass().getName()));
                } else {
                    throw closeFailure;
                }
            }
        }
    }
}