package ru.sber.qa.examples.dictionaries;


import dto.dictionaries.response.DictionarySplittingPointsRespDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.common.mapper.TypeRef;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import request.experiment.CreateExperimentRequestFactory;
import ru.sber.qa.config.ApiEnvironmentConfiguration;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;



@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class DictionariesGetSplittingPointTemplates {


    String urlIFT = "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru";
     String urlDEV= "https://ingress-v2.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru";
    String url= urlDEV;

    private final CreateExperimentRequestFactory factory = new CreateExperimentRequestFactory();


    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                    .keyStore("src/test/resources/keystore.p12", "V_at_platform_2025")
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );

    /** Общее хранилище для id между тестами. */
    public static class SharedState {
        public static Long experimentId;
    }


    @Test
    @DisplayName("Получить справочник точек сплиттования")
    void testChangeStatusExperimentById(ru.sber.qa.services.rest.RestService restService) {

        List<DictionarySplittingPointsRespDto> expected = List.of(
                DictionarySplittingPointsRespDto.builder().id(1L).code("MAPPER").name("Маппер").build(),
                DictionarySplittingPointsRespDto.builder().id(2L).code("OPTIMIZER").name("Оптимизатор").build(),
                DictionarySplittingPointsRespDto.builder().id(3L).code("REACTION_SERV").name("Сервис реакций").build()
        );

        var response = restService.restClient()
                .get(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*"),
                        url + "/api/v1/dictionaries/splitting-points")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));

        List<DictionarySplittingPointsRespDto> actual =response.toResponse().as(new TypeRef<List<DictionarySplittingPointsRespDto>>() {});

        // 3. Сравнение
        assertThat(actual).isEqualTo(expected);
        // Если порядок не гарантирован, используй:
        // assertThat(actual)
        //     .usingRecursiveFieldByFieldElementComparator()
        //     .containsExactlyInAnyOrderElementsOf(expected);
    }





}
