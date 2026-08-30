package config.services.core;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Reloadable;

@LoadPolicy(Config.LoadType.MERGE)
@Sources({
        "system:properties",
        "system:env",
        "file:secure.local.override.properties",
        "file:secure.local.properties"
})
public interface SecureLocalConfig extends Config, Accessible, Reloadable {
}
