package ru.sber.qa.dictionaries;

import ru.sber.qa.allure.CriticalRegression;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.environment.EnvironmentConfigurationExample;
import dto.dictionaries.response.DictionarySplittingPointsRespDto;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import net.javacrumbs.jsonunit.core.internal.Options;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.JsonMatchers;

import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;


@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class DictionariesGetSplittingPointTemplates extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("Получить справочник точек сплиттования")
    void testDictionariesGetSplittingPoint() throws JsonProcessingException {

      DictionarySplittingPointsRespDto[] respDto = {
              DictionarySplittingPointsRespDto.builder()
                      .id(1L)
                      .code("MAPPER")
                      .name("Маппер")
                      .build(),
              DictionarySplittingPointsRespDto.builder()
                      .id(2L)
                      .code("OPTIMIZER")
                      .name("Оптимизатор")
                      .build(),
              DictionarySplittingPointsRespDto.builder()
                      .id(3L)
                      .code("REACTION_SERV")
                      .name("Сервис реакций")
                      .build()
      };

      ObjectMapper mapper = new ObjectMapper();
      String expectedJson = mapper.writeValueAsString(respDto);


        getFlowWithDbRest().flow().restClient()
                .get(spec -> spec,
                        "/api/v1/dictionaries/splitting-points")
                .should(
                        haveStatusCode(HttpStatus.SC_OK))
               .toValidatableJson().should(
                       JsonMatchers.beEqualToJson(expectedJson, new Options(IGNORING_ARRAY_ORDER
                               //, IGNORING_EXTRA_ARRAY_ITEMS
                       ))
               );
    }


    @CriticalRegression
    @Test
    @DisplayName("Получить справочник точек сплиттования")
    void testDictionariesGetSplittingPointTemplates() throws JsonProcessingException {

        DictionarySplittingPointsRespDto[] respDto = {
                DictionarySplittingPointsRespDto.builder()
                        .id(1L)
                        .code("MAPPER")
                        .name("Маппер")
                        .build(),
                DictionarySplittingPointsRespDto.builder()
                        .id(2L)
                        .code("OPTIMIZER")
                        .name("Оптимизатор")
                        .build(),
                DictionarySplittingPointsRespDto.builder()
                        .id(3L)
                        .code("REACTION_SERV")
                        .name("Сервис реакций")
                        .build()
        };

        ObjectMapper mapper = new ObjectMapper();
        String expectedJson = mapper.writeValueAsString(respDto);


        getFlowWithDbRest().flow().restClient()
                .get(spec -> spec,
                        "/api/v2/dictionaries/splitting-points")
                .should(
                        haveStatusCode(HttpStatus.SC_OK))
                .toValidatableJson().should(
                        JsonMatchers.beEqualToJson(expectedJson, new Options(IGNORING_ARRAY_ORDER
                                //, IGNORING_EXTRA_ARRAY_ITEMS
                        ))
                );
    }
}
