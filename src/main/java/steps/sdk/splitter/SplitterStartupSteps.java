package steps.sdk.splitter;

import org.junit.jupiter.api.*;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static steps.sdk.splitter.SplitterSdkAccess.*;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class SplitterStartupSteps {
    @TempDir public Path directory;

    public static Object mapperExecutor(Path rules) throws Throwable {
        Object props = create(ROOT + "config.props.SplitterConfigProperties");
        call(props, "setSplittingPoint", "MAPPER");
        call(props, "setQuantum", 10000);
        for (String setter : List.of("setApiConfigLoad", "setPreliminaryCalculationEnabled", "setAllRuleCodeExpEnabled",
                "setEmptyObjectsResponseEnabled", "setAllowResultWithoutMain")) call(props, setter, true);
        call(props, "setRulesConfigCode", code("SplitterRulesConfigCode", "MAPPER"));
        call(props, "setRulesFilePath", rules.toString());
        Object factory = call(type("jakarta.validation.Validation"), "buildDefaultValidatorFactory");
        try {
            Object validator = call(factory, "getValidator");
            Object configuration = create(ROOT + "config.SplitterAutoConfiguration");
            // Only the file-loader/validator is under test. Executor collaborators are not called by its constructor.
            var method = Arrays.stream(configuration.getClass().getMethods())
                    .filter(m -> m.getName().equals("splittingExecutor")).findFirst().orElseThrow();
            Object[] arguments = Arrays.stream(method.getParameterTypes())
                    .map(c -> c.isInstance(props) ? props : c.isInstance(validator) ? validator : org.mockito.Mockito.mock(c))
                    .toArray();
            return call(configuration, "splittingExecutor", arguments);
        } finally { call(factory, "close"); }
    }

    public static void binding(Map<String,Object> properties) throws Throwable {
        binding(properties, "SplitterConfigProperties");
    }
    public static void binding(Map<String,Object> properties, String propertiesClass) throws Throwable {
        Object context = create("org.springframework.context.annotation.AnnotationConfigApplicationContext");
        try {
            Object source = type("org.springframework.core.env.MapPropertySource")
                    .getConstructor(String.class, Map.class).newInstance("2891", properties);
            Object environment = call(context, "getEnvironment");
            Object sources = call(environment, "getPropertySources");
            // Remove machine environment input: each case has an isolated, deterministic set of properties.
            call(sources, "remove", "systemProperties");
            call(sources, "remove", "systemEnvironment");
            call(sources, "addFirst", source);
            call(type("org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor"), "register", context);
            Class<?> beanType = type(ROOT + "config.props." + propertiesClass);
            call(context, "register", (Object) new Class<?>[]{beanType});
            call(context, "refresh");
            Object bean = call(context, "getBean", beanType);
            if (propertiesClass.equals("SplitterConfigProperties")) assertEquals("MAPPER", call(bean, "getSplittingPoint"));
            else assertNotNull(call(bean, "getTopic"));
        } finally { call(context, "close"); }
    }

    public static Map<String,Object> properties() {
        Map<String,Object> map = new LinkedHashMap<>();
        map.put("splitter.config.splitting-point", "MAPPER");
        map.put("splitter.config.quantum", "10000");
        map.put("splitter.config.rules-config-code", "MAPPER");
        for (String key : List.of("api-config-load", "preliminary-calculation-enabled", "all-rule-code-exp-enabled",
                "empty-objects-response-enabled", "allow-result-without-main")) map.put("splitter.config." + key, "true");
        return map;
    }
    public static String chain(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = failure; t != null && seen.add(t); t = t.getCause()) text.append(t).append('\n');
        return text.toString();
    }
}
