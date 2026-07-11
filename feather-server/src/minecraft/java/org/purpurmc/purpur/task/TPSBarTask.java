package org.purpurmc.purpur.task;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.purpurmc.purpur.PurpurConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TPSBarTask extends BossBarTask {
    private static TPSBarTask instance;
    private double tps = 20.0D;
    private double mspt = 0.0D;
    private int tick = 0;

    public static TPSBarTask instance() {
        if (instance == null) {
            instance = new TPSBarTask();
        }
        return instance;
    }

    @Override
    BossBar createBossBar() {
        return BossBar.bossBar(Component.text(""), 0.0F, instance().getBossBarColor(), PurpurConfig.commandTPSBarProgressOverlay);
    }

    @Override
    void updateBossBar(BossBar bossbar, Player player) {
        // SparklyPaper start - track world's MSPT
        if (net.feathermc.feather.config.modules.async.SparklyPaperParallelWorldTicking.enabled) {
            // Get player's current world
            net.minecraft.server.level.ServerLevel serverLevel = ((org.bukkit.craftbukkit.CraftWorld) player.getWorld()).getHandle();

            // Calculate world-specific MSPT and TPS
            double worldMspt = calculateWorldMSPT(serverLevel);
            double worldTps = Math.min(20.0, 1000.0 / Math.max(worldMspt, 0.001)); // Avoid division by zero

            // Store original values
            double originalTps = this.tps;
            double originalMspt = this.mspt;

            try {
                // Temporarily set to world values
                this.tps = worldTps;
                this.mspt = worldMspt;

                // Create MSPT component with world name
                Component msptWithWorld = Component.empty()
                    .append(getMSPTColor())
                    .append(Component.text(" [" + player.getWorld().getName() + "]").color(net.kyori.adventure.text.format.NamedTextColor.GRAY));

                // Update the boss bar using the methods that depend on the fields
                bossbar.progress(getBossBarProgress());
                bossbar.color(getBossBarColor());
                bossbar.name(MiniMessage.miniMessage().deserialize(PurpurConfig.commandTPSBarTitle,
                    Placeholder.component("tps", getTPSColor()),
                    Placeholder.component("mspt", msptWithWorld),
                    Placeholder.component("ping", getPingColor(player.getPing()))
                ));
            } finally {
                // Restore original values
                this.tps = originalTps;
                this.mspt = originalMspt;
            }
            return;
        }
        // SparklyPaper end - track world's MSPT
        bossbar.progress(getBossBarProgress());
        bossbar.color(getBossBarColor());
        bossbar.name(MiniMessage.miniMessage().deserialize(PurpurConfig.commandTPSBarTitle,
                Placeholder.component("tps", getTPSColor()),
                Placeholder.component("mspt", getMSPTColor()),
                Placeholder.component("ping", getPingColor(player.getPing()))
        ));
    }

    @Override
    public void run() {
        if (++tick < PurpurConfig.commandTPSBarTickInterval) {
            return;
        }
        tick = 0;

        this.tps = Math.max(Math.min(Bukkit.getTPS()[0], 20.0D), 0.0D);
        this.mspt = Bukkit.getAverageTickTime();

        super.run();
    }

    private float getBossBarProgress() {
        if (PurpurConfig.commandTPSBarProgressFillMode == FillMode.MSPT) {
            return Math.max(Math.min((float) mspt / 50.0F, 1.0F), 0.0F);
        } else {
            return Math.max(Math.min((float) tps / 20.0F, 1.0F), 0.0F);
        }
    }

    private BossBar.Color getBossBarColor() {
        if (isGood(PurpurConfig.commandTPSBarProgressFillMode)) {
            return PurpurConfig.commandTPSBarProgressColorGood;
        } else if (isMedium(PurpurConfig.commandTPSBarProgressFillMode)) {
            return PurpurConfig.commandTPSBarProgressColorMedium;
        } else {
            return PurpurConfig.commandTPSBarProgressColorLow;
        }
    }

    private boolean isGood(FillMode mode) {
        return isGood(mode, 0);
    }

    private boolean isGood(FillMode mode, int ping) {
        if (mode == FillMode.MSPT) {
            return mspt < 40;
        } else if (mode == FillMode.TPS) {
            return tps >= 19;
        } else if (mode == FillMode.PING) {
            return ping < 100;
        } else {
            return false;
        }
    }

    private boolean isMedium(FillMode mode) {
        return isMedium(mode, 0);
    }

    private boolean isMedium(FillMode mode, int ping) {
        if (mode == FillMode.MSPT) {
            return mspt < 50;
        } else if (mode == FillMode.TPS) {
            return tps >= 15;
        } else if (mode == FillMode.PING) {
            return ping < 200;
        } else {
            return false;
        }
    }

    private Component getTPSColor() {
        String color;
        if (isGood(FillMode.TPS)) {
            color = PurpurConfig.commandTPSBarTextColorGood;
        } else if (isMedium(FillMode.TPS)) {
            color = PurpurConfig.commandTPSBarTextColorMedium;
        } else {
            color = PurpurConfig.commandTPSBarTextColorLow;
        }
        return MiniMessage.miniMessage().deserialize(color, Placeholder.parsed("text", String.format("%.2f", tps)));
    }

    private Component getMSPTColor() {
        String color;
        if (isGood(FillMode.MSPT)) {
            color = PurpurConfig.commandTPSBarTextColorGood;
        } else if (isMedium(FillMode.MSPT)) {
            color = PurpurConfig.commandTPSBarTextColorMedium;
        } else {
            color = PurpurConfig.commandTPSBarTextColorLow;
        }
        return MiniMessage.miniMessage().deserialize(color, Placeholder.parsed("text", String.format("%.2f", mspt)));
    }

    private Component getPingColor(int ping) {
        String color;
        if (isGood(FillMode.PING, ping)) {
            color = PurpurConfig.commandTPSBarTextColorGood;
        } else if (isMedium(FillMode.PING, ping)) {
            color = PurpurConfig.commandTPSBarTextColorMedium;
        } else {
            color = PurpurConfig.commandTPSBarTextColorLow;
        }
        return MiniMessage.miniMessage().deserialize(color, Placeholder.parsed("text", String.format("%s", ping)));
    }

    // SparklyPaper start - track world's MSPT
    private double calculateWorldMSPT(final net.minecraft.server.level.ServerLevel level) {
        long[] times = level.tickTimes5s.getTimes();
        long total = 0L;
        int count = 0;

        for (long value : times) {
            if (value > 0L) {
                total += value;
                count++;
            }
        }

        if (count == 0) return 0.0;

        return (double) total / (double) count * 1.0E-6D;
    }
    // SparklyPaper end - track world's MSPT

    public enum FillMode {
        TPS, MSPT, PING
    }
}
