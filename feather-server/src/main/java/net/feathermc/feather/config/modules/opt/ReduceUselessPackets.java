package net.feathermc.feather.config.modules.opt;

import net.feathermc.feather.config.ConfigModules;
import net.feathermc.feather.config.EnumConfigCategory;

public class ReduceUselessPackets extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".reduce-packets";
    }

    public static boolean reduceUselessEntityMovePackets = false;
    public static boolean filterClientboundSetEntityMotionPacket = false;
    public static boolean disableUselessParticles = false;

    @Override
    public void onLoaded() {
        reduceUselessEntityMovePackets = config.getBoolean(getBasePath() + ".reduce-entity-move-packets", reduceUselessEntityMovePackets);
        filterClientboundSetEntityMotionPacket = config.getBoolean(getBasePath() + ".reduce-entity-motion-packets", filterClientboundSetEntityMotionPacket);
        disableUselessParticles = config.getBoolean(getBasePath() + ".disable-useless-particles", disableUselessParticles);
    }
}
