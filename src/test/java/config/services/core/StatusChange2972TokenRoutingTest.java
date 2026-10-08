package config.services.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests selection only; uses synthetic values and never sends a request. */
class StatusChange2972TokenRoutingTest {
    private StatusChange2972Settings settings() {
        var s = new StatusChange2972Settings();
        s.properties.setProperty("explab2972." + s.env + ".auth.enabled", "true");
        s.properties.setProperty("rest.explab-gateway.token", "synthetic-global");
        s.properties.setProperty("rest.configuration-service.token", "synthetic-configuration");
        return s;
    }
    @Test void scopedPrimaryOverridesGlobalGatewayIdentity() {
        var s = settings();
        s.properties.setProperty("explab2972." + s.env + ".auth.primary.token", "synthetic-mapper");
        assertEquals("synthetic-mapper", s.token("primary", false));
    }
    @Test void roleTokensRemainIndependent() {
        var s = settings();
        s.properties.setProperty("explab2972." + s.env + ".auth.primary.token", "synthetic-mapper");
        s.properties.setProperty("explab2972." + s.env + ".auth.approver.token", "synthetic-validator");
        assertEquals("synthetic-validator", s.token("approver", false));
        assertEquals("synthetic-mapper", s.token("primary", false));
    }
    @Test void blankScopedTokenCannotFallBackToGlobalIdentity() {
        var s = settings();
        s.properties.setProperty("explab2972." + s.env + ".auth.primary.token", "");
        assertThrows(IllegalStateException.class, () -> s.token("primary", false));
    }
    @Test void corporateConfigurationRetainsItsOwnCredential() {
        var s = settings();
        org.junit.jupiter.api.Assumptions.assumeFalse(s.env.equals("local"));
        s.properties.setProperty("explab2972." + s.env + ".auth.primary.token", "synthetic-mapper");
        assertEquals("synthetic-configuration", s.token("primary", true));
    }
    @Test void absentScopedTokenKeepsLegacyCorporateFallback() {
        var s = settings();
        org.junit.jupiter.api.Assumptions.assumeFalse(s.env.equals("local"));
        s.properties.remove("explab2972." + s.env + ".auth.primary.token");
        s.properties.remove("explab2972.auth.primary.token");
        assertEquals("synthetic-global", s.token("primary", false));
    }
}
