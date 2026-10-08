package config.services.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusChange2972SettingsTest {
    @Test
    void capturesObservedStandUserWhenNoIndependentIdIsConfigured() {
        var settings = new StatusChange2972Settings();
        String key = "explab2972." + settings.env + ".auth.primary.user-id";
        settings.properties.remove(key);
        settings.properties.remove("explab2972.auth.primary.user-id");

        settings.observeUser("primary", 4321L);

        assertEquals(4321L, settings.user("primary"));
    }

    @Test
    void retainsIndependentCheckWhenStandUserIdIsConfigured() {
        var settings = new StatusChange2972Settings();
        String key = "explab2972." + settings.env + ".auth.primary.user-id";
        settings.properties.setProperty(key, "4321");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> settings.observeUser("primary", 9876L));

        assertTrue(failure.getMessage().contains("does not match"));
        assertEquals(4321L, settings.user("primary"));
    }

    @Test
    void rejectsInvalidObservedStandUserId() {
        var settings = new StatusChange2972Settings();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> settings.observeUser("primary", 0L));

        assertTrue(failure.getMessage().contains("invalid createdBy"));
    }

    @Test
    void rejectsAChangedIdentityAfterItWasObserved() {
        var settings = new StatusChange2972Settings();
        String key = "explab2972." + settings.env + ".auth.primary.user-id";
        settings.properties.remove(key);
        settings.properties.remove("explab2972.auth.primary.user-id");
        settings.observeUser("primary", 4321L);
        assertThrows(IllegalStateException.class, () -> settings.observeUser("primary", 9876L));
        assertEquals(4321L, settings.user("primary"));
    }

    @Test
    void respectsAnIndependentGlobalUserId() {
        var settings = new StatusChange2972Settings();
        String key = "explab2972." + settings.env + ".auth.primary.user-id";
        settings.properties.remove(key);
        settings.properties.setProperty("explab2972.auth.primary.user-id", "4321");
        assertThrows(IllegalStateException.class, () -> settings.observeUser("primary", 9876L));
        assertEquals(4321L, settings.user("primary"));
    }

    @Test
    void refusesToReplaceMalformedConfiguredIdentity() {
        var settings = new StatusChange2972Settings();
        String key = "explab2972." + settings.env + ".auth.primary.user-id";
        settings.properties.setProperty(key, "not-a-number");
        assertThrows(IllegalStateException.class, () -> settings.observeUser("primary", 4321L));
        assertEquals("not-a-number", settings.properties.getProperty(key));
    }

    @Test
    void keepsRoleIdentitiesIndependent() {
        var settings = new StatusChange2972Settings();
        for (String role : java.util.List.of("primary", "approver")) {
            settings.properties.remove("explab2972." + settings.env + ".auth." + role + ".user-id");
            settings.properties.remove("explab2972.auth." + role + ".user-id");
        }
        settings.observeUser("primary", 4321L);
        settings.observeUser("approver", 9876L);
        assertEquals(4321L, settings.user("primary"));
        assertEquals(9876L, settings.user("approver"));
    }
}
