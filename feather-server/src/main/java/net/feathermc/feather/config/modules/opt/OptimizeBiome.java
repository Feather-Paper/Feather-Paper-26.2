package net.feathermc.feather.config.modules.opt;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;

public class OptimizeBiome extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".cache-biome";
    }

    public static boolean enabled = false;
    public static boolean mobSpawn = false;
    public static boolean advancement = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        mobSpawn = config.getBoolean(getBasePath() + ".mob-spawning", false);
        advancement = config.getBoolean(getBasePath() + ".advancements", false);
    }
}
