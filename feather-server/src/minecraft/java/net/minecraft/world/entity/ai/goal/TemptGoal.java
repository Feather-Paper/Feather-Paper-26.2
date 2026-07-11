package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class TemptGoal extends Goal {
    private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();
    private static final double DEFAULT_STOP_DISTANCE = 2.5;
    private final TargetingConditions targetingConditions;
    protected final Mob mob;
    protected final double speedModifier;
    private double px;
    private double py;
    private double pz;
    private double pRotX;
    private double pRotY;
    protected @Nullable LivingEntity player; // CraftBukkit
    private int calmDown;
    private boolean isRunning;
    private final Predicate<ItemStack> items;
    private final boolean canScare;
    private final double stopDistance;
    protected int globalTemptationLookupIndex; // Paper - optimise temptation checks // Leaf - Paper PR: Optimise temptation lookups changes - private final -> protected

    public TemptGoal(final PathfinderMob mob, final double speedModifier, final Predicate<ItemStack> items, final boolean canScare) {
        this((Mob)mob, speedModifier, items, canScare, 2.5);
    }

    public TemptGoal(final PathfinderMob mob, final double speedModifier, final Predicate<ItemStack> items, final boolean canScare, final double stopDistance) {
        this((Mob)mob, speedModifier, items, canScare, stopDistance);
    }

    private TemptGoal(final Mob mob, final double speedModifier, final Predicate<ItemStack> items, final boolean canScare, final double stopDistance) {
        this.globalTemptationLookupIndex = io.papermc.paper.entity.temptation.GlobalTemptationLookup.indexFor(items); // Paper - optimise temptation checks
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.items = items;
        this.canScare = canScare;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.targetingConditions = globalTemptationLookupIndex >= 0 ? TEMPT_TARGETING.copy() : TEMPT_TARGETING.copy().selector((target, level) -> this.shouldFollow(target)); // Paper - optimise temptation checks - skip selector if we have a lookup index.
    }

    private final int leaf$internal = Math.max(1, reducedTickDelay(net.feathermc.feather.config.modules.opt.EntityGoal.chanceTempt)); private int leaf$counter; // Leaf - configurable goal update interval
    @Override
    public boolean canUse() {
        if (this.calmDown > 0) {
            this.calmDown--;
            return false;
        } else {
            if (!isRunning()) { leaf$counter++; if (leaf$counter < leaf$internal) { return false; } else { leaf$counter = this.mob.getRandom().nextInt(leaf$internal); } } // Leaf - configurable goal update interval
            // Paper start - optimise temptation lookups
            final TargetingConditions rangeTargetingConditions = this.targetingConditions.range(this.mob.getAttributeValue(Attributes.TEMPT_RANGE));

            if (this.globalTemptationLookupIndex != -1) {
                final net.minecraft.server.level.ServerLevel level = getServerLevel(this.mob);
                final io.papermc.paper.entity.temptation.GlobalTemptationLookup lookup = level.getTemptGoalLookup();
                final java.util.BitSet lookupBitSet = lookup.getBitSet(this.globalTemptationLookupIndex);
                final net.minecraft.server.level.ServerPlayer[] players = lookup.players(); // Leaf - Paper PR: Optimise temptation lookups changes
                // Check if the lookup needs to be computed this tick. Do so for all players if needed.
                if (!lookup.isCalculated(this.globalTemptationLookupIndex)) {
                    for (int i = 0; i < players.length; i++) { // Leaf - Paper PR: Optimise temptation lookups changes
                        lookupBitSet.set(i, shouldFollow(players[i])); // Leaf - Paper PR: Optimise temptation lookups changes
                    }
                    lookup.setCalculated(this.globalTemptationLookupIndex);
                }
                double d = -1.0;
                net.minecraft.server.level.ServerPlayer nearestPlayer = null;
                // Only iterate over players that passed #shouldFollow either in the prior computation or another goals canUse check.
                // Leaf start - Paper PR: Optimise temptation lookups changes
                final Mob mob = this.mob;
                final double mobX = mob.getX();
                final double mobY = mob.getY();
                final double mobZ = mob.getZ();
                for (int i = lookupBitSet.nextSetBit(0); i >= 0; i = lookupBitSet.nextSetBit(i + 1)) {
                    final net.minecraft.server.level.ServerPlayer player = players[i];
                    if (rangeTargetingConditions.test(level, mob, player)) {
                        final double d1 = player.distanceToSqr(mobX, mobY, mobZ);
                        // Leaf end - Paper PR: Optimise temptation lookups changes
                        if (d == -1.0 || d1 < d) {
                            d = d1;
                            nearestPlayer = player;
                        }
                    }
                }
                this.player = nearestPlayer;
            } else {
            // Default case for non-optimized / non vanilla tempt goal predicates.
            // Paper end - optimise temptation lookups
            this.player = getServerLevel(this.mob)
                .getNearestPlayer(this.targetingConditions.range(this.mob.getAttributeValue(Attributes.TEMPT_RANGE)), this.mob);
            } // Paper - optimise temptation lookups
            // CraftBukkit start
            if (this.player != null) {
                org.bukkit.event.entity.EntityTargetLivingEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(this.mob, this.player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TEMPT);
                if (event.isCancelled()) {
                    return false;
                }
                this.player = (event.getTarget() == null) ? null : ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getTarget()).getHandle();
            }
            // CraftBukkit end
            return this.player != null;
        }
    }

    private boolean shouldFollow(final LivingEntity player) {
        return (this.items.test(player.getMainHandItem()) || this.items.test(player.getOffhandItem())) && (!(this.mob instanceof net.minecraft.world.entity.npc.villager.Villager villager) || !villager.isSleeping()); // Purpur - Villagers follow emerald blocks
    }

    @Override
    public boolean canContinueToUse() {
        if (this.canScare()) {
            if (this.mob.distanceToSqr(this.player) < 36.0) {
                if (this.player.distanceToSqr(this.px, this.py, this.pz) > 0.010000000000000002) {
                    return false;
                }

                if (Math.abs(this.player.getXRot() - this.pRotX) > 5.0 || Math.abs(this.player.getYRot() - this.pRotY) > 5.0) {
                    return false;
                }
            } else {
                this.px = this.player.getX();
                this.py = this.player.getY();
                this.pz = this.player.getZ();
            }

            this.pRotX = this.player.getXRot();
            this.pRotY = this.player.getYRot();
        }

        return this.canUse();
    }

    protected boolean canScare() {
        return this.canScare;
    }

    @Override
    public void start() {
        this.px = this.player.getX();
        this.py = this.player.getY();
        this.pz = this.player.getZ();
        this.isRunning = true;
    }

    @Override
    public void stop() {
        this.player = null;
        this.stopNavigation();
        this.calmDown = reducedTickDelay(100);
        this.isRunning = false;
    }

    @Override
    public void tick() {
        this.mob.getLookControl().setLookAt(this.player, this.mob.getMaxHeadYRot() + 20, this.mob.getMaxHeadXRot());
        if (this.mob.distanceToSqr(this.player) < this.stopDistance * this.stopDistance) {
            this.stopNavigation();
        } else {
            this.navigateTowards(this.player);
        }
    }

    protected void stopNavigation() {
        this.mob.getNavigation().stop();
    }

    protected void navigateTowards(final LivingEntity player) { // Paper
        this.mob.getNavigation().moveTo(player, this.speedModifier);
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public static class ForNonPathfinders extends TemptGoal {
        public ForNonPathfinders(final Mob mob, final double speedModifier, final Predicate<ItemStack> items, final boolean canScare, final double stopDistance) {
            super(mob, speedModifier, items, canScare, stopDistance);
        }

        @Override
        protected void stopNavigation() {
            this.mob.getMoveControl().setWait();
        }

        @Override
        protected void navigateTowards(final LivingEntity player) { // Paper
            Vec3 target = player.getEyePosition().subtract(this.mob.position()).scale(this.mob.getRandom().nextDouble()).add(this.mob.position());
            this.mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, this.speedModifier);
        }
    }
}
