package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class ProjectileWeaponItem extends Item {
    public static final Predicate<ItemStack> ARROW_ONLY = itemStack -> itemStack.is(ItemTags.ARROWS);
    public static final Predicate<ItemStack> ARROW_OR_FIREWORK = ARROW_ONLY.or(itemStack -> itemStack.is(Items.FIREWORK_ROCKET));

    public ProjectileWeaponItem(final Item.Properties properties) {
        super(properties);
    }

    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.getAllSupportedProjectiles();
    }

    public abstract Predicate<ItemStack> getAllSupportedProjectiles();

    public static ItemStack getHeldProjectile(final LivingEntity entity, final Predicate<ItemStack> valid) {
        if (valid.test(entity.getItemInHand(InteractionHand.OFF_HAND))) {
            return entity.getItemInHand(InteractionHand.OFF_HAND);
        } else {
            return valid.test(entity.getItemInHand(InteractionHand.MAIN_HAND)) ? entity.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        }
    }

    public abstract int getDefaultProjectileRange();

    // Paper PR start - prevent item consumption for cancelled events
    protected record UnrealizedDrawResult(
        List<ItemStack> projectiles,
        @Nullable ItemStack originalInPlayerInventory
        // Null in case the unrealised draw result is a noop (case of Crossbow)
    ) {
        public void consumeProjectilesFromPlayerInventory(final int projectStackIndex) {
            if (projectStackIndex != 0 || originalInPlayerInventory == null) return;
            if (projectiles.isEmpty()) return; // Whatever happened here, nothing
            final ItemStack nonIntangibleStack = projectiles.get(projectStackIndex);
            originalInPlayerInventory.shrink(nonIntangibleStack.getCount());
        }
    }
    protected boolean shoot(
        // Paper PR end - prevent item consumption for cancelled events
        final ServerLevel level,
        final LivingEntity shooter,
        final InteractionHand hand,
        final ItemStack weapon,
        final List<ItemStack> projectiles,
        final float power,
        final float uncertainty,
        final boolean isCrit,
        final @Nullable LivingEntity targetOverride
        , final float drawStrength // Paper - Pass draw strength
    ) {
        // Paper PR start - prevent item consumption for cancelled events
        return shoot(level, shooter, hand, weapon, new UnrealizedDrawResult(projectiles, null), power, uncertainty, isCrit, targetOverride, drawStrength);
    }
    protected boolean shoot(
        final ServerLevel level,
        final LivingEntity shooter,
        final InteractionHand hand,
        ItemStack weapon, // Remove final
        final UnrealizedDrawResult unrealizedDrawResult,
        final float power,
        final float uncertainty,
        final boolean isCrit,
        final @Nullable LivingEntity targetOverride
        , final float drawStrength // Paper - Pass draw strength
    ) {
        List<ItemStack> projectiles = unrealizedDrawResult.projectiles();
        boolean atLeastOneShootBowEventUncancelled = false;
        // Paper PR end - prevent item consumption for cancelled events
        float maxAngle = EnchantmentHelper.processProjectileSpread(level, weapon, shooter, 0.0F);
        float angleStep = projectiles.size() == 1 ? 0.0F : 2.0F * maxAngle / (projectiles.size() - 1);
        float angleOffset = (projectiles.size() - 1) % 2 * angleStep / 2.0F;
        float direction = 1.0F;

        for (int i = 0; i < projectiles.size(); i++) {
            ItemStack projectile = projectiles.get(i);
            if (!projectile.isEmpty()) {
                float angle = angleOffset + direction * ((i + 1) / 2) * angleStep;
                direction = -direction;
                int index = i;
                // CraftBukkit start
                Projectile projectileEntity = this.createProjectile(level, shooter, weapon, projectile, isCrit);
                this.shootProjectile(shooter, projectileEntity, index, power, uncertainty, angle, targetOverride);

                // Paper PR start - prevent item consumption for cancelled events; call for each shot projectile
                boolean preConsumption = weapon.is(Items.CROSSBOW) || shooter.level().shouldConsumeArrow;
                org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(shooter, weapon, projectile, projectileEntity, hand, drawStrength, preConsumption);
                // Paper PR end - prevent item consumption for cancelled events; call for each shot projectile
                if (event.isCancelled()) {
                    event.getProjectile().remove();
                    continue; // Paper PR - prevent item consumption for cancelled events; call for each shot projectile
                }
                atLeastOneShootBowEventUncancelled = true; // Paper PR - prevent item consumption for cancelled events

                if (event.getProjectile() == projectileEntity.getBukkitEntity()) {
                    if (Projectile.spawnProjectile(
                        projectileEntity,
                        level,
                        projectile
                    ).isRemoved()) {
                        // Paper PR start - prevent item consumption for cancelled events
                        continue; // call for each shot projectile
                    }
                }
                if (this instanceof CrossbowItem crossbow) {
                    // moved up to ensure events uncancelled
                    float shotPitch = crossbow.getShotPitch(shooter.getRandom(), index);
                    shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT, shooter.getSoundSource(), 1.0F, shotPitch);
                }
                if (!event.shouldConsumeItem() && projectileEntity instanceof final AbstractArrow abstractArrow)
                    abstractArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                if (event.shouldConsumeItem()) {
                    // Update item reference to explicitly use crossbow on hand, since plugin can change item in inventory during this stage
                    weapon = shooter.getItemInHand(hand);
                    if (weapon.is(Items.CROSSBOW)) {
                        List<ItemStack> newProjectiles = new ArrayList<>(weapon.get(DataComponents.CHARGED_PROJECTILES).itemCopies());
                        newProjectiles.remove(i - (projectiles.size() - newProjectiles.size()));
                        weapon.set(DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(newProjectiles));
                    } else if (level.shouldConsumeArrow) {
                        unrealizedDrawResult.consumeProjectilesFromPlayerInventory(i);
                        // Paper PR end - prevent item consumption for cancelled events
                    }
                }
                // CraftBukkit end
                weapon.hurtAndBreak(this.getDurabilityUse(projectile), shooter, hand.asEquipmentSlot());
                if (weapon.isEmpty()) {
                    break;
                }
            }
        }
        return atLeastOneShootBowEventUncancelled; // Paper PR - prevent item consumption for cancelled events
    }

    protected int getDurabilityUse(final ItemStack projectile) {
        return 1;
    }

    protected abstract void shootProjectile(
        final LivingEntity shooter,
        final Projectile projectileEntity,
        final int index,
        final float power,
        final float uncertainty,
        final float angle,
        final @Nullable LivingEntity targetOverrride
    );

    protected Projectile createProjectile(
        final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit
    ) {
        ArrowItem arrowItem = projectile.getItem() instanceof ArrowItem arrow ? arrow : (ArrowItem)Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(level, projectile, shooter, weapon);
        if (isCrit) {
            arrow.setCritArrow(true);
        }

        arrow.setActualEnchantments(weapon.getEnchantments()); // Purpur - Add an option to fix MC-3304 projectile looting

        return arrow;
    }

    protected static List<ItemStack> draw(final ItemStack weapon, final ItemStack projectile, final LivingEntity shooter) {
        // Paper PR start - prevent item consumption for cancelled events
        return draw(weapon, projectile, shooter, ProjectileDrawingItemConsumption.IMMEDIATELY);
    }
    protected enum ProjectileDrawingItemConsumption {
        // Will immediately consume from the passed projectile stack, like vanilla would
        IMMEDIATELY,
        // Will create a copyWithCount from the projectileStack, allowing for later reduction.
        // The stacks yielded will adhere to vanilla's intangibility layout, with the first itemstack
        // being tangible, allowing for it to be picked up once shot.
        // Callers that do *not* consume later are responsible for marking the shot projectile as intangible.
        MAYBE_LATER,
        NEVER,
    }
    protected static List<ItemStack> draw(final ItemStack weapon, final ItemStack projectile, final LivingEntity shooter, final ProjectileDrawingItemConsumption consume) {
        // Paper PR end - prevent item consumption for cancelled events
        if (projectile.isEmpty()) {
            return List.of();
        }

        int numProjectiles = shooter.level() instanceof ServerLevel serverLevel ? EnchantmentHelper.processProjectileCount(serverLevel, weapon, shooter, 1) : 1;
        List<ItemStack> drawn = new ArrayList<>(numProjectiles);
        ItemStack projectileCopy = projectile.copy();

        shooter.level().shouldConsumeArrow = true; // Paper PR - prevent item consumption for cancelled events
        for (int i = 0; i < numProjectiles; i++) {
            ItemStack drawnStack = useAmmo(weapon, i == 0 ? projectile : projectileCopy, shooter, i > 0, consume); // Paper // Paper PR - prevent item consumption for cancelled events
            if (!drawnStack.isEmpty()) {
                drawn.add(drawnStack);
            }
        }

        return drawn;
    }

    protected static ItemStack useAmmo(final ItemStack weapon, final ItemStack projectile, final LivingEntity holder, final boolean forceInfinite) {
        // Paper PR start - prevent item consumption for cancelled events
        return useAmmo(weapon, projectile, holder, forceInfinite, ProjectileDrawingItemConsumption.IMMEDIATELY);
    }
    protected static ItemStack useAmmo(final ItemStack weapon, final ItemStack projectile, final LivingEntity holder, final boolean forceInfinite, final ProjectileDrawingItemConsumption consumption) {
        int ammoToUse = !forceInfinite && consumption != ProjectileDrawingItemConsumption.NEVER && !holder.hasInfiniteMaterials() && holder.level() instanceof ServerLevel serverLevel
            ? EnchantmentHelper.processAmmoUse(serverLevel, weapon, projectile, 1)
            : 0;
        // Paper PR end - prevent item consumption for cancelled events
        if (ammoToUse > projectile.getCount()) {
            return ItemStack.EMPTY;
        }

        if (ammoToUse == 0) {
            if (!forceInfinite) holder.level().shouldConsumeArrow = false; // Paper PR - prevent item consumption for cancelled events
            ItemStack copy = projectile.copyWithCount(1);
            copy.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            return copy;
        }

        ItemStack used =consumption == ProjectileDrawingItemConsumption.MAYBE_LATER ? projectile.copyWithCount(ammoToUse) : projectile.split(ammoToUse); // Paper PR - prevent item consumption for cancelled events
        if (projectile.isEmpty() && holder instanceof Player player) {
            player.getInventory().removeItem(projectile);
        }

        return used;
    }
}
