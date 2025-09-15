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
import ru.sber.qa.services.pacman.SupParameter;
import ru.sber.qa.services.pacman.parameter.dto.ParameterDto;

import java.util.List;

import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_ADD_GET_BOOLEAN_LIST_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_ADD_GET_DELL_DATE_LIST_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_ADD_GET_DELL_STRING_LIST_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_ADD_GET_DOUBLE_NO_LIST_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_ADD_GET_LONG_NO_LIST_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_GET_NULL_PARAMETER_FILTER;
import static ru.sber.qa.config.services.pacman.TestSupParameterFilters.TEST_GET_NULL_PARAMETER_FILTER_SEVERAL_VALUES;
import static ru.sber.qa.config.services.pacman.TestSupParametersToAdd.TEST_ADD_GET_BOOLEAN_LIST_PARAMETER;
import static ru.sber.qa.config.services.pacman.TestSupParametersToAdd.TEST_ADD_GET_DELL_DATE_LIST_PARAMETER;
import static ru.sber.qa.config.services.pacman.TestSupParametersToAdd.TEST_ADD_GET_DELL_STRING_LIST_PARAMETER;
import static ru.sber.qa.config.services.pacman.TestSupParametersToAdd.TEST_ADD_GET_DOUBLE_NO_LIST_PARAMETER;
import static ru.sber.qa.config.services.pacman.TestSupParametersToAdd.TEST_ADD_GET_LONG_NO_LIST_PARAMETER;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class PacmanSupParameterTest {

    private static final Logger log = LoggerFactory.getLogger(PacmanSupParameterTest.class);

    @Test
    void getSupParamsTest(PacmanService pacmanService) {
        log.info("-----------------------------------------");
        log.info("getSupParamsTest");
        SupClient supClient = pacmanService.supClient();

        // Получаем список параметров(фильтр задан так, чтобы получить несколько)
        List<ParameterDto> values = supClient.getParameters(TEST_GET_NULL_PARAMETER_FILTER_SEVERAL_VALUES)
                .map(SupParameter::getParameterValue)
                .toList();

        values.forEach(v -> log.info("value: id = {}, scope = {}, name = {}, description = {}, type = {}, role = {}, list = {}, tenantCode = {}",
                v.id(), v.scopes(), v.name(), v.description(),
                v.type(), v.roles(), v.list(), v.tenantCode()));

        // Получаем 1 параметр и достаем его значение
        ParameterDto value = supClient.getParameter(TEST_GET_NULL_PARAMETER_FILTER)
                .getParameterValue();

        log.info("value: id = {}, scope = {}, name = {}, description = {}, type = {}, role = {}, list = {}, tenantCode = {}",
                value.id(), value.scopes(), value.name(), value.description(),
                value.type(), value.roles(), value.list(), value.tenantCode());
    }

    @Test
    void addSupParamTest(PacmanService pacmanService) {
        SupClient supClient = pacmanService.supClient();

        log.info("-----------------------------------------");
        log.info("Add SUP parameters");

        supClient.addParameter(TEST_ADD_GET_DELL_STRING_LIST_PARAMETER);
        supClient.addParameter(TEST_ADD_GET_LONG_NO_LIST_PARAMETER);
        supClient.addParameter(TEST_ADD_GET_DOUBLE_NO_LIST_PARAMETER);
        supClient.addParameter(TEST_ADD_GET_BOOLEAN_LIST_PARAMETER);
        supClient.addParameter(TEST_ADD_GET_DELL_DATE_LIST_PARAMETER);

        log.info("-----------------------------------------");
        log.info("Check if parameters were added");

        boolean exists = supClient.isParameterExist(TEST_ADD_GET_DELL_STRING_LIST_PARAMETER_FILTER);
        Assertions.assertTrue(exists, "Parameter exists, deletion failed");

        ParameterDto value = supClient.getParameter(TEST_ADD_GET_DELL_STRING_LIST_PARAMETER_FILTER)
                .getParameterValue();
        log.info("value: id = {}, scope = {}, name = {}, description = {}, type = {}, role = {}, list = {}, tenantCode = {}",
                value.id(), value.scopes(), value.name(), value.description(),
                value.type(), value.roles(), value.list(), value.tenantCode());

        value = supClient.getParameter(TEST_ADD_GET_LONG_NO_LIST_PARAMETER_FILTER)
                .getParameterValue();
        log.info("value: id = {}, scope = {}, name = {}, description = {}, type = {}, role = {}, list = {}, tenantCode = {}",
                value.id(), value.scopes(), value.name(), value.description(),
                value.type(), value.roles(), value.list(), value.tenantCode());

        value = supClient.getParameter(TEST_ADD_GET_DOUBLE_NO_LIST_PARAMETER_FILTER)
                .getParameterValue();
        log.info("value: id = {}, scope = {}, name = {}, description = {}, type = {}, role = {}, list = {}, tenantCode = {}",
                value.id(), value.scopes(), value.name(), value.description(),
                value.type(), value.roles(), value.list(), value.tenantCode());

        value = supClient.getParameter(TEST_ADD_GET_BOOLEAN_LIST_PARAMETER_FILTER)
                .getParameterValue();
        log.info("value: id = {}, scope = {}, name = {}, description = {}, type = {}, role = {}, list = {}, tenantCode = {}",
                value.id(), value.scopes(), value.name(), value.description(),
                value.type(), value.roles(), value.list(), value.tenantCode());

        value = supClient.getParameter(TEST_ADD_GET_DELL_DATE_LIST_PARAMETER_FILTER)
                .getParameterValue();
        log.info("value: id = {}, scope = {}, name = {}, description = {}, type = {}, role = {}, list = {}, tenantCode = {}",
                value.id(), value.scopes(), value.name(), value.description(),
                value.type(), value.roles(), value.list(), value.tenantCode());

        //  если раскомментировать, то можно наглядно посмотреть как происходит удаление(если явно не прописать - оно и так удалит все ранее добавленные параметры)
        //  после окончания теста все изменения возвращаются в исходное состояние, удаляются добавленные СУПы, добавляются удаленные
//        log.info("-----------------------------------------");
//        log.info("Delete parameters");
//        supClient.deleteParameter(TEST_ADD_GET_DELL_STRING_LIST_PARAMETER_FILTER);
//        supClient.deleteParameter(TEST_ADD_GET_LONG_NO_LIST_PARAMETER_FILTER);
//        supClient.deleteParameter(TEST_ADD_GET_DOUBLE_NO_LIST_PARAMETER_FILTER);
//        supClient.deleteParameter(TEST_ADD_GET_BOOLEAN_LIST_PARAMETER_FILTER);
//        supClient.deleteParameter(TEST_ADD_GET_DELL_DATE_LIST_PARAMETER_FILTER);

        // после окончания теста все изменения возвращаются в исходное состояние, удаляются добавленные СУПы, добавляются удаленные
    }

}
