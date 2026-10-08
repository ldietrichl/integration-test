package config.extensions;

import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

/** Opt in to per-scenario evidence for any workload, independent of a service or ticket. */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith({WorkloadRunScopeExtension.class, WorkloadScenarioEvidenceExtension.class})
public @interface WorkloadScenario {
    String value();
}
