package net.minecraft.world.entity.monster.cubemob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.jspecify.annotations.Nullable;

public class Slime extends AbstractCubeMob implements Enemy {
    public Slime(final EntityType<? extends Slime> type, final Level level) {
        super(type, level);
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new AbstractCubeMob.CubeMobAttackGoal(this));
    }

    @Override
    protected void addTargetingGoals() {
        this.targetSelector
            .addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> Math.abs(target.getY() - this.getY()) <= 4.0));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.slimeRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.slimeRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.slimeControllable;
    }

    @Override
    public float getJumpPower() {
        float height = super.getJumpPower();
        return getRider() != null && this.isControllable() && actualJump ? height * 1.5F : height;
    }

    @Override
    public boolean onSpacebar() {
        if (onGround && getRider() != null && this.isControllable()) {
            actualJump = true;
            if (getRider().getForwardMot() == 0 || getRider().getStrafeMot() == 0) {
                jumpFromGround(); // jump() here if not moving
            }
        }
        return true; // do not jump() in wasd controller, let vanilla controller handle
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    protected String getMaxHealthEquation() {
        return level().purpurConfig.slimeMaxHealth;
    }

    @Override
    protected String getAttackDamageEquation() {
        return level().purpurConfig.slimeAttackDamage;
    }

    @Override
    protected java.util.Map<Integer, Double> getMaxHealthCache() {
        return level().purpurConfig.slimeMaxHealthCache;
    }

    @Override
    protected java.util.Map<Integer, Double> getAttackDamageCache() {
        return level().purpurConfig.slimeAttackDamageCache;
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.slimeTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.slimeAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return this.isTiny() ? SoundEvents.SLIME_HURT_SMALL : SoundEvents.SLIME_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.isTiny() ? SoundEvents.SLIME_DEATH_SMALL : SoundEvents.SLIME_DEATH;
    }

    @Override
    protected ParticleOptions getParticleType() {
        return ParticleTypes.ITEM_SLIME;
    }

    @Override
    protected SoundEvent getSquishSound() {
        return this.isTiny() ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH;
    }

    @Override
    protected SoundEvent getJumpSound() {
        return this.isTiny() ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP;
    }

    public static boolean checkSlimeSpawnRules(
        final EntityType<Slime> type, final LevelAccessor level, final EntitySpawnReason spawnReason, final BlockPos pos, final RandomSource random
    ) {
        // Purpur start - Config to disable hostile mob spawn on ice
        if (net.minecraft.world.entity.monster.Monster.canSpawnInBlueAndPackedIce(level, pos)) {
            return false;
        }
        // Purpur end - Config to disable hostile mob spawn on ice
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            if (EntitySpawnReason.isSpawner(spawnReason)) {
                return checkMobSpawnRules(type, level, spawnReason, pos, random);
            }

            // Paper start - Replace rules for Height in Swamp Biomes
            final double maxHeightSwamp = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.surfaceBiome.maximum;
            final double minHeightSwamp = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.surfaceBiome.minimum;
            // Paper end - Replace rules for Height in Swamp Biomes
            if (level.getBiome(pos).is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS) && pos.getY() > minHeightSwamp && pos.getY() < maxHeightSwamp) { // Paper - Replace rules for Height in Swamp Biomes
                float surfaceSlimeSpawnChance = level.environmentAttributes().getValue(EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE, pos);
                if (random.nextFloat() < surfaceSlimeSpawnChance && level.getMaxLocalRawBrightness(pos) <= random.nextInt(8)) {
                    return checkMobSpawnRules(type, level, spawnReason, pos, random);
                }
            }

            if (!(level instanceof WorldGenLevel worldGenLevel)) {
                return false;
            }

            ChunkPos chunkPos = ChunkPos.containing(pos);
            // Leaf start - Matter - Secure Seed
            boolean slimeChunk = level.getMinecraftWorld().paperConfig().entities.spawning.allChunksAreSlimeChunks || (net.feathermc.feather.config.modules.misc.SecureSeed.enabled
                ? level.getChunk(chunkPos.x(), chunkPos.z()).isSlimeChunk()
                : WorldgenRandom.seedSlimeChunk(chunkPos.x(), chunkPos.z(), worldGenLevel.getSeed(), level.getMinecraftWorld().spigotConfig.slimeSeed).nextInt(10) == 0); // Paper
            // Leaf end - Matter - Secure Seed
            // Paper start - Replace rules for Height in Slime Chunks
            final double maxHeightSlimeChunk = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.slimeChunk.maximum;
            if (random.nextInt(10) == 0 && slimeChunk && pos.getY() < maxHeightSlimeChunk) {
            // Paper end - Replace rules for Height in Slime Chunks
                return checkMobSpawnRules(type, level, spawnReason, pos, random);
            }
        }

        return false;
    }

    @Override
    protected boolean canBeABaby() {
        return false;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData
    ) {
        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    public void setSize(final int size, final boolean updateHealth) {
        super.setSize(size, updateHealth);
        int actualSize = this.entityData.get(ID_SIZE);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(actualSize);
        this.xpReward = actualSize;
    }

    @Override
    protected void setcubeMobHealth(final int actualSize) {
        // Purpur start - Configurable entity base attributes
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(leaf$getMaxHealth(this, actualSize)); // Leaf - Improve Purpur JS expression evaluation
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(leaf$getAttackDamage(this, actualSize)); // Leaf - Improve Purpur JS expression evaluation
        // Purpur end - Configurable entity base attributes
    }

    // Leaf start - Improve Purpur JS expression evaluation
    private static double leaf$getMaxHealth(final Slime slime, final int size) {
        final Level level = slime.level();
        if (level.purpurConfig.slimeMaxHealthEnabled) {
            return slime.getFromCache(slime::getMaxHealthEquation, slime::getMaxHealthCache, () -> (double) (size * size));
        }
        return size * size;
    }

    private static double leaf$getAttackDamage(final Slime slime, final int attackDamage) {
        final Level level = slime.level();
        if (level.purpurConfig.slimeAttackDamageEnabled) {
            return slime.getFromCache(slime::getAttackDamageEquation, slime::getAttackDamageCache, () -> (double) attackDamage);
        }
        return attackDamage;
    }
    // Leaf end - Improve Purpur JS expression evaluation
}
