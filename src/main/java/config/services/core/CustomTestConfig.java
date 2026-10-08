package config.services.core;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Reloadable;

// Preserve secret references for SecurePropertyResolver instead of expanding them in Owner.
@Config.DisableFeature(Config.DisableableFeature.VARIABLE_EXPANSION)
@LoadPolicy(Config.LoadType.MERGE)
@Sources({
        "file:src/test/resources/test.properties",
        "classpath:test.properties"
})
public interface CustomTestConfig extends Reloadable {
    @Key("env")
    String env();


    @Key("keystore.pass")
    String keystorePass();

    @Key("truststore.pass")
    String truststorePass();

    @Key("rest.configuration-service.token")
    String configurationServiceToken();

    @Key("rest.explab-gateway.token")
    String explabGatewayToken();
}
