package net.minecraft.server;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.io.File;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.util.SignatureValidator;
import org.jspecify.annotations.Nullable;

public record Services(
    MinecraftSessionService sessionService,
    ServicesKeySet servicesKeySet,
    GameProfileRepository profileRepository,
    UserNameToIdResolver nameToIdCache,
    ProfileResolver profileResolver
    , @Nullable PaperServices paper // Paper
    , org.galemc.gale.configuration.@Nullable GaleConfigurations galeConfigurations // Gale - Gale configuration
) {
    public static final String USERID_CACHE_FILE = "usercache.json";

    // Paper start - add paper configuration files
    public record PaperServices(
        io.papermc.paper.configuration.PaperConfigurations configurations,
        io.papermc.paper.profile.PaperFilledProfileCache filledProfileCache
    ) {}

    public Services(MinecraftSessionService sessionService, ServicesKeySet servicesKeySet, GameProfileRepository profileRepository, UserNameToIdResolver nameToIdCache, ProfileResolver profileResolver) {
        // Gale start - Gale configuration
        this(sessionService, servicesKeySet, profileRepository, nameToIdCache, profileResolver, null, null);
    }
    public Services(MinecraftSessionService sessionService, ServicesKeySet servicesKeySet, GameProfileRepository profileRepository, UserNameToIdResolver nameToIdCache, ProfileResolver profileResolver, @javax.annotation.Nullable PaperServices paper) throws Exception {
        this(sessionService, servicesKeySet, profileRepository, nameToIdCache, profileResolver, paper, org.galemc.gale.configuration.GaleConfigurations.setup(paper.configurations().getGlobalConfigFile()));
    }
    // Gale end - Gale configuration

    @Override
    public PaperServices paper() {
        return java.util.Objects.requireNonNull(this.paper);
    }
    // Paper end - add paper configuration files
    // Gale start - Gale configuration
    @Override
    public org.galemc.gale.configuration.GaleConfigurations galeConfigurations() {
        return java.util.Objects.requireNonNull(this.galeConfigurations);
    }
    // Gale end - Gale configuration

    public static Services create(final YggdrasilAuthenticationService serviceAccess, final File nameCacheDir, final File userCacheFile, final joptsimple.OptionSet options) throws Exception { // Paper - add options to load paper config files; add userCacheFile parameter
        MinecraftSessionService sessionService = serviceAccess.createMinecraftSessionService();
        GameProfileRepository profileRepository = serviceAccess.createProfileRepository();
        UserNameToIdResolver profileCache = new CachedUserNameToIdResolver(profileRepository, userCacheFile); // Paper
        // Paper start - load paper config files from cli options
        final java.nio.file.Path legacyConfigPath = ((File) options.valueOf("paper-settings")).toPath();
        final java.nio.file.Path configDirPath = ((File) options.valueOf("paper-settings-directory")).toPath();
        io.papermc.paper.configuration.PaperConfigurations paperConfigurations = io.papermc.paper.configuration.PaperConfigurations.setup(legacyConfigPath, configDirPath, nameCacheDir.toPath(), (File) options.valueOf("spigot-settings"));
        PaperServices paperServices = new PaperServices(
            paperConfigurations,
            new io.papermc.paper.profile.PaperFilledProfileCache()
        );
        ProfileResolver profileResolver = new ProfileResolver.Cached(sessionService, profileCache, paperServices.filledProfileCache());
        // Gale start - Gale configuration
        org.galemc.gale.configuration.GaleConfigurations galeConfigurations = org.galemc.gale.configuration.GaleConfigurations.setup(configDirPath);
        return new Services(sessionService, serviceAccess.getServicesKeySet(), profileRepository, profileCache, profileResolver, paperServices, galeConfigurations);
        // Gale end - Gale configuration
        // Paper end - load paper config files from cli options
    }

    public @Nullable SignatureValidator profileKeySignatureValidator() {
        return SignatureValidator.from(this.servicesKeySet, ServicesKeyType.PROFILE_KEY);
    }

    public boolean canValidateProfileKeys() {
        return !this.servicesKeySet.keys(ServicesKeyType.PROFILE_KEY).isEmpty();
    }
}
