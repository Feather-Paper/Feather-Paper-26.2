package net.feathermc.feather.command;

import net.minecraft.server.MinecraftServer;
import org.bukkit.command.Command;
import org.bukkit.craftbukkit.util.permissions.CraftDefaultPermissions;

import java.util.HashMap;
import java.util.Map;

public final class FeatherCommands {

    public static final String COMMAND_BASE_PERM = CraftDefaultPermissions.FEATHER_ROOT + ".command";

    private FeatherCommands() {
    }

    private static final Map<String, Command> COMMANDS = new HashMap<>();

    static {
        COMMANDS.put(FeatherCommand.COMMAND_LABEL, new FeatherCommand());
    }

    public static void registerCommands(final MinecraftServer server) {
        COMMANDS.forEach((s, command) -> server.server.getCommandMap().register(s, "Feather", command));
    }
}
