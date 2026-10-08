package util;

public final class SplitterFlowPaths {

    private SplitterFlowPaths() {}

    public static String objectPath(String objectId) {
        return "splittingResults.find { it.objectId == '" + objectId + "' }";
    }

    public static String objectResultPath(String objectId, String ruleCode) {
        return objectPath(objectId) + ".objectResults.find { it.ruleCode == '" + ruleCode + "' }";
    }
}
