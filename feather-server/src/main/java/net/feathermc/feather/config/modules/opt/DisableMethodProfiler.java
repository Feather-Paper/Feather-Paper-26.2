package net.feathermc.feather.config.modules.opt;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;

public class DisableMethodProfiler extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".disable-method-profiler";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
    }
}
