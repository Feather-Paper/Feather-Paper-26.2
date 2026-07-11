package net.feathermc.feather.config.modules.opt;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;
import net.feathermc.feather.util.FeatherFastCollections;

public class FastCollectionsOptimization extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".fast-collections";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        FeatherFastCollections.setFastCollectionsEnabled(enabled);
    }
}
