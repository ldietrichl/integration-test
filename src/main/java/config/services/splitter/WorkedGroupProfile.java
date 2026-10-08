package config.services.splitter;

import config.services.core.StandSettings;
import infrastructure.kubernetes.ConfigMapState;
import infrastructure.kubernetes.WorkloadTarget;
import steps.container.KubernetesWorkloadSteps;
import flow.WorkloadFlow;
import io.qameta.allure.Allure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import config.services.core.TestConfigurationFiles;

/**
 * Shared stand preparation for worked-group mapper and reactions coverage.
 * It is opt-in from the Gradle ticket task and verifies every selected ConfigMap
 * against the live application Deployment before patching.
 */
public final class WorkedGroupProfile {
    public static void prepare(boolean reactions, boolean allowWithoutMain) {
        prepare("explab2690", reactions, allowWithoutMain, "rest", false, "splitter/EXPLAB_2690/configmap/");
    }

    /** Shared worked-group profile; existing callers retain their original settings. */
    public static void prepare(String namespace, boolean reactions, boolean allowWithoutMain,
                               String loadMode, boolean dynamicOnly, String rulesDirectory) {
        if (!java.util.Set.of("rest", "kafka").contains(loadMode))
            throw new IllegalArgumentException("Config load mode must be rest or kafka");
        String enabled = namespace + ".stand.config.enabled";
        boolean flagsManaged = flag(namespace + ".stand.application-flags.enabled", false);
        if ((!allowWithoutMain || dynamicOnly) && (!flagsManaged || !flag(enabled, false)))
            throw new IllegalStateException("Denied no-MAIN scenarios require managed application flags");
        if (!flag(enabled, false)) {
            Allure.parameter(enabled, "false");
            return;
        }
        String prefix = reactions ? namespace + ".reactions." : namespace + ".";
        String kind = reactions ? "reactions" : "mapper";
        String resource = prop(prefix + "configmap.rules.resource",
                rulesDirectory + kind + "-required.yml");
        StandSettings stand = new StandSettings();
        var properties = TestConfigurationFiles.load("test.properties");
        var plan = workloadPlan(namespace, reactions, loadMode,
                key -> System.getProperty(key, properties.getProperty(key)), key -> stand.optional(key, null));
        String workload = plan.target().workload();
        String service = plan.target().service();
        String rulesConfigMap = plan.rulesMap();
        String rulesKey = plan.rulesKey();
        String standPrefix = "stand." + stand.environment + ".";
        StandSettings.installMissing(StandSettings.workloadDefaults(stand.environment, workload,
                plan.deployment(), rulesConfigMap), key -> stand.optional(key.substring(standPrefix.length()), null));

        var states = new java.util.ArrayList<ConfigMapState>();
        states.add(ConfigMapState.resource(rulesConfigMap, rulesKey, resource));
        Map<String, String> serviceValues = serviceConfigValues(prefix, loadMode);
        if (!serviceValues.isEmpty()) {
            String serviceConfigMap = prop(prefix + "configmap.service.name",
                    stand.optional("workloads." + workload + ".service-configmap", service));
            states.add(new ConfigMapState(serviceConfigMap, serviceValues));
        }
        Allure.parameter(namespace + ".stand.workload", workload);
        Allure.parameter(namespace + ".configmap.rules.name", rulesConfigMap);
        KubernetesWorkloadSteps steps = new WorkloadFlow() { }.workloadSteps();
        WorkloadTarget target = plan.target();
        // Use the same gateway and service path as the business requests.
        var probe = infrastructure.kubernetes.WorkloadHttpReadinessProbe.ingressGet(
                config.services.core.RestEndpointResolver.baseUri(config.services.core.RestServiceEndpoint.EXPLAB_GATEWAY),
                reactions ? constants.SplitterEndpointPaths.reactionsVersion() : constants.SplitterEndpointPaths.mapperVersion());
        Allure.parameter(namespace + ".allow-result-without-main", flagsManaged ? allowWithoutMain : "UNMANAGED");
        new steps.container.WorkloadScenarioSteps(steps).prepare(target, session -> {
            var combined = new java.util.ArrayList<>(states);
            if (flagsManaged) {
                combined.addAll(session.environmentState(applicationFlags(prefix, allowWithoutMain, loadMode, dynamicOnly)));
            }
            return combined;
        }, probe, infrastructure.kubernetes.WorkloadScenarioEvidence.current(), false);
    }

    /** Only resource identity is reused; precalculation flags and rules belong to each scenario. */
    record WorkloadPlan(WorkloadTarget target, String deployment, String rulesMap, String rulesKey) { }

    static WorkloadPlan workloadPlan(String namespace, boolean reactions, String mode,
                                     Function<String, String> properties, Function<String, String> stand) {
        Function<String, String> inherited = "explab2690".equals(namespace)
                ? key -> key.startsWith("explab2690.") ? properties.apply(key) : null : properties;
        WorkloadPlan base;
        if (reactions) {
            var p = ReactionsPrecalcProfile.plan("", mode, inherited, stand, key -> null);
            base = new WorkloadPlan(p.target(), p.deployment(), p.rulesMap(), p.rulesKey());
        } else {
            var p = MapperPrecalcProfile.plan("", mode, inherited, stand, key -> null);
            base = new WorkloadPlan(p.target(), p.deployment(), p.rulesMap(), p.rulesKey());
        }
        String prefix = namespace + (reactions ? ".reactions." : ".");
        String workload = value(properties.apply(prefix + "stand.workload"), base.target().workload());
        String service = value(properties.apply(prefix + "stand.service"), base.target().service());
        String scope = "workloads." + workload + ".";
        String deployment = value(stand.apply(scope + "deployment"),
                value(properties.apply(prefix + "stand.deployment"), base.deployment()));
        String rules = value(properties.apply(prefix + "configmap.rules.name"),
                value(stand.apply(scope + "configmap"), base.rulesMap()));
        String container = value(properties.apply(prefix + "stand.container"),
                value(stand.apply(scope + "container"), base.target().container()));
        int port = Integer.parseInt(value(properties.apply(prefix + "stand.service-port"), Integer.toString(base.target().port())));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid workload service port");
        return new WorkloadPlan(new WorkloadTarget(workload, service, port, container), deployment, rules,
                value(properties.apply(prefix + "configmap.rules.key"), base.rulesKey()));
    }

    private static String value(String configured, String fallback) {
        return configured == null ? fallback : configured.trim();
    }

    static Map<String, String> applicationFlags(String prefix, boolean allowWithoutMain, String loadMode, boolean dynamicOnly) {
        Map<String, String> flags = new LinkedHashMap<>();
        Map.of("allow-result-without-main", Boolean.toString(allowWithoutMain),
                "all-rule-code-exp-enabled", "true", "empty-objects-response-enabled", "true",
                "return-suppressed", "true", "api-config-load", Boolean.toString(loadMode.equals("rest"))).forEach((key, value) ->
                flags.put(prop(prefix + "application-env." + key,
                        "SPLITTER_" + key.replace('-', '_').toUpperCase(java.util.Locale.ROOT)), value));
        if (dynamicOnly) flags.put(prop(prefix + "application-env.preliminary-calculation-enabled",
                "SPLITTER_PRELIMINARY_CALCULATION_ENABLED"), "false");
        return flags;
    }

    static Map<String, String> serviceConfigValues(String prefix, String mode) {
        if (!flag(prefix + "configmap.service.enabled", false)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        String propertyKey = prop(prefix + "configmap.service.api-config-load-key", "").trim();
        if (!propertyKey.isBlank()) {
            result.put(propertyKey, loadValue(prefix + "configmap.service.api-config-load-value", mode));
        }
        String envKey = prop(prefix + "configmap.service.api-config-load-env-key", "").trim();
        if (!envKey.isBlank()) {
            result.put(envKey, loadValue(prefix + "configmap.service.api-config-load-env-value", mode));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException(prefix + "configmap.service.enabled=true requires at least one service key");
        }
        return result;
    }

    private static String loadValue(String property, String mode) {
        String expected = Boolean.toString("rest".equals(mode));
        String actual = prop(property, expected);
        if (!expected.equals(actual)) throw new IllegalArgumentException(property + " contradicts config load mode " + mode);
        return actual;
    }

    private static String prop(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.trim().isBlank() ? fallback : value.trim();
    }

    private static boolean flag(String name, boolean fallback) {
        String value = prop(name, Boolean.toString(fallback));
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException(name + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static int number(String name, int fallback, int min, int max) {
        int value = Integer.parseInt(prop(name, Integer.toString(fallback)));
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " is out of range");
        }
        return value;
    }
}
