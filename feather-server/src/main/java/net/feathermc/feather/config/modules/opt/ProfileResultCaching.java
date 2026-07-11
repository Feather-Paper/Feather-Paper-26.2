package net.feathermc.feather.config.modules.opt;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;
import net.feathermc.feather.util.FeatherProfileCache;

public class ProfileResultCaching extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".profile-result-caching";
    }

    public static boolean enabled = true;
    public static long ttlSeconds = 1800L;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        ttlSeconds = config.getInt(getBasePath() + ".ttl-seconds", (int) ttlSeconds);
        FeatherProfileCache.configure(enabled, ttlSeconds);
    }
}
