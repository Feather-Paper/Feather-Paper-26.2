package net.minecraft.world.entity.ai.behavior;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InsideBrownianWalk {
    public static BehaviorControl<PathfinderMob> create(final float speedModifier) {
        return BehaviorBuilder.create(
            i -> i.group(i.absent(MemoryModuleType.WALK_TARGET))
                .apply(
                    i,
                    walkTarget -> (level, body, timestamp) -> {
                        if (level.canSeeSky(body.blockPosition())) {
                            return false;
                        }

                        BlockPos bodyPos = body.blockPosition();
                        // Leaf start - rewrite InsideBrownianWalk
                        BlockPos[] poses = net.feathermc.feather.util.BlockAreaUtils.getBlocksBetween(
                            bodyPos.offset(-1, -1, -1),
                            bodyPos.offset(1, 1, 1)
                        );

                        // Fisher-Yates shuffle is faster for this case
                        for (int index = poses.length - 1; index > 0; index--) {
                            int j = body.getRandom().nextInt(index + 1);
                            BlockPos temp = poses[index];
                            poses[index] = poses[j];
                            poses[index] = temp;
                        }

                        for (BlockPos pos : poses) {
                            if (!level.canSeeSky(pos) &&
                                level.loadedAndEntityCanStandOn(pos, body) &&
                                level.noCollision(body)) {
                                walkTarget.set(new WalkTarget(pos, speedModifier, 0));
                                break;
                            }
                        }
                        // Leaf end - rewrite InsideBrownianWalk
                        return true;
                    }
                )
        );
    }
}
