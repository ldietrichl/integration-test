package config.services.db;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Reloadable;
import ru.sber.qa.services.configuration.converters.SecretPropertyConverter;

/**
 * Информация об используемой БД из заданного массива в конфигурационном файле указанном в аннотации {@link Sources}
 * <p> Инициализация конфига для конкретной БД:
 * <p> {@code DbConfig dbConfig = ConfigFactory.create(DbConfig.class, Map.of("dbName", dbName));}
 * <p> где параметр dbName определяет какой элемент массива будет выбран
 * <p>
 * Пример:
 * <p> в файле указаны следующие параметры
 * <p> db.example_var1.login=loginVar1
 * <p> db.example_var1.pass=passVar1
 * <p> db.example_var1.url=urlVar1
 * <p> db.example.var2.login=loginVar2
 * <p> db.example.var2.pass=passVar2
 * <p> db.example.var2.url=urlVar2
 * <p> Инициализация в коде:
 * <p>{@code DbConfig exampleVar1Config = ConfigFactory.create(DbConfig.class, Map.of("dbName", "example_var1"));
 * <p> DbConfig exampleVar2Config = ConfigFactory.create(DbConfig.class, Map.of("dbName", "example.var2"));}
 *
 * @see <a href="https://matteobaccan.github.io/owner/docs/variables-expansion">Документация Owner</a>
 */
@LoadPolicy(Config.LoadType.MERGE)
@Sources({
        "system:env",
        "system:properties",
        "file:secure.local.override.properties",
        "file:secure.local.properties",
        "classpath:database.properties",
        "classpath:config/database.properties"
})
public interface CustomDatabaseConfig extends Reloadable {
    @DefaultValue("")
    String env();

    @DefaultValue("")
    String name();

    @Key("db.${env}.${name}.url")
    String url();

    @Key("db.${env}.${name}.login")
    @ConverterClass(SecretPropertyConverter.class)
    String login();

    @Key("db.${env}.${name}.password")
    @ConverterClass(SecretPropertyConverter.class)
    String password();

    @DefaultValue("60")
    @Key("db.${env}.${name}.timeout.in.seconds")
    String timeoutInSeconds();

    @DefaultValue("1")
    @Key("db.${env}.${name}.connection.pool.size")
    int connectionPoolSize();
}
