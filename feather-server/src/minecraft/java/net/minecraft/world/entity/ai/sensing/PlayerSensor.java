package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

public class PlayerSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(
            MemoryModuleType.NEAREST_PLAYERS,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS
        );
    }

    @Override
    protected void doTick(final ServerLevel level, final LivingEntity body) {
        // Leaf start - Remove stream in PlayerSensor
        // Leaf start - fast bit radix sort
        it.unimi.dsi.fastutil.objects.ObjectArrayList<Player> players = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        double distance = this.getFollowDistance(body);
        double distSq = distance * distance;
        net.minecraft.world.phys.Vec3 pos = body.position();
        double x = pos.x();
        double y = pos.y();
        double z = pos.z();
        for (Player player : level.players()) {
            if (!EntitySelector.NO_SPECTATORS.test(player)) {
                continue;
            }
            if (player.distanceToSqr(x, y, z) >= distSq) {
                continue;
            }

            players.add(player);
        }
        level.fastBitRadixSort.sort(players.elements(), players.size(), pos);
        // Leaf end - fast bit radix sort
        // Leaf end - Remove stream in PlayerSensor
        Brain<?> brain = body.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, players);
        // Leaf start - Remove stream in PlayerSensor
        List<Player> visiblePlayers = new java.util.ArrayList<>(players.size());
        for (Player livingEntity : players) {
            if (isEntityTargetable(level, body, livingEntity)) {
                visiblePlayers.add(livingEntity);
            }
        }
        // Leaf end - Remove stream in PlayerSensor
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, visiblePlayers.isEmpty() ? null : visiblePlayers.get(0));
        // Leaf start - Remove stream in PlayerSensor
        List<Player> visibleAttackablePlayers = new java.util.ArrayList<>(visiblePlayers.size());
        for (Player livingEntity : visiblePlayers) {
            if (isEntityAttackable(level, body, livingEntity)) {
                visibleAttackablePlayers.add(livingEntity);
            }
        }
        // Leaf end - Remove stream in PlayerSensor
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS, visibleAttackablePlayers);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, visibleAttackablePlayers.isEmpty() ? null : visibleAttackablePlayers.get(0));
    }

    protected double getFollowDistance(final LivingEntity body) {
        return body.getAttributeValue(Attributes.FOLLOW_RANGE);
    }
}
