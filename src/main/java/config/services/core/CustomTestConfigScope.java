package config.services.core;

import org.aeonbits.owner.ConfigFactory;

public interface CustomTestConfigScope {
    CustomTestConfig TEST_CONFIG = ConfigFactory.create(CustomTestConfig.class);
}
