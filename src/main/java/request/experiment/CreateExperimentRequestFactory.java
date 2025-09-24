package request.experiment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.experiment.ExperimentRequestDto;

import java.util.Collections;
import java.util.List;

/**
 * Фабрика: по параметрам теста (CreateExperimentParams) строит DTO и JSON.
 */
public class CreateExperimentRequestFactory {

    private final ObjectMapper mapper;

    public CreateExperimentRequestFactory() {
        // сериализуем null-поля
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    /** Построить DTO по параметрам. */
    public ExperimentRequestDto buildDto(CreateExperimentParams p) {

        // ---- фильтр cjId ----
        var filter = ExperimentRequestDto.Filter.builder()
                .parameterCode("cjId")
                .operatorCode("equal")
                .values(p.getCjIds())
                .build();

        // ---- configQuery (внутри groupConfig) ----
        var configQuery = ExperimentRequestDto.ConfigQuery.builder()
                .allCjComms(false)
                .sorts(Collections.emptyList())
                .filters(List.of(filter))
                .commIdsInclude(Collections.emptyList())
                .commIdsExclude(Collections.emptyList())
                .build();

        // ---- groupConfig только для группы A ----
        var groupConfigA = ExperimentRequestDto.GroupConfig.builder()
                .id(p.getGroupConfigId())
                .configSource(p.getConfigSource())
                .dynamicConfig(p.isDynamicConfig())
                .staticConfig(Collections.emptyList())
                .configQuery(configQuery)
                .build();

        // ---- группа A (целёвая) ----
        var groupA = ExperimentRequestDto.ExperimentGroup.builder()
                .id(p.getGroupAId())               // может быть null
                .symbolName("A")
                .name(" Целевая группа")
                .actionTypeId(p.getActionTypeId())
                .groupConfig(groupConfigA)
                .size(p.getSizeA())
                .baseline(false)
                .configSplits(null)                // как в успешном примере
                .shareFrom(p.getShareFromA())
                .shareTo(p.getShareToA())
                .build();

        // ---- группа B (контрольная) ----
        var groupB = ExperimentRequestDto.ExperimentGroup.builder()
                .id(p.getGroupBId())               // может быть null
                .symbolName("B")
                .name(" Контрольная группа")
                .actionTypeId(p.getActionTypeId())
                .groupConfig(null)                 // строго null, как в образце
                .size(p.getSizeB())
                .baseline(p.isBaselineB())
                .configSplits(null)                // как в образце
                .shareFrom(p.getShareFromB())
                .shareTo(p.getShareToB())
                .build();

        // ---- корневой объект ----
        var dtoBuilder = ExperimentRequestDto.builder()
                .id(p.getId())
                .name(p.getName())
                .startDt(p.getStartDt())
                .endDt(p.getEndDt())
                .createdBy(p.getCreatedBy())
                .createdDt(p.getCreatedDt())
                .updatedBy(p.getUpdatedBy())
                .updatedDt(p.getUpdatedDt())
                .statusChangedBy(p.getStatusChangedBy())


                // верхние objectId/formatId в примере были null — оставляем null,
                // а итоговые значения ставим ниже (см. конец билда)
                .object(p.getObject())
                .formatId(null)

                .salt(p.getSalt())
                .hypothesisDesc(p.getHypothesisDesc())
                .creator(p.getCreator())
                .sendings(p.getSendings())
                .budget(p.getBudget())
                .hashAlgorithm(p.getHashAlgorithm())
                .quantum(p.getQuantum())
                .compareType(p.getCompareType())
                .metrics(Collections.emptyList())
                .experimentGroups(List.of(groupA, groupB))
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
                .updateMetrics(p.isUpdateMetrics())
                .purpose(p.getPurpose())
                .withCampaign(p.isWithCampaign())
                .formatId(p.getFormatId())
                .objectId(p.getObjectId());

        // финальные значения внизу (как в удачном JSON)
        dtoBuilder.object(p.getObject());


        // Schedule заполняем только если что-то передали
        if (p.getRepeatPeriod() != null || p.getRepeatStartDate() != null ||
                p.getRepeatStopDate() != null || p.getSaltTextPart() != null ||
                p.getMonthShift() != null || p.getTaskNumber() != null) {

            var schedule = ExperimentRequestDto.ScheduleParam.builder()
                    .repeatPeriod(p.getRepeatPeriod())
                    .repeatStartDate(p.getRepeatStartDate())
                    .repeatStopDate(p.getRepeatStopDate())
                    .saltTextPart(p.getSaltTextPart())
                    .monthShift(p.getMonthShift())
                    .taskNumber(p.getTaskNumber())
                    .build();

            dtoBuilder.scheduleParam(schedule);
        }

        return dtoBuilder.build();
    }

    /** Сериализовать в JSON-строку (включая null-поля). */
    public String toJson(ExperimentRequestDto dto) {
        try {
            return mapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса", e);
        }
    }
}