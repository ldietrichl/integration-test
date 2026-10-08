package config.services.splitter;
import infrastructure.kubernetes.WorkloadScenarioEvidence;

import config.services.core.*;
import constants.SplitterEndpointPaths;
import infrastructure.kubernetes.*;

import io.qameta.allure.Allure;
import steps.container.KubernetesWorkloadSteps;

import java.util.*;
import java.util.function.Function;

/** Uses the corporate EXPLAB_2690 lifecycle; all Kubernetes operations belong to shared Fabric8 services. */
public final class ReactionsPrecalcProfile {
    public static final String RULES = """
            rules:
              final-exp-rule:
                rule-code: MAIN
                proc-code: finalExpByLayerAndId
                proc-params:
                  max-layer-priority: true
                  max-id: false
            """;
    public record Plan(WorkloadTarget target, String deployment, String rulesMap, String rulesKey,
                Map<String, String> environment, boolean fresh) { }
    private final KubernetesWorkloadSteps steps;
    private final KubernetesWorkloadControl session;

    public ReactionsPrecalcProfile(KubernetesWorkloadSteps steps, KubernetesWorkloadControl session) {
        this.steps = steps;
        this.session = session;
    }

    public static Plan configuredPlan(String profile, StandSettings stand) {
        var properties = TestConfigurationFiles.load("test.properties");
        Function<String, String> property = key -> System.getProperty(key, properties.getProperty(key));
        return plan(profile, Objects.toString(property.apply("splitter.config.load.mode"), "rest"),
                property, key -> stand.optional(key, null), System::getenv);
    }

    public static ReactionsPrecalcProfile prepare(String profile, WorkloadScenarioEvidence diagnostics) {
        var stand = new StandSettings();
        var plan = configuredPlan(profile, stand);
        Allure.parameter("EXPLAB-2885 workload", plan.target().workload());
        Allure.parameter("EXPLAB-2885 deployment", stand.required("workloads." + plan.target().workload() + ".deployment"));
        var steps = new flow.WorkloadFlow() { }.workloadSteps();
        var probe = WorkloadHttpReadinessProbe.ingressGet(RestEndpointResolver.baseUri(RestServiceEndpoint.EXPLAB_GATEWAY),
                SplitterEndpointPaths.reactionsVersion());
        var session = new steps.container.WorkloadScenarioSteps(steps).prepare(plan.target(),
                selected -> desiredState(plan, selected), probe, diagnostics, plan.fresh());
        return new ReactionsPrecalcProfile(steps, session);
    }

    public static List<ConfigMapState> desiredState(Plan plan, KubernetesWorkloadControl session) {
        // Deployment env/configMapKeyRef and envFrom are resolved by the common mechanism.
        var states = new ArrayList<>(session.environmentState(plan.environment()));
        states.add(new ConfigMapState(plan.rulesMap(), Map.of(plan.rulesKey(), RULES)));
        return merge(states);
    }

    public static List<ConfigMapState> merge(List<ConfigMapState> states) {
        return WorkloadStates.merge(states);
    }

    public void restart() {
        new steps.container.WorkloadScenarioSteps(steps).restart(session);
    }

    public static Plan plan(String profile, String mode, Function<String, String> properties,
                     Function<String, String> stand, Function<String, String> environment) {
        if (!Set.of("", "OFF", "MATRIX", "FRESH", "NO_TABLE", "MONITOR_FRESH", "MONITOR_NO_CONFIG",
                "MONITOR_OFF", "RESTART", "DUPLICATES").contains(profile))
            throw new IllegalArgumentException("Unknown EXPLAB_2885_PROFILE: " + profile);
        mode = mode.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("rest", "kafka").contains(mode)) throw new IllegalArgumentException("Unknown config load mode: " + mode);
        Function<String, String> setting = suffix -> first(properties.apply("explab2885.reactions." + suffix),
                properties.apply("explab2690.reactions." + suffix));
        String workload = first(setting.apply("stand.workload"), "splitter-reactions");
        String service = first(setting.apply("stand.service"), stand.apply("services.splitter-reactions.name"), "splitter-reactions-service");
        String container = first(setting.apply("stand.container"), stand.apply("workloads." + workload + ".container"), service);
        var target = new WorkloadTarget(workload, service, Integer.parseInt(first(setting.apply("stand.service-port"), "8080")), container);
        String deployment = first(stand.apply("workloads." + workload + ".deployment"), setting.apply("stand.deployment"), service);
        String rulesMap = first(setting.apply("configmap.rules.name"), stand.apply("workloads." + workload + ".configmap"), service + "-lib");
        String rulesKey = first(setting.apply("configmap.rules.key"), "splitter-rules-reactions.yml");
        Map<String, String> flags = new LinkedHashMap<>();
        flags.put("preliminary-calculation-enabled", Boolean.toString(!Set.of("OFF", "MONITOR_OFF").contains(profile)));
        flags.put("api-config-load", Boolean.toString(mode.equals("rest")));
        flags.put("allow-result-without-main", matrix(profile, environment, "EXPLAB_2885_ALLOW_WITHOUT_MAIN", "false"));
        flags.put("all-rule-code-exp-enabled", matrix(profile, environment, "EXPLAB_2885_ALL", "true"));
        flags.put("empty-objects-response-enabled", matrix(profile, environment, "EXPLAB_2885_EMPTY_OBJECTS", "true"));
        Map<String, String> env = new LinkedHashMap<>();
        flags.forEach((key, value) -> {
            String name = first(setting.apply("application-env." + key), "SPLITTER_" + key.replace('-', '_').toUpperCase(Locale.ROOT));
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid application environment name: " + name);
            if (env.putIfAbsent(name, value) != null) throw new IllegalArgumentException("Duplicate application environment mapping: " + name);
        });
        return new Plan(target, deployment, rulesMap, rulesKey, Collections.unmodifiableMap(env),
                Set.of("FRESH", "NO_TABLE", "MONITOR_FRESH", "MONITOR_NO_CONFIG").contains(profile));
    }

    private static String matrix(String profile, Function<String, String> env, String key, String fallback) {
        if (!"MATRIX".equals(profile)) return fallback;
        String value = env.apply(key);
        if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("Set " + key + "=true|false");
        return value;
    }
    private static String first(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty()).findFirst().orElse(null);
    }
}
