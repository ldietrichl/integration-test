package ru.sber.qa.splitter.EXLAB_2891;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;
import steps.flow.splitter.mapper.MapperFreshSteps;

import config.environment.special.EnvironmentConfigWithMapperPrecalc;
import util.splittercheck.PrecalcResponseAssertions;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.splitter.support.RestConfigLoadModeOnly;
import static org.junit.jupiter.api.Assertions.*;

@SetEnvironmentConfiguration(EnvironmentConfigWithMapperPrecalc.class)
@RestConfigLoadModeOnly
@EnabledIfSystemProperty(named = "exlab2891.fresh.enabled", matches = "true")
@DisplayName("EXLAB-2891. MAPPER: чистый старт и дубликаты до загрузки конфигурации")
public class SplitterMapperFresh2891FlowTest extends MapperFreshSteps {

    @Test @CriticalRegression
    @DisplayName("EXLAB-2891-T15/T18. Дубликаты без конфигурации; связи появляются после её загрузки")
    void duplicateBeforeBusinessConfig() {
        assertEquals("rest", System.getProperty("splitter.config.load.mode", "rest"), "Чистый старт требует REST-профиля");
        scenario("После перезапуска принять дубликат и загрузить конфигурацию", f -> {
            var object = preObject("FRESH", "gold");
            var result = calculate(f, pre(101, object, object.deepCopy(), preObject("CONTROL", "gold")));
            // These counters also detect interference from an already loaded business configuration.
            PrecalcResponseAssertions.numberEquals(result.path("counter"), "totalExps", 0);
            PrecalcResponseAssertions.numberEquals(result.path("counter"), "totalObjects", 2);
            PrecalcResponseAssertions.numberEquals(result.path("counter"), "objectsAdded", 2);
            load(f, exp(101, 1));
            // First prove that the loaded experiment matches uncached input.
            assertMainA(splitOne(f, "DYNAMIC", "gold"), 101);
            var control = splitOne(f, "CONTROL", "silver");
            var duplicate = splitOne(f, "FRESH", "silver");
            assertAll("Актуализация сохранённых объектов после загрузки конфигурации",
                    () -> assertMainA(control, 101), () -> assertMainA(duplicate, 101));
        });
    }
}
