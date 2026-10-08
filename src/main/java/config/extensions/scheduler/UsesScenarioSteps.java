package config.extensions.scheduler;
import java.lang.annotation.*;
/** Connects thin scenario methods to same-named source-backed step methods for report discovery. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UsesScenarioSteps { Class<?> value(); }
