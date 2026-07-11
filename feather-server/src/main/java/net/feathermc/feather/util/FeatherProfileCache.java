package net.feathermc.feather.util;

import com.mojang.authlib.GameProfile;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class FeatherProfileCache {

    private static final long DEFAULT_TTL_MILLIS = 30L * 60L * 1000L; // 30 min default
    private static final Map<UUID, CachedProfileEntry> CACHE = new ConcurrentHashMap<>();
    private static boolean enabled = true;
    private static long ttlMillis = DEFAULT_TTL_MILLIS;

    private FeatherProfileCache() {}

    public static void configure(boolean isEnabled, long ttlSeconds) {
        enabled = isEnabled;
        ttlMillis = Math.max(1L, ttlSeconds) * 1000L;
        if (!enabled) {
            CACHE.clear();
        }
    }

    public static @Nullable GameProfile getCachedProfile(UUID uuid) {
        if (!enabled || uuid == null) {
            return null;
        }
        CachedProfileEntry entry = CACHE.get(uuid);
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMillis) {
                return entry.profile;
            } else {
                CACHE.remove(uuid, entry);
            }
        }
        return null;
    }

    public static void putCachedProfile(UUID uuid, GameProfile profile) {
        if (!enabled || uuid == null || profile == null) {
            return;
        }
        CACHE.put(uuid, new CachedProfileEntry(profile, System.currentTimeMillis()));
    }

    public static void invalidate(UUID uuid) {
        if (uuid != null) {
            CACHE.remove(uuid);
        }
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    private static final class CachedProfileEntry {
        final GameProfile profile;
        final long timestamp;

        CachedProfileEntry(GameProfile profile, long timestamp) {
            this.profile = profile;
            this.timestamp = timestamp;
        }
    }
}
