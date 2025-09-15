package ru.sber.qa.config.properties;

import org.aeonbits.owner.ConfigFactory;

public interface TestConfigScope  {

    TestConfig TEST_CONFIG = ConfigFactory.create(TestConfig.class);

}
