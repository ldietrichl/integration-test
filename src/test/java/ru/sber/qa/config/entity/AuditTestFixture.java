package ru.sber.qa.config.entity;

import io.perfeccionista.framework.Environment;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.Filter;
import io.restassured.http.Cookies;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import ru.sber.qa.services.audit.AuditService;
import ru.sber.qa.services.audit.DefaultAuditServiceConfiguration;
import ru.sber.qa.services.pvm.UnofficialPvmAbyssClient;
import ru.sber.qa.services.pvm.api.unofficial.UnofficialPvmAbyssApi;
import ru.sber.qa.services.pvm.api.unofficial.UnofficialPvmAbyssClientConfiguration;
import ru.sber.qa.services.pvm.api.unofficial.dto.InternalSourceKafkaTopicsFetchRq;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static ru.sber.qa.services.pvm.api.unofficial.UnofficialPvmAbyssClientConfiguration.unofficialPvmAbyssClientConfiguration;

/**
 * @deprecated Данный класс необходим для подготовки окружения демонстрационных тестов и неприменим в реальных тестах.
 */
public class AuditTestFixture {
    private MockedStatic<RestAssured> restAssuredMockedStatic;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        //region Подготовка заглушки DefaultAuditServiceConfiguration
        DefaultAuditServiceConfiguration defaultAuditServiceConfigurationMock = mock(DefaultAuditServiceConfiguration.class);
        UnofficialPvmAbyssApi unofficialPvmAbyssApi = new UnofficialPvmAbyssApi("http://localhost",
                "/test", new Cookies());
        UnofficialPvmAbyssClientConfiguration configuration =
                unofficialPvmAbyssClientConfiguration(unofficialPvmAbyssApi, "1234",
                        1, 1000, InternalSourceKafkaTopicsFetchRq.OffsetSort.LATEST);
        when(defaultAuditServiceConfigurationMock.getUnofficialPvmAbyssClient(any(), any(String.class)))
                .thenReturn(new UnofficialPvmAbyssClient(configuration));
        //endregion

        //region Замена конфигурации сервиса через рефлексию.
        AuditService auditService = Environment.getForCurrentThread().getService(AuditService.class);
        Field field = auditService.getClass().getDeclaredField("serviceConfiguration");
        field.setAccessible(true);
        field.set(auditService, defaultAuditServiceConfigurationMock);
        //endregion

        //region Подготовка заглушки ответа
        AuditTestData auditTestData = new AuditTestData();
        String jsonForReturnInMock = auditTestData.getRespose();
        //endregion

        //region Подготовка заглушки Response
        Response responseMock = mock(Response.class);
        when(responseMock.statusCode()).thenReturn(200);
        when(responseMock.jsonPath()).thenReturn(JsonPath.from(jsonForReturnInMock));
        //endregion

        //region Подготовка статичной заглушки RestAssured
        restAssuredMockedStatic = mockStatic(RestAssured.class);

        restAssuredMockedStatic.when(() -> RestAssured.config())
                .thenAnswer((Answer<RestAssuredConfig>) invocation -> {
                    return new RestAssuredConfig();
                });
        restAssuredMockedStatic.when(() -> RestAssured.given())
                .thenAnswer((Answer<RequestSpecification>) invocation -> {
                    //region Подготовка заглушки RequestSpecification
                    RequestSpecification requestSpecificationMock = mock(RequestSpecification.class);
                    when(requestSpecificationMock.post(any(String.class), any(Object[].class))).thenReturn(responseMock);
                    when(requestSpecificationMock.spec(any(RequestSpecification.class))).thenReturn(requestSpecificationMock);
                    when(requestSpecificationMock.cookies(any(Cookies.class))).thenReturn(requestSpecificationMock);
                    when(requestSpecificationMock.contentType(any(String.class))).thenReturn(requestSpecificationMock);
                    when(requestSpecificationMock.body(any(Object.class))).thenReturn(requestSpecificationMock);
                    when(requestSpecificationMock.baseUri(any(String.class))).thenReturn(requestSpecificationMock);
                    when(requestSpecificationMock.basePath(any(String.class))).thenReturn(requestSpecificationMock);
                    //endregion
                    return requestSpecificationMock;
                });
        restAssuredMockedStatic.when(() -> RestAssured.filters())
                .thenAnswer((Answer<List<Filter>>) invocation -> {
                    return new ArrayList<>();
                });
        //endregion
    }

    @AfterEach
    void tearDown() {
        restAssuredMockedStatic.close();
    }
}
