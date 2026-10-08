package flow;

import config.services.rest.SchedulerProbeRestService;
import infrastructure.kubernetes.KubernetesTunnelService;
import infrastructure.kubernetes.TunnelSession;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.containers.flow.ContainerServiceFlow;
import ru.sber.qa.flow.Flow;
import steps.container.KubernetesTunnelSteps;
import steps.rest.scheduler.SchedulerSteps;

/** Keeps framework DB, mTLS REST and ContainerService in the same project flow. */
public final class SchedulerInfrastructureFlow implements Flow, RestCustomFlow, DbCustomFlow, ContainerServiceFlow, WorkloadFlow {
    public KubernetesTunnelSteps infrastructureSteps() {
        return new KubernetesTunnelSteps(Environment.getForCurrentThread().getService(KubernetesTunnelService.class));
    }

    public steps.rest.scheduler.SchedulerRegistrySteps schedulerRegistrySteps(
            TunnelSession scheduler, TunnelSession dictionary) {
        if (!dictionary.isAlive()) throw new IllegalStateException("The owned dictionary tunnel is not alive");
        return new steps.rest.scheduler.SchedulerRegistrySteps(
                schedulerSteps(scheduler),
                new steps.rest.dictionaries.v1.DictionariesV1Steps(probeRestClient(), dictionary.baseUri()));
    }

    public SchedulerSteps schedulerSteps(TunnelSession session) {
        if (!session.isAlive()) throw new IllegalStateException("The owned tunnel is not alive");
        return new SchedulerSteps(probeRestClient(), session.baseUri());
    }

    private ru.sber.qa.services.rest.RestClient probeRestClient() {
        return Environment.getForCurrentThread().getService(SchedulerProbeRestService.class).restClient();
    }
}
