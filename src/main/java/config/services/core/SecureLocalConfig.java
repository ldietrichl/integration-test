package config.services.core;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Reloadable;

// Secret values are literal; dollar signs/braces must not be interpreted as Owner variables.
@Config.DisableFeature(Config.DisableableFeature.VARIABLE_EXPANSION)
@LoadPolicy(Config.LoadType.MERGE)
@Sources({
        "file:secure.local.override.properties",
        "file:secure.users.local.override.properties"
})
public interface SecureLocalConfig extends Config, Accessible, Reloadable {
}
