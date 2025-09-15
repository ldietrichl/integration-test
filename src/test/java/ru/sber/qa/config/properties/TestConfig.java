package ru.sber.qa.config.properties;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Reloadable;
import ru.sber.qa.services.configuration.converters.SecretPropertyConverter;

@LoadPolicy(Config.LoadType.MERGE)
@Sources({
        // пути к файлам с проперти
        "system:properties",
        "classpath:test.properties",
        "classpath:config/test.properties"
})
public interface TestConfig extends Reloadable {

    // ключ в файле properties
    @Key("keystore.pass")
    // указание конвертера для дешифровки
    @ConverterClass(SecretPropertyConverter.class)
    String keystorePass();

    // ключ в файле properties
    @Key("truststore.pass")
    // указание конвертера для дешифровки
    @ConverterClass(SecretPropertyConverter.class)
    String truststorePass();

}