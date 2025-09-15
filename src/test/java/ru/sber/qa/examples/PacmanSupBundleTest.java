package ru.sber.qa.examples;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.services.pacman.PacmanService;
import ru.sber.qa.services.pacman.SupClient;
import ru.sber.qa.services.pacman.bundle.dto.BundleAttributeDto;
import ru.sber.qa.services.pacman.bundle.dto.BundleDto;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_ADD_GET_DELL_DATE_LIST_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_ADD_GET_DELL_STRING_LIST_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_ADD_GET_DELL_STRING_NO_LIST_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_GET_NULL_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParametersToAdd.TEST_ADD_GET_DELL_DATE_LIST_PARAMETER;
import static ru.sber.qa.config.services.pacman.TestSupParametersToAdd.TEST_ADD_GET_DELL_STRING_LIST_PARAMETER;
import static ru.sber.qa.config.services.pacman.TestSupParametersToAdd.TEST_ADD_GET_DELL_STRING_NO_LIST_PARAMETER;
import static ru.sber.qa.services.pacman.utils.PacmanUtils.formatBundleValueIfDateType;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class PacmanSupBundleTest {

    public static final BundleAttributeDto BUNDLE_ATTRIBUTE_4 = new BundleAttributeDto(null, "PLATFORM_RUNTIME_ENVIRONMENT", "OPENSHIFT");

    private static final Logger log = LoggerFactory.getLogger(PacmanSupBundleTest.class);

    @Test
    void getSupBundlesTest(PacmanService pacmanService) {
        SupClient supClient = pacmanService.supClient();

        log.info("-----------------------------------------");
        log.info("getSupBundleTest");

        //получаем все значения Bundle параметра по фильтру
        List<BundleDto> values = supClient
                .getParameter(TEST_GET_NULL_PARAMETER_FILTER)
                .getBundles();

        values.forEach(value -> log.info("value: id = {}, active = {}, groupCode = {}, createDate = {}, startDate = {}, path = {}, values = {}, status = {}",
                value.id(), value.active(), value.groupCode(), value.createDate(),
                value.startDate(), value.path(), value.values(), value.status()));


        //получаем 1 конкретное значение Bundle параметра по атрибутам
        BundleDto value = supClient
                .getParameter(TEST_GET_NULL_PARAMETER_FILTER)
                .getBundle(List.of());
        //в данном случае задан пустой лист(аттрибуты могут быть либо заполнены, либо,
        // если их нет, то обязательно передаем пустой список

        log.info("value: id = {}, active = {}, groupCode = {}, createDate = {}, startDate = {}, path = {}, values = {}, status = {}",
                value.id(), value.active(), value.groupCode(), value.createDate(),
                value.startDate(), value.path(), value.values(), value.status());

        Assertions.assertEquals(value.values(), List.of("https://mm-core-node3.iftonline.sberbank.ru:9443"), "Values are not equal");
    }

    @Test
    void addNewBundleToExistingParameterTest(PacmanService pacmanService) {
        SupClient supClient = pacmanService.supClient();

        log.info("-----------------------------------------");
        log.info("Add bundle to existing Parameter");

        // Добавляем значение уже существующему параметру
        supClient.getParameter(TEST_GET_NULL_PARAMETER_FILTER)
                .setBundleValue(List.of(BUNDLE_ATTRIBUTE_4), "testString1");

        BundleDto value = supClient
                .getParameter(TEST_GET_NULL_PARAMETER_FILTER)
                .getBundle(List.of(BUNDLE_ATTRIBUTE_4));

        log.info("value: id = {}, active = {}, groupCode = {}, createDate = {}, startDate = {}, path = {}, values = {}, status = {}",
                value.id(), value.active(), value.groupCode(), value.createDate(),
                value.startDate(), value.path(), value.values(), value.status());

        Assertions.assertEquals(value.values(), List.of("testString1"), "Values are not equal");

        //после окончания теста все изменения возвращаются в исходное состояние, удаляются добавленные СУПы, бандлы возвращаются в исходное состояние
    }

    @Test
    void addNewParameterAndNewBundleTest(PacmanService pacmanService) {
        SupClient supClient = pacmanService.supClient();

        log.info("-----------------------------------------");
        log.info("Add new Parameter and New Bundle");

//        Добавляем новый параметр + добавляем значение на лету
        supClient.addParameter(TEST_ADD_GET_DELL_STRING_NO_LIST_PARAMETER)
                .setBundleValue(List.of(BUNDLE_ATTRIBUTE_4), "testString1");

        BundleDto value = supClient
                .getParameter(TEST_ADD_GET_DELL_STRING_NO_LIST_PARAMETER_FILTER)
                .getBundle(List.of(BUNDLE_ATTRIBUTE_4));

        log.info("value: id = {}, active = {}, groupCode = {}, createDate = {}, startDate = {}, path = {}, values = {}, status = {}",
                value.id(), value.active(), value.groupCode(), value.createDate(),
                value.startDate(), value.path(), value.values(), value.status());

        Assertions.assertEquals(value.values(), List.of("testString1"), "Values are not equal");

        //после окончания теста все изменения возвращаются в исходное состояние, удаляются добавленные СУПы, бандлы возвращаются в исходное состояние
    }

    public static final BundleAttributeDto BUNDLE_ATTRIBUTE_2 = new BundleAttributeDto(null, BundleAttributeDto.CHANNEL, "ch");
    public static final BundleAttributeDto BUNDLE_ATTRIBUTE_3 = new BundleAttributeDto(null, BundleAttributeDto.SUBSYSTEM, "sub");

    @Test
    void addSeveralSupBundlesTest(PacmanService pacmanService) {
        SupClient supClient = pacmanService.supClient();

        List<LocalDate> listLocalDate = Arrays.asList(LocalDate.of(2019, 1, 1), LocalDate.now());

        log.info("-----------------------------------------");
        log.info("Add new Parameters and Set Bundles");

        supClient.addParameter(TEST_ADD_GET_DELL_STRING_LIST_PARAMETER)
                .setBundleValue(List.of(BUNDLE_ATTRIBUTE_2, BUNDLE_ATTRIBUTE_3), Arrays.asList("testString1", "testString2", "testString3"));

        supClient.addParameter(TEST_ADD_GET_DELL_DATE_LIST_PARAMETER)
                .setBundleValue(List.of(), formatBundleValueIfDateType(listLocalDate));

        log.info("-----------------------------------------");
        log.info("Get Bundle value");

        BundleDto value = supClient.getParameter(TEST_ADD_GET_DELL_STRING_LIST_PARAMETER_FILTER)
                .getBundle(List.of(BUNDLE_ATTRIBUTE_2, BUNDLE_ATTRIBUTE_3));

        log.info("value: id = {}, active = {}, groupCode = {}, createDate = {}, startDate = {}, path = {}, values = {}, status = {}",
                value.id(), value.active(), value.groupCode(), value.createDate(),
                value.startDate(), value.path(), value.values(), value.status());

        Assertions.assertEquals(value.values(), Arrays.asList("testString1", "testString2", "testString3"), "Values are not equal");

        value = supClient.getParameter(TEST_ADD_GET_DELL_DATE_LIST_PARAMETER_FILTER)
                .getBundle(List.of());

        log.info("value: id = {}, active = {}, groupCode = {}, createDate = {}, startDate = {}, path = {}, values = {}, status = {}",
                value.id(), value.active(), value.groupCode(), value.createDate(),
                value.startDate(), value.path(), value.values(), value.status());

        Assertions.assertEquals(value.values(), formatBundleValueIfDateType(listLocalDate), "Values are not equal");
    }

    @Test
    void typicalScenarioExampleTest(PacmanService pacmanService) {
        //устанавливаем новое значение параметра для существующего СУПа
        pacmanService.supClient().getParameter(TEST_GET_NULL_PARAMETER_FILTER)
                .setBundleValue(List.of(BUNDLE_ATTRIBUTE_4), "testString1");

        //далее логика теста, совершаем перевод, оформляем полис, выставляем настройки, выполняем проверки и т.д.

        //после окончания теста все изменения возвращаются в исходное состояние, удаляются добавленные СУПы, бандлы возвращаются в исходное состояние
    }

}
