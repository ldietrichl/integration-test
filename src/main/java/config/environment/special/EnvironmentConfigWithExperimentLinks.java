package config.environment.special;

import config.environment.EnvironmentConfigWithDbRest;
import config.services.core.ExperimentLinksKafkaConfiguration;
import io.perfeccionista.framework.service.ConfiguredServiceHolder;
import io.perfeccionista.framework.service.ServiceConfigurationManager;
import ru.sber.qa.services.kafka.KafkaService;

/** V1 mTLS, DB and workload services are inherited; only the Kafka observer profile is specialised. */
public final class EnvironmentConfigWithExperimentLinks extends EnvironmentConfigWithDbRest {
    @Override public ServiceConfigurationManager getServiceConfigurations() {
        return super.getServiceConfigurations()
                .put(ConfiguredServiceHolder.of(KafkaService.class, new ExperimentLinksKafkaConfiguration()));
    }
}
