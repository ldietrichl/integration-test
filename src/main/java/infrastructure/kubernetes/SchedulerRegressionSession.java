package infrastructure.kubernetes;

import config.services.core.SchedulerSettings;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.rest.RestClient;
import steps.container.KubernetesTunnelSteps;
import steps.rest.dictionaries.v1.DictionariesV1Steps;
import steps.rest.scheduler.SchedulerRegistrySteps;
import steps.rest.scheduler.SchedulerSteps;

/** One scenario owns its routes. No global URI mutation, process adoption or request replay. */
public final class SchedulerRegressionSession implements AutoCloseable {
    private static final ThreadLocal<SchedulerRegressionSession> CURRENT = new ThreadLocal<>();
    private final KubernetesTunnelSteps infrastructure;
    private TunnelSession scheduler;
    private TunnelSession dictionary;
    private TunnelSession user;

    private SchedulerRegressionSession(KubernetesTunnelService service, String scenario) {
        infrastructure = new KubernetesTunnelSteps(service);
        infrastructure.beginScenario(scenario);
    }

    public static boolean enabled() {
        return config.services.container.KubernetesTunnelConfigScope.enabled()
                || new SchedulerSettings().tunnelEnabled();
    }

    public static SchedulerRegressionSession begin(String scenario) {
        if (!enabled()) return null;
        if (CURRENT.get() != null)
            throw new IllegalStateException("Previous scheduler scenario still owns a tunnel scope");
        SchedulerRegressionSession session = new SchedulerRegressionSession(
                Environment.getForCurrentThread().getService(KubernetesTunnelService.class), scenario);
        CURRENT.set(session);
        return session;
    }

    /** Called by the existing project REST step; opening stays lazy for DB-only preflight. */
    public static String schedulerEndpoint() {
        if (!new SchedulerSettings().tunnelEnabled()) return null;
        SchedulerRegressionSession session = CURRENT.get();
        if (session == null) {
            if (enabled())
                throw new IllegalStateException("Automatic scheduler routing requires AbstractSchedulerFlowTest lifecycle");
            return null;
        }
        if (session.scheduler == null) session.scheduler = session.infrastructure.openConfigured();
        requireAlive(session.scheduler, "Scheduler");
        return session.scheduler.baseUri();
    }

    public static SchedulerRegistrySteps registrySteps(RestClient client) {
        String schedulerUri = schedulerEndpoint();
        if (schedulerUri == null)
            return new SchedulerRegistrySteps(new SchedulerSteps(client), new DictionariesV1Steps(client));
        SchedulerRegressionSession session = CURRENT.get();
        if (session.dictionary == null) session.dictionary = session.infrastructure.openDictionaryCli();
        requireAlive(session.dictionary, "Dictionary");
        return new SchedulerRegistrySteps(new SchedulerSteps(client, schedulerUri),
                new DictionariesV1Steps(client, session.dictionary.baseUri()));
    }

    public static steps.rest.user.UserServiceRegressionSteps userSteps(RestClient client) {
        SchedulerRegressionSession session = CURRENT.get();
        if (session == null) {
            if (enabled())
                throw new IllegalStateException("Application identity lookup requires AbstractSchedulerFlowTest lifecycle");
            return new steps.rest.user.UserServiceRegressionSteps(client);
        }
        if (session.user == null) session.user = session.infrastructure.openUserCli();
        requireAlive(session.user, "User-service");
        return new steps.rest.user.UserServiceRegressionSteps(client, session.user.baseUri());
    }

    private static void requireAlive(TunnelSession session, String target) {
        if (!session.isAlive())
            throw new IllegalStateException(target + " tunnel was lost; stop the scenario without automatic replay");
    }

    public void attachScenarioLogs() { infrastructure.attachScenarioLogs(); }

    @Override
    public void close() {
        try { infrastructure.closeAll(); }
        finally { CURRENT.remove(); }
    }
}
