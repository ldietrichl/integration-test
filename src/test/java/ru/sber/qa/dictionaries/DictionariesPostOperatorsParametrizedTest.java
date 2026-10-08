package ru.sber.qa.dictionaries;

import config.environment.EnvironmentConfigWithDbRest;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import request.dictionaries.DictionariesParams;
import request.dictionaries.DictionariesRequestFactory;
import ru.sber.qa.services.db.validation.ValidatableTable;
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithDbRest.class)
public class DictionariesPostOperatorsParametrizedTest extends Flows {
    private final DictionariesRequestFactory factory = new DictionariesRequestFactory();

    //todo: добавить тест с csvsource а текущий оставить для примера и задейзейблить
    //данные для сорса: equal, not_equal, more, less, more_equal, less_equal, is_null, is_not_null, in, not_in, like, not_like, like_any, not_like_any, like_all

    @DisplayName("Справочник operators (каждый код)")
    @CriticalRegression
    @ParameterizedTest(name = "{index}) code={0}")
    @MethodSource("operatorCodes")
    public void getOperatorsBySinTest(String code) {
        Assertions.assertNotNull(code);

        var params = DictionariesParams.builder()
                .operatorCodes(List.of(code))
                .build();

        var dto = factory.buildOperatorsDto(params);

        getFlowWithDbRest().flow().restClient()
                .post(spec -> spec
                                .body(dto),
                        "/api/v2/dictionaries/operators")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }

    /**
     * Источник параметров: все коды из таблицы operator_dict
     */
    static Stream<Arguments> operatorCodes() {
        ValidatableTable table = getFlowWithDb().flow().dbExpLabManuallyStartClient(dbClient ->
                dbClient.executeSelect("""
                        SELECT code
                        FROM dictionaries.operator_dict od""")
        );

        List<Map<String, Object>> rows = table.toSimpleTable();

        return rows.stream()
                .map(r -> r.get("code"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .map(Arguments::of);
    }
}
