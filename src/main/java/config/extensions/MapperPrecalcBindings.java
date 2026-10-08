package config.extensions;

import config.services.splitter.MapperPrecalcProfile;

import config.services.core.StandSettings;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Supplies the same workload defaults as EXPLAB_2690's tickets.gradle.kts for standard test/IDE runs. */
public final class MapperPrecalcBindings implements BeforeEachCallback {
    @Override public void beforeEach(ExtensionContext context) {
        var stand = new StandSettings();
        var plan = MapperPrecalcProfile.configuredPlan("", stand);
        String prefix = "stand." + stand.environment + ".";
        installMissing(defaults(stand.environment, plan), key -> stand.optional(key.substring(prefix.length()), null));
    }

    public static Map<String, String> defaults(String environment, MapperPrecalcProfile.Plan plan) {
        return StandSettings.workloadDefaults(environment, plan.target().workload(), plan.deployment(), plan.rulesMap());
    }

    public static synchronized void installMissing(Map<String, String> defaults, Function<String, String> configured) {
        StandSettings.installMissing(defaults, configured);
    }
}
