package config.services.rest;

import io.perfeccionista.framework.service.DefaultServiceConfiguration;
import io.perfeccionista.framework.service.DefaultServiceOrder;
import ru.sber.qa.services.rest.RestService;

/** No ingress certificate: only explicit project-owned loopback diagnostic tunnels. */
@DefaultServiceOrder(-90)
@DefaultServiceConfiguration(SchedulerTunnelRestConfiguration.class)
public final class SchedulerProbeRestService extends RestService { }
