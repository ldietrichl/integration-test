package util.ignite;

import config.services.core.TestEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Build entry point only. Compiles helpers, never opens a client or invokes a helper command. */
public final class IgniteRuntimePreparation {
    private IgniteRuntimePreparation() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !List.of("probe", "data-operator").contains(args[0])) {
            throw new IllegalArgumentException("Expected probe or data-operator");
        }
        boolean dataOperator = "data-operator".equals(args[0]);
        var profile = new IgniteProfile(TestEnvironment.current(), new EnvironmentProperties("ignite.properties"));
        List<String> names = dataOperator
                ? List.of("LinksCacheSchema.java", "LinksCacheTool.java")
                : List.of("IgniteClientSupport.java", "IgniteConnectionProbe.java");
        Path bundle = profile.runtimeDirectory();
        long present = names.stream().filter(name -> Files.isRegularFile(bundle.resolve(name))).count();
        if (present != 0 && present != names.size()) {
            throw new IllegalStateException("Incomplete helper source bundle: " + bundle);
        }
        Path source = present == names.size() ? bundle
                : Path.of(dataOperator ? "tools/data-operator-explab-2974" : "tools/ignite-client");
        IgniteClientRuntime.prepare(bundle, names.stream().map(source::resolve).toList(),
                dataOperator ? "LinksCacheTool" : "IgniteConnectionProbe");
        System.out.println("IgniteHelpersPrepared=true; StandConnections=0; mode=" + args[0]);
    }
}
