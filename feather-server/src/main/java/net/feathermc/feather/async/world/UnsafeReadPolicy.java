package net.feathermc.feather.async.world;

import net.feathermc.feather.config.FeatherConfig;

import java.util.Locale;

public enum UnsafeReadPolicy {
    STRICT,
    BUFFERED,
    DISABLED;

    public static UnsafeReadPolicy fromString(String readPolicy) {
        try {
            return valueOf(readPolicy.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            FeatherConfig.LOGGER.warn("Invalid unsafe read policy: {}, falling back to {}.", readPolicy, DISABLED.toString());
            return DISABLED;
        }
    }
}
