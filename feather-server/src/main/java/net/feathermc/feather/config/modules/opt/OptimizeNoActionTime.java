package net.feathermc.feather.config.modules.opt;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;
import net.feathermc.feather.config.annotations.Experimental;

public class OptimizeNoActionTime extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".optimize-no-action-time";
    }

    @Experimental
    public static boolean disableLightCheck = false;

    @Override
    public void onLoaded() {
        disableLightCheck = config.getBoolean(getBasePath() + ".disable-light-check", disableLightCheck);
    }
}
