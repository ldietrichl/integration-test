package ru.sber.qa.experiments.EXPLAB_2559;

import org.junit.jupiter.api.Assumptions;

final class ExperimentV2UserConditionScopeAssumptions {

    private static final String GENERIC_MAPPER_SCOPE_PROPERTY = "experiment.mapper.scope.available";
    private static final String MAPPER_SCOPE_PROPERTY = "exlab2559.mapper.scope.available";
    private static final String GENERIC_MAPPER_SCOPE_ENV = "EXPERIMENT_MAPPER_SCOPE_AVAILABLE";
    private static final String MAPPER_SCOPE_ENV = "EXPLAB_2559_MAPPER_SCOPE_AVAILABLE";

    private ExperimentV2UserConditionScopeAssumptions() {
    }

    static void assumeMapperScopeAvailable() {
        Assumptions.assumeTrue(isMapperScopeAvailable(),
                "EXPLAB-2559 проверяет MAPPER-эксперименты: для запуска нужен scope MAPPER или area_clist_sp_all. "
                        + "На REACTIONS-only стенде передайте -Dexperiment.mapper.scope.available=false, "
                        + "чтобы не считать падение дефектом userCondition.");
    }

    private static boolean isMapperScopeAvailable() {
        String configuredValue = firstNotBlank(
                System.getProperty(GENERIC_MAPPER_SCOPE_PROPERTY),
                System.getenv(GENERIC_MAPPER_SCOPE_ENV),
                System.getProperty(MAPPER_SCOPE_PROPERTY),
                System.getenv(MAPPER_SCOPE_ENV)
        );
        return configuredValue == null || Boolean.parseBoolean(configuredValue);
    }

    private static String firstNotBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
