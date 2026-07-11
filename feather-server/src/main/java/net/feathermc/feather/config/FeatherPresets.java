package net.feathermc.feather.config;

import java.util.HashMap;
import java.util.Map;

public final class FeatherPresets {

    public enum Preset {
        BALANCED,
        PERFORMANCE,
        EXTREME
    }

    private static final Map<Preset, Map<String, Object>> PRESET_OVERRIDES = new HashMap<>();

    static {
        Map<String, Object> performance = new HashMap<>();
        // # performance preset overrides
        performance.put("performance.faster-random-generator.enabled", true);
        performance.put("performance.optimize-biome.enabled", true);
        performance.put("performance.optimize-block-entities.enabled", true);
        performance.put("performance.optimize-despawn.enabled", true);
        performance.put("performance.optimize-item-ticking.enabled", true);
        performance.put("performance.optimize-no-action-time.enabled", true);
        performance.put("performance.optimize-random-tick.enabled", true);
        performance.put("performance.optimize-waypoint.enabled", true);
        performance.put("performance.sleeping-block-entity.enabled", true);
        performance.put("performance.reduce-useless-packets.enabled", true);
        performance.put("performance.skip-ai-for-non-aware-mob.enabled", true);
        performance.put("performance.check-survival-before-growth.enabled", true);
        performance.put("performance.cache-entity-type-convert.enabled", true);
        performance.put("performance.profile-result-caching.enabled", true);
        performance.put("performance.profile-result-caching.ttl-seconds", 1800);
        performance.put("performance.disable-method-profiler.enabled", true);
        performance.put("performance.fast-collections.enabled", true);
        PRESET_OVERRIDES.put(Preset.PERFORMANCE, performance);

        Map<String, Object> extreme = new HashMap<>(performance);
        // # extreme preset overrides
        extreme.put("performance.faster-random-generator.enable-for-worldgen", true);
        extreme.put("performance.skip-inactive-entity-for-execute.enabled", true);
        extreme.put("performance.tile-entity-snapshot-creation.enabled", true);
        PRESET_OVERRIDES.put(Preset.EXTREME, extreme);
    }

    public static boolean getOverride(Preset preset, String path, boolean defaultValue) {
        Map<String, Object> overrides = PRESET_OVERRIDES.get(preset);
        if (overrides != null && overrides.containsKey(path)) {
            Object val = overrides.get(path);
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
        }
        return defaultValue;
    }

    public static int getOverride(Preset preset, String path, int defaultValue) {
        Map<String, Object> overrides = PRESET_OVERRIDES.get(preset);
        if (overrides != null && overrides.containsKey(path)) {
            Object val = overrides.get(path);
            if (val instanceof Integer) {
                return (Integer) val;
            }
        }
        return defaultValue;
    }

    public static double getOverride(Preset preset, String path, double defaultValue) {
        Map<String, Object> overrides = PRESET_OVERRIDES.get(preset);
        if (overrides != null && overrides.containsKey(path)) {
            Object val = overrides.get(path);
            if (val instanceof Double) {
                return (Double) val;
            }
        }
        return defaultValue;
    }

    public static long getOverride(Preset preset, String path, long defaultValue) {
        Map<String, Object> overrides = PRESET_OVERRIDES.get(preset);
        if (overrides != null && overrides.containsKey(path)) {
            Object val = overrides.get(path);
            if (val instanceof Long) {
                return (Long) val;
            }
        }
        return defaultValue;
    }
}
