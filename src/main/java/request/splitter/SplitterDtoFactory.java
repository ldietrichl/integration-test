package request.splitter;

import dto.splitter.config.*;
import dto.splitter.common.ParamDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class SplitterDtoFactory {
    private SplitterDtoFactory() {
    }

    public static ParamDto param(String code, String value, String dataType) {
        return ParamDto.builder()
                .paramCode(code)
                .paramValues(List.of(value))
                .dataType(dataType)
                .build();
    }

    public static RuleDto rule(String dataType, String paramCode, String paramSource, String operatorCode, String... values) {
        return RuleDto.builder()
                .dataType(dataType)
                .paramCode(paramCode)
                .paramSource(paramSource)
                .operatorCode(operatorCode)
                .values(values == null ? null : List.of(values))
                .build();
    }

    public static ObjectSelectConditionDto condition(int id, List<List<RuleDto>> rules) {
        return ObjectSelectConditionDto.builder().id(id).rules(rules).build();
    }

    public static ShareDto share(int from, int to) {
        return ShareDto.builder().shareFrom(from).shareTo(to).build();
    }

    public static SplittingResultDto result(int conditionId, ParamDto... params) {
        return SplittingResultDto.builder()
                .conditionId(conditionId)
                .resultParams(params == null ? Collections.emptyList() : List.of(params))
                .build();
    }

    public static GroupDto group(String code, List<ShareDto> shares, List<SplittingResultDto> results) {
        return GroupDto.builder().code(code).shares(shares).splittingResults(results).build();
    }

    public static ExperimentDto experiment(int id, String salt, List<ObjectSelectConditionDto> conditions, List<GroupDto> groups) {
        return ExperimentDto.builder()
                .id(id)
                .purpose("DCG")
                .salt(salt)
                .objectSelectConditions(conditions)
                .groups(groups)
                .build();
    }

    public static SplittingConfigDto config(List<ExperimentDto> experiments) {
        return SplittingConfigDto.builder().experiments(experiments).build();
    }

    public static LoadConfigRequestDto loadConfigRequest(long version, boolean force, SplittingConfigDto config) {
        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(force)
                .splittingPointCode("MAPPER")
                .splittingConfig(config)
                .build();
    }

    public static SplittingObjectDto splitObject(String objectId, ParamDto... params) {
        return SplittingObjectDto.builder().objectId(objectId).objectParams(List.of(params)).build();
    }

    public static SplitRequestDto splitRequest(String requestId, String splittingId, List<ParamDto> requestParams, List<SplittingObjectDto> objects) {
        return SplitRequestDto.builder()
                .requestId(requestId)
                .splittingId(splittingId)
                .requestParams(requestParams)
                .splittingObjects(objects)
                .build();
    }
}
