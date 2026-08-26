package config.environment.special;

import config.services.db.CustomDatabaseServiceConfiguration;
import io.perfeccionista.framework.DefaultEnvironmentConfiguration;
import io.perfeccionista.framework.service.ConfiguredServiceHolder;
import io.perfeccionista.framework.service.ServiceConfigurationManager;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.services.db.DatabaseService;


/**
 * Конфигурационный класс окружения, предназначенный для использования вне тестов,
 * например, при подготовке тестовой среды.
 *
 * <p>В отличие от стандартных конфигураций, этот класс НЕ включает служебные сервисы,
 * используемые только внутри тестов. Вместо этого он предоставляет возможность
 * зарегистрировать специализированные сервисы, такие как {@link DatabaseService},
 * необходимые для подготовки и настройки окружения.</p>
 */
public class EnvironmentSpecialConfigWithDb extends DefaultEnvironmentConfiguration {

    @Override
    public @NotNull ServiceConfigurationManager getServiceConfigurations() {
        return super.getServiceConfigurations()
                // Служебные сервисы (не добавляются намеренно, так как не нужны вне тестов)

                // Тестовые сервисы
                .put(ConfiguredServiceHolder.of(DatabaseService.class, new CustomDatabaseServiceConfiguration()));
    }
}
