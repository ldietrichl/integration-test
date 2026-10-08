package config.services.splitter;

import java.lang.annotation.*;

@Inherited @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface WorkedGroupPolicy {
    boolean allowWithoutMain() default true;
}
