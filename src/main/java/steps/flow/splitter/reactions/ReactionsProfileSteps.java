package steps.flow.splitter.reactions;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;

import io.qameta.allure.Allure;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class ReactionsProfileSteps extends ReactionsPrecalcSteps {
    @Override public String managedProfile() { return java.util.Objects.toString(System.getenv("EXPLAB_2885_PROFILE"), ""); }
    public static void profile(String expected) {
        assumeTrue(expected.equals(System.getenv("EXPLAB_2885_PROFILE")), "Requires EXPLAB_2885_PROFILE=" + expected);
        Allure.parameter("EXPLAB_2885_PROFILE", expected);
    }
    public static boolean requiredFlag(String name) {
        String value = System.getenv(name);
        assertTrue("true".equals(value) || "false".equals(value), "Set " + name + "=true|false to the effective stand value");
        Allure.parameter(name, value); return Boolean.parseBoolean(value);
    }
}
