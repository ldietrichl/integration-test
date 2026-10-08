package ru.sber.qa.user.regress;

import config.environment.special.EnvironmentConfigWithStatusChange2972;
import config.services.core.StatusChange2972Settings;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithStatusChange2972.class)
@ResourceLock("user-service-regression")
@Regression
class UserServiceRegressionFlowTest extends Flows {
    private static final String CONTROL_POINT_STATUS_UPDATE = "status_update_exp";
    private static final String DEFAULT_ROLE = "expCreator";
    private static final String BACKUP_ROLE = "spMAPPER";
    private static final String EXPECTED_SPLITTING_POINT = "MAPPER";

    private enum UserServiceImplementation {
        GECKO, FORK
    }

    private static UserServiceImplementation implementation(StatusChange2972Settings settings) {
        String value = settings.optional("launch-plan.users.implementation", "gecko").toLowerCase();
        if (value.contains("fork")) {
            return UserServiceImplementation.FORK;
        }
        return UserServiceImplementation.GECKO;
    }

    private static Map<String, Object> primaryUserPayload(StatusChange2972Settings settings) {
        String sub = settings.required("auth.primary.sub");
        String employeeId = settings.required("auth.primary.employee-id");
        String login = settings.required("auth.primary.login");
        String email = settings.required("auth.primary.email");

        return Map.of(
                "sub", sub,
                "employeeId", employeeId,
                "userName", login,
                "lastName", "Regression",
                "firstName", "Auto",
                "email", email,
                "splittingPoints", List.of(EXPECTED_SPLITTING_POINT),
                "roles", List.of(DEFAULT_ROLE, BACKUP_ROLE)
        );
    }

    private static void assumeGeckoImplementation(StatusChange2972Settings settings) {
        assumeTrue(implementation(settings).equals(UserServiceImplementation.GECKO),
                "Тест-кейсы user-regress GECKO выполняются только для старого контракта");
    }

    private static void assumeForkImplementation(StatusChange2972Settings settings) {
        assumeTrue(implementation(settings).equals(UserServiceImplementation.FORK),
                "Тест-кейсы для /api/v2/users/{id} выполняются только для fork-варианта");
    }

    @CriticalRegression
    @Test
    @DisplayName("USER-REG-01. permissions возвращает контракт для первичного пользователя")
    void permissionsShouldReturnContractForConfiguredPrimaryUser() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        assumeGeckoImplementation(settings);
        String primarySub = settings.required("auth.primary.sub");

        AtomicReference<ValidatableResponseWrapper> response = new AtomicReference<>();
        getFlowWithRest()
                .step("Запрашиваем /api/v2/users/permissions", flow -> response.set(
                        flow.restCustomSteps().userServiceSteps().getPermissionsStatusOk(
                                primarySub,
                                CONTROL_POINT_STATUS_UPDATE,
                                List.of(DEFAULT_ROLE, BACKUP_ROLE)
                        )
                ))
                .run();

        assertNotNull(response.get(), "Ответ по /api/v2/users/permissions не должен быть null");
        Object envelope = response.get().toJsonPath().get();
        assertInstanceOf(Map.class, envelope, "Ожидаем JSON-объект");
        Map<?, ?> values = (Map<?, ?>) envelope;
        assertTrue(values.containsKey("userId"), "Ожидаем поле userId в permissions");
        assertNotNull(values.get("effectivePermissions"), "Поля effectivePermissions не должно быть null");
        assertNotNull(values.get("userData"), "Поля userData не должно быть null");
    }

    @CriticalRegression
    @Test
    @DisplayName("USER-REG-02. permissions отклоняет отсутствие обязательных ролей")
    void permissionsShouldRejectMissingRole() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        assumeGeckoImplementation(settings);
        String primarySub = settings.required("auth.primary.sub");

        getFlowWithRest()
                .step("Запрашиваем /api/v2/users/permissions без roles", flow -> flow.restCustomSteps().userServiceSteps()
                        .getPermissionsStatusBadRequest(primarySub, CONTROL_POINT_STATUS_UPDATE, List.of()))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("USER-REG-03. upsert пользователя идентичен при повторном вызове (idempotent)")
    void postUserShouldBeIdempotent() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        assumeGeckoImplementation(settings);
        String primarySub = settings.required("auth.primary.sub");
        Map<String, Object> payload = primaryUserPayload(settings);

        AtomicLong firstUserId = new AtomicLong();
        AtomicLong secondUserId = new AtomicLong();
        getFlowWithRest()
                .step("Выполняем POST /api/v2/users", flow ->
                        flow.restCustomSteps().userServiceSteps().upsertStatusOk(payload))
                .step("Считываем userId после первого upsert", flow ->
                        firstUserId.set(extractUserIdFromPermissions(flow.restCustomSteps().userServiceSteps()
                                .getPermissionsStatusOk(primarySub, CONTROL_POINT_STATUS_UPDATE, List.of(DEFAULT_ROLE, BACKUP_ROLE))
                        )))
                .step("Повторяем POST /api/v2/users тем же телом", flow ->
                        flow.restCustomSteps().userServiceSteps().upsertStatusOk(payload))
                .step("Считываем userId после второго upsert", flow ->
                        secondUserId.set(extractUserIdFromPermissions(flow.restCustomSteps().userServiceSteps()
                                .getPermissionsStatusOk(primarySub, CONTROL_POINT_STATUS_UPDATE, List.of(DEFAULT_ROLE, BACKUP_ROLE))
                        )))
                .run();

        assertNotEquals(0L, firstUserId.get(), "Первый upsert не вернул userId");
        assertNotEquals(0L, secondUserId.get(), "Повторный upsert не вернул userId");
        assertEquals(firstUserId.get(), secondUserId.get(), "Повторный upsert с тем же payload должен быть идемпотентным");
    }

    @Test
    @DisplayName("USER-REG-04. upsert отклоняет некорректный запрос")
    void postUserShouldRejectInvalidPayload() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        assumeGeckoImplementation(settings);
        Map<String, Object> payload = Map.of(
                "employeeId", settings.required("auth.primary.employee-id"),
                "userName", settings.required("auth.primary.login")
        );

        getFlowWithRest()
                .step("Проверяем POST /api/v2/users с неполным телом", flow ->
                        flow.restCustomSteps().userServiceSteps().upsertStatusBadRequest(payload))
                .run();
    }

    @Test
    @DisplayName("USER-REG-05. получение пользователя через /subs возвращает объект с ожидаемым sub")
    void shouldGetBySubsForConfiguredPrimary() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        assumeGeckoImplementation(settings);
        String primarySub = settings.required("auth.primary.sub");

        AtomicReference<ValidatableResponseWrapper> response = new AtomicReference<>();
        getFlowWithRest()
                .step("Запрашиваем /api/v2/users/subs", flow -> response.set(
                        flow.restCustomSteps().userServiceSteps().getBySubsStatusOk(List.of(primarySub))
                ))
                .run();

        List<?> users = extractUsers(response.get(), "users");
        assertFalse(users.isEmpty(), "Ожидаем хотя бы одну запись пользователя");

        Object first = users.get(0);
        assertInstanceOf(Map.class, first, "Элемент списка users должен быть объектом");
        Map<?, ?> user = (Map<?, ?>) first;
        assertEquals(primarySub, user.get("sub"), "Первый элемент должен совпадать по sub");
    }

    @Test
    @DisplayName("USER-REG-06. /audit/{sub} возвращает объект пользователя")
    void shouldGetAuditBySub() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        assumeGeckoImplementation(settings);
        String primarySub = settings.required("auth.primary.sub");

        AtomicReference<ValidatableResponseWrapper> response = new AtomicReference<>();
        getFlowWithRest()
                .step("Запрашиваем /api/v2/users/audit/{sub}", flow -> response.set(
                        flow.restCustomSteps().userServiceSteps().getAuditBySubStatusOk(primarySub)
                ))
                .run();

        Map<?, ?> payload = (Map<?, ?>) response.get().toJsonPath().get();
        assertNotNull(payload.get("id"), "Аудит должен возвращать id");
    }

    @Test
    @DisplayName("USER-REG-07. поиск по роли и по employeeId")
    void shouldGetByRoleAndByEmployeeIds() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        assumeGeckoImplementation(settings);

        String employeeId = settings.required("auth.primary.employee-id");
        AtomicReference<ValidatableResponseWrapper> byRoleResponse = new AtomicReference<>();
        AtomicReference<ValidatableResponseWrapper> byEmployeeIdsResponse = new AtomicReference<>();
        getFlowWithRest()
                .step("Запрашиваем /api/v2/users/by-role", flow -> byRoleResponse.set(
                        flow.restCustomSteps().userServiceSteps().getByRoleStatusOk(DEFAULT_ROLE)
                ))
                .step("Запрашиваем /api/v2/users/by-employee-ids", flow -> byEmployeeIdsResponse.set(
                        flow.restCustomSteps().userServiceSteps().getByEmployeeIdsStatusOk(List.of(employeeId))
                ))
                .run();

        List<?> byRoleUsers = extractUsers(byRoleResponse.get(), "users");
        List<?> byEmployeeUsers = extractUsers(byEmployeeIdsResponse.get(), "users");
        assertFalse(byRoleUsers.isEmpty(), "Ожидается не пустой список для /by-role");
        assertFalse(byEmployeeUsers.isEmpty(), "Ожидается не пустой список для /by-employee-ids");
    }

    @Test
    @DisplayName("USER-REG-08. fork-реализация возвращает пользователя по id")
    void forkImplementationShouldGetUserById() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        assumeForkImplementation(settings);

        String primaryUserId = settings.optional("auth.primary.user-id", null);
        assumeTrue(primaryUserId != null && !primaryUserId.isBlank(), "Для fork-ветки нужен auth.primary.user-id");
        AtomicReference<ValidatableResponseWrapper> response = new AtomicReference<>();
        getFlowWithRest()
                .step("Запрашиваем /api/v2/users/{id}", flow -> response.set(
                        flow.restCustomSteps().userServiceSteps().getByIdStatusOk(primaryUserId)
                ))
                .run();

        Map<?, ?> payload = (Map<?, ?>) response.get().toJsonPath().get();
        assertNotNull(payload.get("id"), "Fork-реализация должна возвращать id");
        assertEquals(Long.parseLong(primaryUserId), toLong(payload.get("id")), "По запросу /{id} должен вернуться тот же id");
    }

    private static long extractUserIdFromPermissions(ValidatableResponseWrapper response) {
        Object userId = response.toJsonPath().get("userId");
        return toLong(userId);
    }

    @SuppressWarnings("unchecked")
    private static List<?> extractUsers(ValidatableResponseWrapper response, String... keys) {
        Object payload = response.toJsonPath().get();
        if (payload instanceof List<?>) {
            return (List<?>) payload;
        }
        assertInstanceOf(Map.class, payload, "Ожидается список или envelope-объект с пользователями");
        Map<String, Object> values = (Map<String, Object>) payload;
        for (String key : keys) {
            Object users = values.get(key);
            if (users instanceof List<?>) {
                return (List<?>) users;
            }
        }
        for (Object value : values.values()) {
            if (value instanceof List<?>) {
                return (List<?>) value;
            }
        }
        return List.of();
    }

    private static long toLong(Object raw) {
        if (raw == null) {
            fail("Ожидаем числовое значение");
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String value) {
            return Long.parseLong(value);
        }
        throw new IllegalStateException("Неожиданный тип " + raw.getClass().getSimpleName() + " для числового поля");
    }
}
