package net.feathermc.feather.command;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public abstract class PermissionedFeatherSubcommand implements FeatherSubcommand {

    private final Permission permission;

    protected PermissionedFeatherSubcommand(Permission permission) {
        this.permission = permission;
    }

    protected PermissionedFeatherSubcommand(String permission, PermissionDefault permissionDefault) {
        this(new Permission(permission, permissionDefault));
    }

    @Override
    public boolean testPermission(CommandSender sender) {
        return sender.hasPermission(this.permission);
    }

    @Override
    public Permission getPermission() {
        return this.permission;
    }
}
