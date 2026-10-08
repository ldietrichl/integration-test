package config.services.rest;

import io.perfeccionista.framework.service.DefaultServiceConfiguration;
import io.perfeccionista.framework.service.DefaultServiceOrder;
import ru.sber.qa.services.rest.RestService;

/** Explicitly configured test-environment controller, separate from business-service credentials. */
@DefaultServiceOrder(-90)
@DefaultServiceConfiguration(StatusChange2972ControlConfiguration.class)
public final class StatusChange2972ControlService extends RestService { }
