package config.services.rest.config_service;

import io.perfeccionista.framework.service.DefaultServiceConfiguration;
import io.perfeccionista.framework.service.DefaultServiceOrder;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;
import ru.sber.qa.services.rest.RestService;

/**
 * Реализация сервиса для работы с RestAPI
 */
@DefaultServiceOrder(-90)
@DefaultServiceConfiguration(DefaultRestServiceConfiguration.class)
public class CustomRestService extends RestService {

}
