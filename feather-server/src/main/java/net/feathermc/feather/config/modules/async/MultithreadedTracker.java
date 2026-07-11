package net.feathermc.feather.config.modules.async;

import net.feathermc.feather.async.tracker.AsyncTracker;
import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;
import net.feathermc.feather.config.FeatherConfig;
import net.feathermc.feather.config.annotations.Experimental;

public class MultithreadedTracker extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-entity-tracker";
    }

    @Experimental
    public static boolean enabled = false;
    public static int threads = 0;
    private static boolean asyncMultithreadedTrackerInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                ** Experimental Feature **
                Make entity tracking asynchronously, can improve performance significantly,
                especially in some massive entities in small area situations.""", """
                ** 实验性功能 **
                异步实体跟踪,
                在实体数量多且密集的情况下效果明显.""");

        if (asyncMultithreadedTrackerInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncMultithreadedTrackerInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", false);
        threads = config.getInt(getBasePath() + ".threads", 0);

        if (threads <= 0) {
            threads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        }
        threads = Math.max(threads, 1);

        if (enabled) {
            FeatherConfig.LOGGER.info("Using {} threads for Async Entity Tracker", threads);
            AsyncTracker.init();
        }
    }
}
