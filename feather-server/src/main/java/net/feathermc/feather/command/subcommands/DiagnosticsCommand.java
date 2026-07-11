package net.feathermc.feather.command.subcommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import net.feathermc.feather.command.FeatherCommand;
import net.feathermc.feather.command.PermissionedFeatherSubcommand;
import net.feathermc.feather.config.FeatherConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class DiagnosticsCommand extends PermissionedFeatherSubcommand {

    public static final String LITERAL_ARGUMENT = "diagnostics";
    public static final String PERM = FeatherCommand.BASE_PERM + "." + LITERAL_ARGUMENT;
    private static final DecimalFormat DF_MSPT = new DecimalFormat("########0.0");
    private static final DecimalFormat DF_TPS = new DecimalFormat("0.00");
    private static final DecimalFormat DF_CPU = new DecimalFormat("0.0");

    public DiagnosticsCommand() {
        super(PERM, PermissionDefault.OP);
    }

    @Override
    public boolean execute(final CommandSender sender, final String subCommand, final String[] args) {
        MinecraftServer server = MinecraftServer.getServer();

        // # fetch tps
        double[] tps = Bukkit.getTPS();
        String tps5sStr = "N/A";
        String tps1mStr = "N/A";
        String tps5mStr = "N/A";
        String tps15mStr = "N/A";

        if (tps.length == 4) {
            tps5sStr = formatTPS(tps[0]);
            tps1mStr = formatTPS(tps[1]);
            tps5mStr = formatTPS(tps[2]);
            tps15mStr = formatTPS(tps[3]);
        } else if (tps.length >= 3) {
            tps1mStr = formatTPS(tps[0]);
            tps5mStr = formatTPS(tps[1]);
            tps15mStr = formatTPS(tps[2]);
        }

        // # mspt
        ca.spottedleaf.common.time.TickData tickData = server.tickTimes5s;
        ca.spottedleaf.common.time.TickData.TickReportData reportData = tickData.generateTickReport(null, System.nanoTime(), server.tickRateManager().nanosecondsPerTick());
        double avgMspt = reportData == null ? 0.0 : reportData.timePerTickData().segmentAll().average() * 1.0E-6D;
        double minMspt = reportData == null ? 0.0 : reportData.timePerTickData().segmentAll().least() * 1.0E-6D;
        double maxMspt = reportData == null ? 0.0 : reportData.timePerTickData().segmentAll().greatest() * 1.0E-6D;

        // # cpu
        double systemCpu = -1.0;
        double processCpu = -1.0;
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOs = (com.sun.management.OperatingSystemMXBean) os;
            systemCpu = sunOs.getCpuLoad() * 100.0;
            processCpu = sunOs.getProcessCpuLoad() * 100.0;
        }

        // # memory
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        // # chunks and entities
        int totalChunks = 0;
        int totalEntities = 0;
        List<Component> worldLines = new ArrayList<>();
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            int chunks = level.getChunkSource().fullChunks.size();
            int entities = level.getEntityCount();
            totalChunks += chunks;
            totalEntities += entities;
            worldLines.add(text(String.format("      - %s: ", level.getWorld().getName()), GOLD)
                .append(text(chunks + " chunks", GREEN))
                .append(text(", ", GRAY))
                .append(text(entities + " entities", GREEN)));
        }

        // # startup time
        long startupTime = FeatherConfig.startupTimeMs;
        String startupStr = startupTime > 0 ? DF_MSPT.format(startupTime / 1000.0) + "s" : "N/A";

        sender.sendMessage(text("━━━━━━━━━━━━━ ", GOLD).append(text("Feather Diagnostics", AQUA)).append(text(" ━━━━━━━━━━━━━", GOLD)));

        Component tpsComp = text("  - Live TPS: ", GOLD);
        if (tps.length == 4) {
            tpsComp = tpsComp.append(text("5s: ", GRAY)).append(getColoredTPS(tps[0], tps5sStr)).append(text(" | ", GRAY));
        }
        tpsComp = tpsComp.append(text("1m: ", GRAY)).append(getColoredTPS(tps[0], tps1mStr)).append(text(" | ", GRAY))
            .append(text("5m: ", GRAY)).append(getColoredTPS(tps[1], tps5mStr)).append(text(" | ", GRAY))
            .append(text("15m: ", GRAY)).append(getColoredTPS(tps[2], tps15mStr));
        sender.sendMessage(tpsComp);

        sender.sendMessage(text("  - MSPT: ", GOLD)
            .append(text("Avg: ", GRAY)).append(getColoredMSPT(avgMspt))
            .append(text(" | ", GRAY))
            .append(text("Min: ", GRAY)).append(getColoredMSPT(minMspt))
            .append(text(" | ", GRAY))
            .append(text("Max: ", GRAY)).append(getColoredMSPT(maxMspt)));

        sender.sendMessage(text("  - CPU Usage: ", GOLD)
            .append(text("JVM Process: ", GRAY)).append(text(DF_CPU.format(processCpu) + "%", GREEN))
            .append(text(" | ", GRAY))
            .append(text("System Total: ", GRAY)).append(text(DF_CPU.format(systemCpu) + "%", GREEN)));

        sender.sendMessage(text("  - Memory Usage: ", GOLD)
            .append(text("Used: ", GRAY)).append(text((usedMemory / 1024 / 1024) + " MB", GREEN))
            .append(text(" / Max: ", GRAY)).append(text((maxMemory / 1024 / 1024) + " MB", GREEN))
            .append(text(" (Allocated: " + (totalMemory / 1024 / 1024) + " MB)", GRAY)));

        sender.sendMessage(text("  - Loaded Chunks & Entities: ", GOLD)
            .append(text("Total Chunks: ", GRAY)).append(text(totalChunks, GREEN))
            .append(text(" | ", GRAY))
            .append(text("Total Entities: ", GRAY)).append(text(totalEntities, GREEN)));

        sender.sendMessage(text("      World Breakdown:", GRAY));
        for (Component wl : worldLines) {
            sender.sendMessage(wl);
        }

        sender.sendMessage(text("  - Startup Benchmark: ", GOLD)
            .append(text("Done in: ", GRAY)).append(text(startupStr, GREEN)));

        sender.sendMessage(text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", GOLD));

        return true;
    }

    private String formatTPS(double tpsVal) {
        return DF_TPS.format(Math.min(20.0, tpsVal));
    }

    private Component getColoredTPS(double val, String formatted) {
        NamedTextColor color = GREEN;
        if (val < 16.0) {
            color = RED;
        } else if (val < 18.5) {
            color = GOLD;
        } else if (val < 19.5) {
            color = YELLOW;
        }
        return text(formatted, color);
    }

    private Component getColoredMSPT(double value) {
        return text(DF_MSPT.format(value) + "ms",
            value >= 50 ? RED :
                value >= 40 ? YELLOW :
                    value >= 30 ? GOLD :
                        value >= 20 ? GREEN :
                            AQUA);
    }
}
