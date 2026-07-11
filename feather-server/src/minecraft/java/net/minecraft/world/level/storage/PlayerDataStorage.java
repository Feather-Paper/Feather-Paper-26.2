package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class PlayerDataStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;
    private final java.util.Map<java.util.UUID, java.util.concurrent.Future<?>> savingLocks = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(); // Leaf - Async playerdata saving

    public PlayerDataStorage(final LevelStorageSource.LevelStorageAccess levelAccess, final DataFixer fixerUpper) {
        this.fixerUpper = fixerUpper;
        this.playerDir = levelAccess.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(final Player player) {
        if (org.spigotmc.SpigotConfig.disablePlayerDataSaving) return; // Spigot
        if (net.feathermc.feather.config.modules.misc.RegionFormatConfig.isReadOnlyMode()) return; // Leaf - Add read-only mode for Linear v2
        // Leaf start - Async playerdata saving
        CompoundTag dataToStore;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(player.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, player.registryAccess());
            player.saveWithoutId(output);
            dataToStore = output.buildResult();
        } catch (Exception exception) {
            LOGGER.warn("Failed to encode player data for {}", player.getPlainTextName(), exception);
            return;
        }
        save(player.getScoreboardName(), player.getUUID(), player.getStringUUID(), dataToStore);
        // Leaf end - Async playerdata saving
    }

    // Leaf start - Async playerdata saving
    public void save(final String playerName, final java.util.UUID uniqueId, final String stringId, final CompoundTag compoundTag) {
        var nbtBytes = new it.unimi.dsi.fastutil.io.FastByteArrayOutputStream(65536);
        try {
            NbtIo.writeCompressed(compoundTag, nbtBytes);
        } catch (Exception exception) {
            LOGGER.warn("Failed to encode player data for {}", stringId, exception);
        }
        lockFor(uniqueId, playerName);
        synchronized (PlayerDataStorage.this) {
            net.feathermc.feather.async.AsyncPlayerDataSaving.submit(() -> {
                try {
                    Path playerDirPath = this.playerDir.toPath();
                    Path tmpFile = Files.createTempFile(playerDirPath, stringId + "-", ".dat");
                    org.apache.commons.io.FileUtils.writeByteArrayToFile(tmpFile.toFile(), nbtBytes.array, 0, nbtBytes.length, false);
                    Path realFile = playerDirPath.resolve(stringId + ".dat");
                    Path oldFile = playerDirPath.resolve(stringId + ".dat_old");
                    Util.safeReplaceFile(realFile, tmpFile, oldFile);
                } catch (Exception var7) {
                    LOGGER.warn("Failed to save player data for {}", playerName, var7);
                } finally {
                    synchronized (PlayerDataStorage.this) {
                        savingLocks.remove(uniqueId);
                    }
                }
            }).ifPresent(future -> savingLocks.put(uniqueId, future));
        }
    }

    private void lockFor(final java.util.UUID uniqueId, final String playerName) {
        java.util.concurrent.Future<?> fut;
        synchronized (this) {
            fut = savingLocks.get(uniqueId);
        }
        if (fut == null) {
            return;
        }
        while (true) {
            try {
                fut.get(10_000L, java.util.concurrent.TimeUnit.MILLISECONDS);
                break;
            } catch (InterruptedException ignored) {
            } catch (java.util.concurrent.ExecutionException
                     | java.util.concurrent.TimeoutException exception) {
                LOGGER.warn("Failed to save player data for {}", playerName, exception);

                String threadDump = "";
                var threadMXBean = java.lang.management.ManagementFactory.getThreadMXBean();
                for (var threadInfo : threadMXBean.dumpAllThreads(true, true)) {
                    if (threadInfo.getThreadName().equals("Leaf IO Thread")) { // TODO: We should use instanceOf here
                        threadDump = threadInfo.toString();
                        break;
                    }
                }
                LOGGER.warn(threadDump);
                fut.cancel(true);
                break;
            } finally {
                savingLocks.remove(uniqueId);
            }
        }
    }
    // Leaf end - Async playerdata saving

    private void backup(final NameAndId nameAndId, final String suffix) {
        Path playerDirPath = this.playerDir.toPath();
        String idString = nameAndId.id().toString();
        Path realPath = playerDirPath.resolve(idString + suffix);
        Path backupPath = playerDirPath.resolve(idString + "_corrupted_" + ZonedDateTime.now().format(FileNameDateFormatter.FORMATTER) + suffix);
        if (Files.isRegularFile(realPath)) {
            try {
                Files.copy(realPath, backupPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception e) {
                LOGGER.warn("Failed to copy the player.dat file for {}", nameAndId.name(), e);
            }
        }
    }

    private Optional<CompoundTag> load(final NameAndId nameAndId, final String suffix) {
        lockFor(nameAndId.id(), nameAndId.name()); // Leaf - Async playerdata saving
        File realFile = new File(this.playerDir, nameAndId.id() + suffix);
        // Spigot start
        boolean usingWrongFile = false;
        if (org.bukkit.Bukkit.getOnlineMode() && !realFile.exists()) { // Paper - Check online mode first
            realFile = new File(this.playerDir, net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(nameAndId.name()) + suffix);
            if (realFile.exists()) {
                usingWrongFile = true;
                LOGGER.warn("Using offline mode UUID file for player {} as it is the only copy we can find.", nameAndId.name());
            }
        }
        // Spigot end
        if (realFile.exists() && realFile.isFile()) {
            try {
                // Spigot start
                Optional<CompoundTag> optional = Optional.of(NbtIo.readCompressed(realFile.toPath(), NbtAccounter.unlimitedHeap()));
                if (usingWrongFile) {
                    realFile.renameTo(new File(realFile.getPath() + ".offline-read"));
                }
                return optional;
                // Spigot end
            } catch (Exception ignored) {
                LOGGER.warn("Failed to load player data for {}", nameAndId.name());
            }
        }

        return Optional.empty();
    }

    public Optional<CompoundTag> load(final NameAndId nameAndId) {
        Optional<CompoundTag> optTag = this.load(nameAndId, ".dat");
        if (optTag.isEmpty()) {
            this.backup(nameAndId, ".dat");
        }

        return optTag.or(() -> this.load(nameAndId, ".dat_old")).map(tag -> {
            int version = NbtUtils.getDataVersion(tag);
            return ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.PLAYER, tag, version, ca.spottedleaf.dataconverter.minecraft.util.Version.getCurrentVersion());
        });
    }

    // CraftBukkit start
    public File getPlayerDir() {
        return this.playerDir;
    }
    // CraftBukkit end
}
