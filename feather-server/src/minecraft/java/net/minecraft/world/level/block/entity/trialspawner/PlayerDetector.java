package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public interface PlayerDetector {
    // Leaf start - Remove stream on PlayerDetector
    PlayerDetector NO_CREATIVE_PLAYERS = (level, selector, pos, requiredPlayerRange, requireLineOfSight) ->
        selector.getPlayerUUIDs(level, p -> p.blockPosition().closerThan(pos, requiredPlayerRange)
            && !p.isCreative()
            && !p.isSpectator()
            && p.affectsSpawning // Paper - Affects Spawning API
            && (!requireLineOfSight || inLineOfSight(level, Vec3.atCenterOf(pos), p.getEyePosition()))
        );
    PlayerDetector INCLUDING_CREATIVE_PLAYERS = (level, selector, pos, requiredPlayerRange, requireLineOfSight) ->
        selector.getPlayerUUIDs(level, p -> p.blockPosition().closerThan(pos, requiredPlayerRange)
            && !p.isSpectator()
            && (!requireLineOfSight || inLineOfSight(level, Vec3.atCenterOf(pos), p.getEyePosition()))
        );
    PlayerDetector SHEEP = (level, selector, pos, requiredPlayerRange, requireLineOfSight) ->
        selector.getEntityUUIDs(level, net.minecraft.world.entity.EntityTypes.SHEEP, new net.minecraft.world.phys.AABB(pos).inflate(requiredPlayerRange), entity -> entity.isAlive()
            && (!requireLineOfSight || inLineOfSight(level, Vec3.atCenterOf(pos), entity.getEyePosition()))
        );
    // Leaf end - Remove stream on PlayerDetector

    List<UUID> detect(
        final ServerLevel level,
        final PlayerDetector.EntitySelector selector,
        final BlockPos spawnerPos,
        final double requiredPlayerRange,
        final boolean requireLineOfSight
    );

    private static boolean inLineOfSight(final Level level, final Vec3 origin, final Vec3 dest) {
        BlockHitResult hitResult = level.clip(new ClipContext(dest, origin, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hitResult.getBlockPos().equals(BlockPos.containing(origin)) || hitResult.getType() == HitResult.Type.MISS;
    }

    interface EntitySelector {
        PlayerDetector.EntitySelector SELECT_FROM_LEVEL = new PlayerDetector.EntitySelector() {
            @Override
            public List<ServerPlayer> getPlayers(final ServerLevel level, final Predicate<? super Player> selector) {
                return level.getPlayers(selector);
            }

            @Override
            public <T extends Entity> List<T> getEntities(
                final ServerLevel level, final EntityTypeTest<Entity, T> type, final AABB aabb, final Predicate<? super T> selector
            ) {
                return level.getEntities(type, aabb, selector);
            }

            // Leaf start - Remove stream on PlayerDetector
            @Override
            public List<UUID> getPlayerUUIDs(final ServerLevel level, final Predicate<? super Player> selector) {
                List<UUID> ret = new java.util.ArrayList<>();
                for (Player player : level.players()) {
                    if (selector.test(player)) {
                        ret.add(player.getUUID());
                    }
                }
                return ret;
            }

            @Override
            public <T extends Entity> List<UUID> getEntityUUIDs(
                final ServerLevel level, final EntityTypeTest<Entity, T> type, final AABB bb, final Predicate<? super T> selector
            ) {
                List<UUID> ret = new java.util.ArrayList<>();
                for (Player player : level.players()) {
                    T entity = type.tryCast(player);
                    if (entity != null && selector.test(entity)) {
                        ret.add(entity.getUUID());
                    }
                }
                return ret;
            }
            // Leaf end - Remove stream on PlayerDetector
        };

        List<? extends Player> getPlayers(final ServerLevel level, final Predicate<? super Player> selector);

        <T extends Entity> List<T> getEntities(
            final ServerLevel level, final EntityTypeTest<Entity, T> type, final AABB bb, final Predicate<? super T> selector
        );

        // Leaf start - Remove stream on PlayerDetector
        List<UUID> getPlayerUUIDs(final ServerLevel level, final Predicate<? super Player> selector);

        <T extends Entity> List<UUID> getEntityUUIDs(
            final ServerLevel level, final EntityTypeTest<Entity, T> type, final AABB bb, final Predicate<? super T> selector
        );
        // Leaf end - Remove stream on PlayerDetector

        static PlayerDetector.EntitySelector onlySelectPlayer(final Player player) {
            return onlySelectPlayers(List.of(player));
        }

        static PlayerDetector.EntitySelector onlySelectPlayers(final List<Player> players) {
            return new PlayerDetector.EntitySelector() {
                @Override
                public List<Player> getPlayers(final ServerLevel level, final Predicate<? super Player> selector) {
                    return players.stream().filter(selector).toList();
                }

                @Override
                public <T extends Entity> List<T> getEntities(
                    final ServerLevel level, final EntityTypeTest<Entity, T> type, final AABB bb, final Predicate<? super T> selector
                ) {
                    return players.stream().map(type::tryCast).filter(Objects::nonNull).filter(selector).toList();
                }

                // Leaf start - Remove stream on PlayerDetector
                @Override
                public List<UUID> getPlayerUUIDs(final ServerLevel level, final Predicate<? super Player> selector) {
                    List<UUID> ret = new java.util.ArrayList<>();
                    for (Player player : players) {
                        if (selector.test(player)) {
                            ret.add(player.getUUID());
                        }
                    }
                    return ret;
                }

                @Override
                public <T extends Entity> List<UUID> getEntityUUIDs(
                    final ServerLevel level, final EntityTypeTest<Entity, T> type, final AABB bb, final Predicate<? super T> selector
                ) {
                    List<UUID> ret = new java.util.ArrayList<>();
                    for (Player player : players) {
                        T entity = type.tryCast(player);
                        if (entity != null && selector.test(entity)) {
                            ret.add(entity.getUUID());
                        }
                    }
                    return ret;
                }
                // Leaf end - Remove stream on PlayerDetector
            };
        }
    }
}
