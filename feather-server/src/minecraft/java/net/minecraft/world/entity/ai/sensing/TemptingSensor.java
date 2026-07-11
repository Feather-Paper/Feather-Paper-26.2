package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class TemptingSensor extends Sensor<PathfinderMob> {
    private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();
    private final BiPredicate<PathfinderMob, ItemStack> temptations;
    private final int globalTemptationLookupIndex; // Paper - optimise temptation lookups

    public TemptingSensor(final Predicate<ItemStack> tt) {
        this.temptations = (m, i) -> tt.test(i);
        this.globalTemptationLookupIndex = io.papermc.paper.entity.temptation.GlobalTemptationLookup.indexFor(tt); // Paper - optimise temptation lookups
    }

    public static TemptingSensor forAnimal() {
        return new TemptingSensor((m, i) -> m instanceof Animal animal && animal.isFood(i));
    }

    private TemptingSensor(final BiPredicate<PathfinderMob, ItemStack> temptations) {
        this.temptations = temptations;
        this.globalTemptationLookupIndex = -1;
    }

    @Override
    protected void doTick(final ServerLevel level, final PathfinderMob body) {
        Brain<?> brain = body.getBrain();
        TargetingConditions targeting = TEMPT_TARGETING.copy().range((float)body.getAttributeValue(Attributes.TEMPT_RANGE));
        // Paper start - optimise temptation lookups - on update, ensure below diff filters correctly
        Player targetPlayer;
        if (this.globalTemptationLookupIndex != -1) {
            final io.papermc.paper.entity.temptation.GlobalTemptationLookup lookup = level.getTemptGoalLookup();
            final java.util.BitSet lookupBitSet = lookup.getBitSet(this.globalTemptationLookupIndex);
            final net.minecraft.server.level.ServerPlayer[] players = lookup.players(); // Leaf - Paper PR: Optimise temptation lookups changes
            // Check if the lookup needs to be computed this tick. Do so for all players if needed.
            if (!lookup.isCalculated(this.globalTemptationLookupIndex)) {
                for (int i = 0; i < players.length; i++) { // Leaf - Paper PR: Optimise temptation lookups changes
                    final net.minecraft.server.level.ServerPlayer serverPlayer = players[i]; // Leaf - Paper PR: Optimise temptation lookups changes
                    lookupBitSet.set(i, net.minecraft.world.entity.EntitySelector.NO_SPECTATORS.test(serverPlayer) && this.playerHoldingTemptation(body, serverPlayer)); // check on update
                }
                lookup.setCalculated(this.globalTemptationLookupIndex);
            }
            double d = -1.0;
            net.minecraft.server.level.ServerPlayer nearestPlayer = null;
            // Only iterate over players that passed #shouldFollow either in the prior computation or another goals canUse check.
            // Leaf start - Paper PR: Optimise temptation lookups changes
            final double entityX = body.getX();
            final double entityY = body.getY();
            final double entityZ = body.getZ();
            for (int i = lookupBitSet.nextSetBit(0); i >= 0; i = lookupBitSet.nextSetBit(i + 1)) {
                final net.minecraft.server.level.ServerPlayer player = players[i];
                if (targeting.test(level, body, player) && !body.hasPassenger(player)) { // check on update - consider non passengers
                    final double d1 = player.distanceToSqr(entityX, entityY, entityZ);
                    // Leaf end - Paper PR: Optimise temptation lookups changes
                    if (d == -1.0 || d1 < d) {
                        d = d1;
                        nearestPlayer = player;
                    }
                }
            }
            targetPlayer = nearestPlayer;
        } else {
            // Default case for non-optimized / non-vanilla tempt goal predicates. Sorting the entire list is completely useless, but none of the vanilla logic uses this path now so
            // less diff and easier for updates.
            // Paper end - optimise temptation lookups
        // Leaf start - Remove stream in TemptingSensor
        List<net.minecraft.server.level.ServerPlayer> allPlayers = level.players();
        List<Player> players = new java.util.ArrayList<>();
        for (Player p : allPlayers) {
            if (EntitySelector.NO_SPECTATORS.test(p)
                    && targeting.test(level, body, p)
                    && this.playerHoldingTemptation(body, p)
                    && !body.hasPassenger(p)) {
                players.add(p);
            }
        }
        // Leaf end - Remove stream in TemptingSensor
        if (!players.isEmpty()) {
            players.sort(Comparator.comparingDouble(body::distanceToSqr)); // Leaf - Remove stream in TemptingSensor
            // Paper start - optimise temptation lookups
        }
            targetPlayer = players.isEmpty() ? null : players.getFirst();
        }
        if (targetPlayer != null) {
            Player player = targetPlayer;
            // Paper end - optimise temptation lookups
            // CraftBukkit start
            org.bukkit.event.entity.EntityTargetLivingEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(
                body, player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TEMPT
            );
            if (event.isCancelled()) {
                return;
            }
            if (event.getTarget() instanceof org.bukkit.craftbukkit.entity.CraftHumanEntity target) {
                brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, target.getHandle());
            } else {
                brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
            }
            // CraftBukkit end
        } else {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        }
    }

    private boolean playerHoldingTemptation(final PathfinderMob mob, final Player player) {
        return this.isTemptation(mob, player.getMainHandItem()) || this.isTemptation(mob, player.getOffhandItem());
    }

    private boolean isTemptation(final PathfinderMob mob, final ItemStack itemStack) {
        return this.temptations.test(mob, itemStack);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.TEMPTING_PLAYER);
    }
}
