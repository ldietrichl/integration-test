package config.services.core;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Reloadable;
import ru.sber.qa.services.configuration.converters.SecretPropertyConverter;

@LoadPolicy(Config.LoadType.MERGE)
@Sources("classpath:test.properties")
public interface CustomTestConfig extends Reloadable {
    @Key("env")
    String env();


    @Key("keystore.pass")
    @ConverterClass(SecretPropertyConverter.class)
    String keystorePass();

    @Key("truststore.pass")
    @ConverterClass(SecretPropertyConverter.class)
    String truststorePass();

    @Key("rest.configuration-service.token")
    String configurationServiceToken();

    @Key("rest.explab-gateway.token")
    String explabGatewayToken();
}
