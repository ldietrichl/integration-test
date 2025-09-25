package ru.sber.qa.examples.dictionaries;

import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.perfeccionista.framework.Environment;               // ✅ правильный импорт
import request.dictionaries.DictionariesParams;
import request.dictionaries.DictionariesRequestFactory;
import ru.sber.qa.config.ApiEnvironmentConfiguration;       // ✅ твой конфиг
import ru.sber.qa.services.db.DatabaseService;
import ru.sber.qa.services.db.DefaultDatabaseServiceConfiguration;
import ru.sber.qa.services.rest.RestService;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

class DictionariesPostOperatorsParametrized {

    private static final String DB_NAME = "dev_explab"; // ключ из database.properties
    private static DatabaseService dbService;
    private static RestService restService;
    private static Environment env;

    String urlIFT = "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru";
    String urlDEV= "https://ingress-v2.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru";
    String url= urlDEV;



    private final DictionariesRequestFactory factory = new DictionariesRequestFactory();

    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                    .keyStore("src/test/resources/keystore.p12", "V_at_platform_2025")
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );


    @BeforeAll
    static void beforeAll() {
        env = Environment.createForCurrentThread(new ApiEnvironmentConfiguration());
        env.init();

        //dbService = new DatabaseService();

        dbService = env.getService(DatabaseService.class);
        restService = env.getService(RestService.class);
    }

    /** Источник параметров: все коды из таблицы operator_dict */
    static Stream<Arguments> operatorCodes() {
        var table = dbService.dataBaseClient(DB_NAME)
                .executeSelect("select code from dictionaries.operator_dict od");
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

    @BeforeEach
    void setUpEach() {
        env.beforeTest();
    }

    @AfterEach
    void setDownEach() {
        env.afterTest();
    }

    @AfterAll
    static void afterAll() {
        env.shutdown();
    }

    @DisplayName("Справочник operators (каждый код)")
    @ParameterizedTest(name = "{index}) code={0}")
    @MethodSource("operatorCodes")
    void getOperators_bySin(String code) {
        Assertions.assertNotNull(code);


        var params = DictionariesParams.builder()
                .operatorCodes(List.of(code))
                .build();

        var dto = factory.buildOperatorsDto(params);

        restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*")
                                .body(dto),
                        url+"/api/v1/dictionaries/operators")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }
    }

