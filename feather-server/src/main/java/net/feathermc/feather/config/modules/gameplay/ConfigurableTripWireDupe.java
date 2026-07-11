package net.feathermc.feather.config.modules.gameplay;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;

public class ConfigurableTripWireDupe extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".allow-tripwire-dupe", enabled);
    }
}
