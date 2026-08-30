package config.services.core;

import org.aeonbits.owner.ConfigFactory;

public interface SecureLocalConfigScope {
    SecureLocalConfig SECURE_LOCAL_CONFIG = ConfigFactory.create(SecureLocalConfig.class);
}
