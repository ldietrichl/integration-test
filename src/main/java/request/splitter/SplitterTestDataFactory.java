package request.splitter;

import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static request.splitter.SplitterDtoFactory.*;

public final class SplitterTestDataFactory {
    public static final String MATCHED_OBJECT_ID = "22222222-2222-2222-2222-222222222222";
    public static final String UNMATCHED_OBJECT_ID = "11111111-1111-1111-1111-111111111111";
    public static final String SECOND_OBJECT_ID = "99999999-9999-9999-9999-999999999999";
    public static final String SPLITTING_ID = "123456";

    private SplitterTestDataFactory() {
    }

    public static SplittingObjectDto matchedObject() {
        return splitObject(MATCHED_OBJECT_ID,
                param("configCommId", "1002", "INTEGER"),
                param("cjId", "123", "INTEGER"),
                param("sellingProductId", "1-XCDV6TA", "STRING"),
                param("channelId", "20", "INTEGER"),
                param("templateId", "120222", "INTEGER"));
    }

    public static SplittingObjectDto unmatchedObject() {
        return splitObject(UNMATCHED_OBJECT_ID,
                param("configCommId", "1002", "INTEGER"),
                param("cjId", "123", "INTEGER"),
                param("sellingProductId", "1-SSSV6DD", "STRING"),
                param("channelId", "20", "INTEGER"),
                param("templateId", "120222", "INTEGER"));
    }

    public static SplitRequestDto splitForMatchedObject() {
        return splitRequest(UUID.randomUUID().toString(), SPLITTING_ID, Collections.emptyList(), List.of(matchedObject()));
    }

    public static SplitRequestDto splitForUnmatchedObject() {
        return splitRequest(UUID.randomUUID().toString(), SPLITTING_ID, Collections.emptyList(), List.of(unmatchedObject()));
    }

    public static SplitRequestDto emptyObjectsRequest() {
        return splitRequest(UUID.randomUUID().toString(), SPLITTING_ID, Collections.emptyList(), Collections.emptyList());
    }

    public static SplitRequestDto invalidSplitRequestWithoutRequestId() {
        return SplitRequestDto.builder()
                .splittingId(SPLITTING_ID)
                .requestParams(Collections.emptyList())
                .splittingObjects(List.of(matchedObject()))
                .build();
    }

    public static LoadConfigRequestDto baseValidConfig(long version) {
        return loadConfigRequest(version, false, config(List.of(baseExperiment())));
    }

    public static LoadConfigRequestDto oldForcedBaseValidConfig(long version) {
        return loadConfigRequest(version, true, config(List.of(baseExperiment())));
    }

    public static LoadConfigRequestDto invalidNoSaltOrLayerConfig(long version) {
        ExperimentDto exp = ExperimentDto.builder()
                .id(901)
                .objectSelectConditions(List.of(matchCondition()))
                .groups(List.of(
                        group("A", List.of(share(0, 10000)), List.of(result(1, param("actionType", "1", "INTEGER"))))
                ))
                .build();
        return loadConfigRequest(version, false, config(List.of(exp)));
    }

    public static LoadConfigRequestDto invalidNoConditionsConfig(long version) {
        ExperimentDto exp = ExperimentDto.builder()
                .id(902)
                .salt("SALT-902")
                .objectSelectConditions(Collections.emptyList())
                .groups(List.of(group("A", List.of(share(0, 10000)), Collections.emptyList())))
                .build();
        return loadConfigRequest(version, false, config(List.of(exp)));
    }

    public static LoadConfigRequestDto invalidRangesConfig(long version) {
        ExperimentDto exp = experiment(903, "SALT-903", List.of(matchCondition()), List.of(
                group("A", List.of(share(0, 7000)), Collections.emptyList()),
                group("B", List.of(share(6000, 10000)), Collections.emptyList())
        ));
        return loadConfigRequest(version, false, config(List.of(exp)));
    }

    public static LoadConfigRequestDto requestParamsConfig(long version) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(301)
                .purpose("REQ")
                .salt("SALT-301")
                .objectSelectConditions(List.of(
                        ObjectSelectConditionDto.builder()
                                .id(1)
                                .rules(List.of(List.of(
                                        rule("INTEGER", "age", "REQUEST_PARAMS", "equal", "60")
                                )))
                                .build()
                ))
                .groups(List.of(group("A", List.of(share(0, 10000)), List.of(result(1, param("actionType", "1", "INTEGER"))))))
                .build();
        return loadConfigRequest(version, false, config(List.of(experiment)));
    }

    public static LoadConfigRequestDto andOrConfig(long version) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(401)
                .purpose("AND_OR")
                .salt("SALT-401")
                .objectSelectConditions(List.of(
                        ObjectSelectConditionDto.builder()
                                .id(1)
                                .rules(List.of(
                                        List.of(
                                                rule("STRING", "sellingProductId", "SPLITTING_OBJECTS", "equal", "1-XCDV6TA"),
                                                rule("INTEGER", "age", "REQUEST_PARAMS", "equal", "60")
                                        ),
                                        List.of(
                                                rule("STRING", "sellingProductId", "SPLITTING_OBJECTS", "equal", "1-SSSV6DD"),
                                                rule("STRING", "segment", "REQUEST_PARAMS", "equal", "segment1")
                                        )
                                ))
                                .build()
                ))
                .groups(List.of(group("A", List.of(share(0, 10000)), List.of(result(1, param("actionType", "1", "INTEGER"))))))
                .build();
        return loadConfigRequest(version, false, config(List.of(experiment)));
    }

    public static SplitRequestDto splitForTwoObjectsWithAgeAndSegment() {
        return splitRequest(UUID.randomUUID().toString(), SPLITTING_ID, List.of(
                        param("age", "60", "INTEGER"),
                        param("segment", "segment1", "STRING")
                ), List.of(
                        matchedObject(),
                        splitObject(SECOND_OBJECT_ID,
                                param("configCommId", "1003", "INTEGER"),
                                param("cjId", "123", "INTEGER"),
                                param("sellingProductId", "1-SSSV6DD", "STRING"),
                                param("channelId", "20", "INTEGER"),
                                param("templateId", "120333", "INTEGER"))
                ));
    }

    public static LoadConfigRequestDto priorityConfig(long version) {
        return loadConfigRequest(version, false, config(List.of(
                experimentWithActionType(601, "SALT-601", "1"),
                experimentWithActionType(602, "SALT-602", "3"),
                experimentWithActionType(603, "SALT-603", "2")
        )));
    }

    public static LoadConfigRequestDto tieConfig(long version) {
        return loadConfigRequest(version, false, config(List.of(
                experimentWithActionType(701, "SALT-701", "3"),
                experimentWithActionType(702, "SALT-702", "3")
        )));
    }

    public static LoadConfigRequestDto invalidActionConfig(long version) {
        ExperimentDto bad = experiment(801, "SALT-801", List.of(matchCondition()), List.of(
                group("A", List.of(share(0, 10000)), List.of(result(1, param("unknownAction", "999", "INTEGER"))))
        ));
        ExperimentDto good = experimentWithActionType(802, "SALT-802", "1");
        return loadConfigRequest(version, false, config(List.of(bad, good)));
    }

    public static LoadConfigRequestDto actionType2Config(long version) {
        return loadConfigRequest(version, false, config(List.of(experimentWithActionType(1201, "SALT-1201", "2"))));
    }

    public static LoadConfigRequestDto actionType4Config(long version) {
        return loadConfigRequest(version, false, config(List.of(experimentWithActionType(1301, "SALT-1301", "4"))));
    }

    private static ExperimentDto baseExperiment() {
        return experimentWithActionType(101, "SALT-101", "1");
    }

    private static ExperimentDto experimentWithActionType(int id, String salt, String actionType) {
        return experiment(id, salt, List.of(matchCondition()), List.of(
                group("A", List.of(share(0, 10000)), List.of(result(1, param("actionType", actionType, "INTEGER"))))
        ));
    }

    private static ObjectSelectConditionDto matchCondition() {
        return condition(1, List.of(List.of(rule("STRING", "sellingProductId", "SPLITTING_OBJECTS", "equal", "1-XCDV6TA"))));
    }

    public static LoadConfigRequestDto allRuleConfig(long version) {
        return loadConfigRequest(version, false, config(List.of(
                experiment(401, "SALT-401", List.of(matchCondition()), List.of(
                        group("A", List.of(share(0, 10000)), List.of(result(1, param("actionType", "1", "INTEGER"))))
                )),
                experiment(402, "SALT-402", List.of(matchCondition()), List.of(
                        group("A", List.of(share(0, 10000)), List.of(result(1, param("actionType", "3", "INTEGER"))))
                ))
        )));
    }
}
