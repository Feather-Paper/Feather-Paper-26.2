package net.feathermc.feather.command;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;

import java.util.Collections;
import java.util.List;

public interface FeatherSubcommand {

    boolean execute(CommandSender sender, String subCommand, String[] args);

    default List<String> tabComplete(final CommandSender sender, final String subCommand, final String[] args) {
        return Collections.emptyList();
    }

    boolean testPermission(CommandSender sender);

    Permission getPermission();
}
