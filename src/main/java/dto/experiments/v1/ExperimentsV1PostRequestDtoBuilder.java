package dto.experiments.v1;

import dto.enums.OperatorCodeExpression;

import java.util.Collections;
import java.util.List;

import static io.qameta.allure.Allure.step;

public class ExperimentsV1PostRequestDtoBuilder {
    /** Fresh owned draft; never carries IDs from the historical default example. */
    public static ExperimentsV1PostRequestDto linksFixture(String name, List<String> cjIds, int configCount) {
        if (configCount < 0 || configCount > 8) throw new IllegalArgumentException("Fixture config count 0..8 required");
        java.util.ArrayList<ExperimentsV1PostRequestDto.ExperimentGroup> groups = new java.util.ArrayList<>();
        for (int i = 0; i < configCount; i++) {
            var query = configQueryDefault().toBuilder().filters(List.of(filterDefault().toBuilder()
                    .operatorCode(OperatorCodeExpression.IN).values(cjIds).build())).build();
            groups.add(groupADefault().toBuilder().id(null).symbolName(Character.toString((char) ('A' + i)))
                    .name("Links fixture " + i).size(1000).shareFrom(i * 1000).shareTo((i + 1) * 1000)
                    .groupConfig(groupConfigADefault().toBuilder().id(null).configQuery(query).build()).build());
        }
        groups.add(groupBDefault().toBuilder().id(null).symbolName("Z").name("Links control")
                .size(1000).shareFrom(9000).shareTo(10000).build());
        long now = System.currentTimeMillis();
        return buildDtoDefault().toBuilder().name(name).salt(java.util.UUID.randomUUID().toString())
                .startDt(now + 86400000L).endDt(now + 172800000L).experimentGroups(groups)
                .autoStart(false).autoStop(false).startCampaigns(false).build();
    }
    // ---- фильтр cjId ----
    static public ExperimentsV1PostRequestDto.Filter filterDefault() {
        return step("Формирование дефолтных параметров фильтра для конфигурационного запроса", () ->
                ExperimentsV1PostRequestDto.Filter.builder()
                        .parameterCode("cjId")
                        .operatorCode(OperatorCodeExpression.EQUAL)
                        .values(null) // значения фильтра (например: ["103081"])
                        .build());
    }

    // ---- configQuery (внутри groupConfig) ----
    static public ExperimentsV1PostRequestDto.ConfigQuery configQueryDefault() {
        return step("Формирование дефолтных параметров конфигурационного запроса для конфигурации группы А"
                , () ->
                        ExperimentsV1PostRequestDto.ConfigQuery.builder()
                                .allCjComms(false)
                                .sorts(Collections.emptyList())
                                .filters(List.of(filterDefault()))
                                .commIdsInclude(Collections.emptyList())
                                .commIdsExclude(Collections.emptyList())
                                .build());
    }

    // ---- groupConfig только для группы A ----
    static public ExperimentsV1PostRequestDto.GroupConfig groupConfigADefault() {
        return step("Формирование дефолтных параметров конфигурации группы А для группы А", () ->
                ExperimentsV1PostRequestDto.GroupConfig.builder()
                        .id(2055L)
                        .configSource("SELECT")
                        .dynamicConfig(true)
                        .staticConfig(Collections.emptyList())
                        .configQuery(configQueryDefault())
                        .build());
    }

    // ---- Настройка групп, группа A (целёвая) ----
    static public ExperimentsV1PostRequestDto.ExperimentGroup groupADefault() {
        return step("Формирование дефолтных параметров группы A для корневого объекта создания эксперимента", () ->
                ExperimentsV1PostRequestDto.ExperimentGroup.builder()
                        .id(3570L) // Идентификаторы группы (можно оставить null)
                        .symbolName("A")
                        .name("Целевая группа")
                        .actionTypeId(2)
                        .groupConfig(groupConfigADefault())
                        .size(2000)
                        .baseline(false)
                        .configSplits(null) // как в успешном примере
                        .shareFrom(0)
                        .shareTo(2000)
                        .build());
    }

    // ---- Настройка групп, группа B (контрольная) ----
    static public ExperimentsV1PostRequestDto.ExperimentGroup groupBDefault() {
        return step("Формирование дефолтных параметров группы B для корневого объекта создания эксперимента", () ->
                ExperimentsV1PostRequestDto.ExperimentGroup.builder()
                        .id(3571L) // Идентификаторы группы (можно оставить null)
                        .symbolName("B")
                        .name("Контрольная группа")
                        .actionTypeId(2)
                        .groupConfig(null) // строго null, как в образце
                        .size(2000)
                        .baseline(true) // B — контрольная
                        .configSplits(null) // как в образце
                        .shareFrom(5000)
                        .shareTo(7000)
                        .build());
    }

    // Schedule (опционально)
    static public ExperimentsV1PostRequestDto.ScheduleParam scheduleParamDefault() {
        return step("Формирование дефолтных параметров расписания для корневого объекта создания эксперимента", () ->
                ExperimentsV1PostRequestDto.ScheduleParam.builder()
                        .build());
    }

    static public ExperimentsV1PostRequestDto buildDtoDefault() {
        return step("Формирование корневого объекта дефолтных параметров создания эксперимента", () ->
                ExperimentsV1PostRequestDto.builder()
                        // Основные переменные из теста
                        .id(null)
                        .name(null)
                        .startDt(null)
                        .endDt(null)
                        .createdBy(null)
                        .createdDt(null)
                        .updatedBy(1L)
                        .updatedDt(null)
                        .statusChangedBy(1L)

                        // Верхние objectId/formatId в примере были null — оставляем null,
                        // а итоговые значения ставим ниже (см. конец билда)
                        .object(null)
                        .formatId(null)
                        .salt(null)

                        // Необязательные поля — по умолчанию null (как в образце)
                        .hypothesisDesc(null) // "Du27_01/3" — если нужно
                        .creator(null) // "АСтеповой" — если нужно
                        .sendings(null)
                        .budget(null)

                        // Поля корня (то, что реально важно в контракте)
                        .hashAlgorithm("MURMURHASH")
                        .quantum(10_000)
                        .compareType("FULL")
                        .metrics(Collections.emptyList())
                        .experimentGroups(List.of(groupADefault(), groupBDefault()))
                        .hasNotAgreedCJ(false)
                        .hasReadyToStartCJ(false)
                        .version(4)
                        .scheduleParam(null)
                        .startCampaigns(false)
                        .autoStart(false)
                        .autoStop(false)
                        .startedBy(null)
                        .stoppedBy(null)
                        .realStartDt(null)
                        .realEndDt(null)
                        .relations(Collections.emptyList())
                        .compareConfig(null)
                        .updateMetrics(true)
                        .purpose("COMMON")
                        .withCampaign(false)

                        // Служебные: objectId/formatId (в успешном примере 7 и 1)
                        .formatId(1)
                        .objectId(7).build()
        );
    }

    static public ExperimentsV1PostRequestDto buildDtoDefaultWithCustomParams(
            String name, Long startDt, Long endDt
            , String salt, String hypothesisDesc, String creator
            , List<String> cjIds) {
        return step(("Формирование дефолтных параметров создания эксперимента; Имя '%s', начальная дата: '%s'," +
                        " конечная дата: '%s', соль '%s', гипотеза '%s', автор '%s', cjIds '%s'")
                        .formatted(name, startDt, endDt, salt, hypothesisDesc, creator, cjIds)
                , () -> {
                    List<ExperimentsV1PostRequestDto.Filter> filters = List.of(
                            ExperimentsV1PostRequestDtoBuilder.filterDefault().toBuilder()
                                    // cjIds
                                    .values(cjIds)
                                    .build()
                    );

                    ExperimentsV1PostRequestDto.ConfigQuery configQuery = ExperimentsV1PostRequestDtoBuilder.configQueryDefault().toBuilder()
                            .filters(filters)
                            .build();

                    ExperimentsV1PostRequestDto.GroupConfig groupConfig = ExperimentsV1PostRequestDtoBuilder.groupConfigADefault().toBuilder()
                            .configQuery(configQuery)
                            .build();

                    List<ExperimentsV1PostRequestDto.ExperimentGroup> experimentGroups = List.of(
                            ExperimentsV1PostRequestDtoBuilder.groupADefault().toBuilder()
                                    .groupConfig(groupConfig)
                                    .build()
                            , ExperimentsV1PostRequestDtoBuilder.groupBDefault()
                    );

                    return ExperimentsV1PostRequestDtoBuilder.buildDtoDefault().toBuilder()
                            .name(name)
                            .startDt(startDt)
                            .endDt(endDt) // +1 день
                            .salt(salt)
                            // experimentGroups -> groupConfig -> groupConfig -> filters -> cjIds
                            .experimentGroups(experimentGroups)
                            .hypothesisDesc(hypothesisDesc)
                            .creator(creator)
                            .build();
                });
    }

    static public ExperimentsV1PostRequestDto buildDtoDefaultWithCustomAndScheduleParams(
            String name, Long startDt, Long endDt
            , String salt, String hypothesisDesc, String creator
            , List<String> cjIds, Integer repeatPeriod, Long repeatStartDate
            , Long repeatStopDate, String saltTextPart, Integer monthShift
            , Integer taskNumber) {
        return step(("Формирование дефолтных параметров создания эксперимента; Имя '%s', начальная дата: '%s'," +
                        " конечная дата: '%s', соль '%s', гипотеза '%s', автор '%s', cjIds '%s'," +
                        " период повторения '%s', дата начала повторения '%s', дата окончания повторения '%s'," +
                        " salt text part '%s', month shift '%s', номер задачи '%s'")
                        .formatted(
                                name, startDt, endDt
                                , salt, hypothesisDesc, creator
                                , cjIds, repeatPeriod, repeatStartDate
                                , repeatStopDate, saltTextPart, monthShift
                                , taskNumber)
                , () -> {
                    ExperimentsV1PostRequestDto dto = buildDtoDefaultWithCustomParams(
                            name, startDt, endDt, salt, hypothesisDesc, creator, cjIds);

                    return dto.toBuilder()
                            .scheduleParam(
                                    scheduleParamDefault().toBuilder()
                                            .repeatPeriod(repeatPeriod)
                                            .repeatStartDate(repeatStartDate)
                                            .repeatStopDate(repeatStopDate)
                                            .saltTextPart(saltTextPart)
                                            .monthShift(monthShift)
                                            .taskNumber(taskNumber)
                                            .build()
                            )
                            .build();
                });
    }
}
