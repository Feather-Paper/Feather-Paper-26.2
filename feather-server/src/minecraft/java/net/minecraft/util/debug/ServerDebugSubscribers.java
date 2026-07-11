package net.minecraft.util.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class ServerDebugSubscribers {
    private final MinecraftServer server;
    // Leaf start - Reduce debug subscribers overhead
    private final Map<DebugSubscription<?>, List<ServerPlayer>> enabledSubscriptions = new it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap<>();
    private int tickCount;
    private static final Set<DebugSubscription<?>> EMPTY_SUBSCRIPTIONS = Set.of();
    private static final List<ServerPlayer> EMPTY_PLAYERS = List.of();
    // Leaf end - Reduce debug subscribers overhead

    public ServerDebugSubscribers(final MinecraftServer server) {
        this.server = server;
    }

    private List<ServerPlayer> getSubscribersFor(final DebugSubscription<?> subscription) {
        if (net.feathermc.feather.util.LeafConstants.DISABLE_VANILLA_DEBUG_FEATURE) return EMPTY_PLAYERS; // Leaf - Reduce debug subscribers overhead
        return this.enabledSubscriptions.getOrDefault(subscription, List.of());
    }

    public void tick() {
        if (net.feathermc.feather.util.LeafConstants.DISABLE_VANILLA_DEBUG_FEATURE || tickCount++ % 20 != 0) return; // Leaf - Reduce debug subscribers overhead
        this.enabledSubscriptions.values().forEach(List::clear);

        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            for (DebugSubscription<?> subscription : player.debugSubscriptions()) {
                this.enabledSubscriptions.computeIfAbsent(subscription, s -> new ArrayList<>()).add(player);
            }
        }

        this.enabledSubscriptions.values().removeIf(List::isEmpty);
    }

    public void broadcastToAll(final DebugSubscription<?> subscription, final Packet<?> packet) {
        if (net.feathermc.feather.util.LeafConstants.DISABLE_VANILLA_DEBUG_FEATURE) return; // Leaf - Reduce debug subscribers overhead
        for (ServerPlayer player : this.getSubscribersFor(subscription)) {
            player.connection.send(packet);
        }
    }

    public Set<DebugSubscription<?>> enabledSubscriptions() {
        if (net.feathermc.feather.util.LeafConstants.DISABLE_VANILLA_DEBUG_FEATURE) return EMPTY_SUBSCRIPTIONS; // Leaf - Reduce debug subscribers overhead
        return Set.copyOf(this.enabledSubscriptions.keySet());
    }

    public boolean hasAnySubscriberFor(final DebugSubscription<?> subscription) {
        if (net.feathermc.feather.util.LeafConstants.DISABLE_VANILLA_DEBUG_FEATURE) return false; // Leaf - Reduce debug subscribers overhead
        return !this.getSubscribersFor(subscription).isEmpty();
    }

    public boolean hasRequiredPermissions(final ServerPlayer player) {
        if (net.feathermc.feather.util.LeafConstants.DISABLE_VANILLA_DEBUG_FEATURE) return false; // Leaf - Reduce debug subscribers overhead
        NameAndId nameAndId = player.nameAndId();
        return SharedConstants.IS_RUNNING_IN_IDE && this.server.isSingleplayerOwner(nameAndId) || this.server.getPlayerList().isOp(nameAndId);
    }
}
