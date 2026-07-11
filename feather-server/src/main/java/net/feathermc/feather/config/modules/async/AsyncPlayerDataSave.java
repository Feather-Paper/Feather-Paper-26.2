package net.feathermc.feather.config.modules.async;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;

public class AsyncPlayerDataSave extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-playerdata-save";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Make PlayerData saving asynchronously.""",
            """
                异步保存玩家数据.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);

        if (enabled) {
            net.feathermc.feather.async.AsyncPlayerDataSaving.init();
        }
    }
}
