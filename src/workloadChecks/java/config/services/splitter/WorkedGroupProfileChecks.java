package config.services.splitter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class WorkedGroupProfileChecks {
    @ParameterizedTest
    @CsvSource({"explab2984.,rest,true", "explab2984.reactions.,rest,false",
            "explab2984.,kafka,false", "explab2984.reactions.,kafka,true"})
    void dynamicProfileHasExplicitLoadModeAndPrecalculationDisabled(String prefix, String mode, boolean allow) {
        var flags = WorkedGroupProfile.applicationFlags(prefix, allow, mode, true);
        assertEquals(Boolean.toString(mode.equals("rest")), flags.get("SPLITTER_API_CONFIG_LOAD"));
        assertEquals(Boolean.toString(allow), flags.get("SPLITTER_ALLOW_RESULT_WITHOUT_MAIN"));
        assertEquals("false", flags.get("SPLITTER_PRELIMINARY_CALCULATION_ENABLED"));
        assertEquals("true", flags.get("SPLITTER_EMPTY_OBJECTS_RESPONSE_ENABLED"));
        assertEquals("true", flags.get("SPLITTER_ALL_RULE_CODE_EXP_ENABLED"));
        assertEquals("true", flags.get("SPLITTER_RETURN_SUPPRESSED"));
    }
    @Test void legacyProfileDoesNotChangePrecalculation() {
        var flags = WorkedGroupProfile.applicationFlags("explab2690.", true, "rest", false);
        assertEquals(5, flags.size());
        assertFalse(flags.containsKey("SPLITTER_PRELIMINARY_CALCULATION_ENABLED"));
    }
    @Test void cannotRunDynamicScenariosWithUnmanagedFlags() {
        assertThrows(IllegalStateException.class, () -> WorkedGroupProfile.prepare("unconfigured2984", true, true, "rest", true, "splitter/EXPLAB_2984/configmap/"));
    }
    @Test void rejectsUnknownLoadModeBeforeAnyWorkloadAccess() {
        assertThrows(IllegalArgumentException.class, () -> WorkedGroupProfile.prepare("explab2984", true, true, "typo", true, "splitter/EXPLAB_2984/configmap/"));
    }
    @Test void explicitPrecalculationOverrideCannotBypassManagedFlags() {
        assertThrows(IllegalStateException.class, () -> WorkedGroupProfile.prepare("unconfigured3056", false, true,
                "rest", false, "splitter/EXPLAB_3056/configmap/", java.util.function.UnaryOperator.identity(),
                java.util.Map.of("preliminary-calculation-enabled", "true")));
    }

    @ParameterizedTest @CsvSource({"true,splitter-reactions", "false,splitter-mapper"})
    void suppliesExistingServiceDefaultsWithoutStandKeys(boolean reactions, String workload) {
        var plan = WorkedGroupProfile.workloadPlan("explab2984", reactions, "rest", k -> null, k -> null);
        assertEquals(workload, plan.target().workload());
        assertEquals(workload + "-service", plan.deployment());
        assertEquals(workload + "-service-lib", plan.rulesMap());
        var defaults = config.services.core.StandSettings.workloadDefaults("ift", workload, plan.deployment(), plan.rulesMap());
        assertEquals(4, defaults.size());
        assertEquals(plan.deployment(), defaults.get("stand.ift.workloads." + workload + ".deployment"));
        assertTrue(defaults.keySet().stream().noneMatch(k -> k.contains("mutations")));
    }
    @ParameterizedTest @CsvSource({"true,explab2885.reactions.", "false,exlab2891.mapper."})
    void reusesExistingProfileOverrides(boolean reactions, String prefix) {
        var settings = java.util.Map.of(prefix + "stand.workload", "shared-splitter", prefix + "stand.deployment", "existing-deployment",
                prefix + "stand.service", "existing-service", prefix + "stand.container", "existing-container",
                prefix + "stand.service-port", "8090", prefix + "configmap.rules.name", "existing-rules");
        var plan = WorkedGroupProfile.workloadPlan("explab2984", reactions, "kafka", settings::get, k -> null);
        assertEquals("shared-splitter", plan.target().workload());
        assertEquals("existing-deployment", plan.deployment());
        assertEquals("existing-rules", plan.rulesMap());
        assertEquals(8090, plan.target().port());
        assertEquals("existing-container", plan.target().container());
    }
    @Test void explicitStandIdentityWinsAndDefaultsDoNotReplaceConfiguredValues() {
        var stand = java.util.Map.of("workloads.splitter-reactions.deployment", "configured-deployment",
                "workloads.splitter-reactions.configmap", "configured-rules");
        var plan = WorkedGroupProfile.workloadPlan("explab2984", true, "rest", k -> null, stand::get);
        assertEquals("configured-deployment", plan.deployment());
        assertEquals("configured-rules", plan.rulesMap());
        String key = "stand.check-workedgroup.workloads.fixture.deployment";
        try {
            config.services.core.StandSettings.installMissing(java.util.Map.of(key, "default"), k -> "");
            assertNull(System.getProperty(key), "Explicit blank must remain an error, not be silently replaced");
            config.services.core.StandSettings.installMissing(java.util.Map.of(key, "default"), k -> null);
            assertEquals("default", System.getProperty(key));
            config.services.core.StandSettings.installMissing(java.util.Map.of(key, "another"), k -> null);
            assertEquals("default", System.getProperty(key));
        } finally { System.clearProperty(key); }
    }
    @Test void legacyWorkedGroupKeepsItsExplicitOverrides() {
        var settings = java.util.Map.of("explab2690.reactions.stand.service", "legacy-service",
                "explab2885.reactions.stand.service", "precalc-service");
        var plan = WorkedGroupProfile.workloadPlan("explab2690", true, "rest", settings::get, k -> null);
        assertEquals("legacy-service", plan.target().service());
    }

    @Test void explicitServiceConfigCannotContradictKafkaMode() {
        String prefix = "profile-check.";
        var settings = java.util.Map.of(prefix + "configmap.service.enabled", "true",
                prefix + "configmap.service.api-config-load-key", "api-load");
        try {
            settings.forEach(System::setProperty);
            assertEquals("false", WorkedGroupProfile.serviceConfigValues(prefix, "kafka").get("api-load"));
            assertEquals("true", WorkedGroupProfile.serviceConfigValues(prefix, "rest").get("api-load"));
            System.setProperty(prefix + "configmap.service.api-config-load-value", "true");
            assertThrows(IllegalArgumentException.class, () -> WorkedGroupProfile.serviceConfigValues(prefix, "kafka"));
        } finally {
            settings.keySet().forEach(System::clearProperty);
            System.clearProperty(prefix + "configmap.service.api-config-load-value");
        }
    }
}
