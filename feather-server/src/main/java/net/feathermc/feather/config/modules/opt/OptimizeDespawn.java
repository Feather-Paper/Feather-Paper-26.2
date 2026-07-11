package net.feathermc.feather.config.modules.opt;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;
import net.feathermc.feather.config.annotations.Experimental;
import net.feathermc.feather.util.LeafConstants;

public class OptimizeDespawn extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".optimize-mob-despawn";
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled);
        if (enabled) {
            if (!LeafConstants.ENABLE_FMA) {
                LOGGER.info("NOTE: Recommend enabling FMA to work with optimize-mob-despawn.");
            }
        }
    }
}
