package config.services.core;

import java.util.Properties;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.configuration.scope.ConfigScope;

public class SecureAwareConfigurationService extends ConfigurationService {

    @Override
    public Properties getProperties(ConfigScope configScope) {
        return SecurePropertyResolver.resolve(super.getProperties(configScope));
    }
}
