package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.world.item.ItemStack;

public class EntityEquipment implements net.caffeinemc.mods.lithium.common.entity.EquipmentInfo, net.caffeinemc.mods.lithium.common.util.change_tracking.ChangeSubscriber.CountChangeSubscriber<ItemStack>, net.caffeinemc.mods.lithium.common.world.in_world_tracking.MaybeInLevelObject { // Leaf - Lithium - equipment tracking
    public static final Codec<EntityEquipment> CODEC = Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC).xmap(items -> {
        EnumMap<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
        map.putAll((Map<? extends EquipmentSlot, ? extends ItemStack>)items);
        return new EntityEquipment(map);
    }, equipment -> {
        // Leaf start - Replace entity equipment items to array
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        final ItemStack[] stacks = equipment.items;
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            if (!stack.isEmpty()) {
                items.put(EquipmentSlot.VALUES_ARRAY[i], stack);
            }
        }
        // Leaf end - Replace entity equipment items to array
        return items;
    });
    private final ItemStack[] items; // Leaf - Replace entity equipment items to array
    // Leaf start - Lithium - equipment tracking
    boolean shouldTickEnchantments = false;
    ItemStack recheckEnchantmentForStack = null;
    boolean hasUnsentEquipmentChanges = true;
    boolean inLevel = false;
    // Leaf end - Lithium - equipment tracking

    private EntityEquipment(final EnumMap<EquipmentSlot, ItemStack> items) {
        // Leaf start - Replace entity equipment items to array
        ItemStack[] self = new ItemStack[EquipmentSlot.VALUES_ARRAY.length];
        java.util.Arrays.fill(self, ItemStack.EMPTY);
        if (!items.isEmpty()) {
            for (Entry<EquipmentSlot, ItemStack> e : items.entrySet()) {
                self[e.getKey().ordinal()] = e.getValue();
            }
        }
        this.items = self;
        // Leaf end - Replace entity equipment items to array
    }

    public EntityEquipment() {
        this(new EnumMap<>(EquipmentSlot.class));
    }

    public ItemStack set(final EquipmentSlot slot, final ItemStack itemStack) {
        // Leaf start - Lithium - equipment tracking
        // Leaf start - Replace entity equipment items to array
        final ItemStack[] items = this.items;
        final int slotIndex = slot.ordinal();
        ItemStack oldStack = items[slotIndex];
        items[slotIndex] = itemStack == null ? ItemStack.EMPTY : itemStack;
        // Leaf end - Replace entity equipment items to array
        if (inLevel) this.onEquipmentReplaced(oldStack, itemStack);
        return oldStack;
        // Leaf end - Lithium - equipment tracking
    }

    public ItemStack get(final EquipmentSlot slot) {
        return this.items[slot.ordinal()]; // Leaf - Replace entity equipment items to array
    }

    public boolean isEmpty() {
        for (ItemStack item : this.items) { // Leaf - Replace entity equipment items to array
            if (!item.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public void tick(final Entity owner) {
        // Leaf start - Replace entity equipment items to array
        final ItemStack[] items = this.items;
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (!item.isEmpty()) {
                item.inventoryTick(owner.level(), owner, EquipmentSlot.VALUES_ARRAY[i]);
            }
        }
        // Leaf end - Replace entity equipment items to array
    }

    public void setAll(final EntityEquipment equipment) {
        if (this.inLevel) this.invalidateData(); // Leaf - Lithium - equipment tracking
        System.arraycopy(equipment.items, 0, this.items, 0, this.items.length); // Leaf - Replace entity equipment items to array
        if (this.inLevel) this.initializeData(); // Leaf - Lithium - equipment tracking
    }

    public void dropAll(final LivingEntity dropper) {
        for (ItemStack item : this.items) { // Leaf - Replace entity equipment items to array
            dropper.drop(item, true, false);
        }

        this.clear();
    }

    public void clear() {
        java.util.Arrays.fill(this.items, ItemStack.EMPTY); // Leaf - Replace entity equipment items to array
        if (inLevel) this.invalidateData(); // Leaf - Lithium - equipment tracking
    }

    // Paper start - EntityDeathEvent
    // Needed to not set ItemStack.EMPTY to not existent slot.
    public boolean has(final EquipmentSlot slot) {
        return this.items[slot.ordinal()] != ItemStack.EMPTY; // Leaf - Replace entity equipment items to array
    }
    // Paper end - EntityDeathEvent

    // Leaf start - Lithium - equipment tracking
    @Override
    public boolean lithium$shouldTickEnchantments() {
        if (!this.inLevel) {
            return true;
        }
        this.processScheduledEnchantmentCheck(null);
        return this.shouldTickEnchantments;
    }

    @Override
    public boolean lithium$hasUnsentEquipmentChanges() {
        if (!this.inLevel) {
            return true;
        }
        return this.hasUnsentEquipmentChanges;
    }

    @Override
    public void lithium$onEquipmentChangesSent() {
        if (!this.inLevel) {
            return;
        }
        this.hasUnsentEquipmentChanges = false;
    }

    private void onEquipmentReplaced(final ItemStack oldStack, final ItemStack newStack) {
        if (!this.shouldTickEnchantments) {
            if (this.recheckEnchantmentForStack == oldStack) {
                this.recheckEnchantmentForStack = null;
            }
            this.shouldTickEnchantments = stackHasTickableEnchantment(newStack);
        }

        this.hasUnsentEquipmentChanges = true;

        if (!oldStack.isEmpty()) {
            oldStack.lithium$unsubscribeWithData(this, 0);
        }
        if (!newStack.isEmpty()) {
            newStack.lithium$subscribe(this, 0);
        }
    }

    private static boolean stackHasTickableEnchantment(final ItemStack stack) {
        if (!stack.isEmpty()) {
            net.minecraft.world.item.enchantment.ItemEnchantments enchantments = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            if (enchantments != null && !enchantments.isEmpty()) {
                for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantmentEntry : enchantments.keySet()) {
                    if (!enchantmentEntry.value().getEffects(net.minecraft.world.item.enchantment.EnchantmentEffectComponents.TICK).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void lithium$notify(final @org.jetbrains.annotations.Nullable ItemStack publisher, final int zero) {
        this.hasUnsentEquipmentChanges = true;

        if (!this.shouldTickEnchantments) {
            this.processScheduledEnchantmentCheck(publisher);
            this.scheduleEnchantmentCheck(publisher);
        }
    }

    private void scheduleEnchantmentCheck(final @org.jetbrains.annotations.Nullable ItemStack toCheck) {
        this.recheckEnchantmentForStack = toCheck;
    }

    private void processScheduledEnchantmentCheck(final @org.jetbrains.annotations.Nullable ItemStack ignoredStack) {
        if (this.recheckEnchantmentForStack != null && this.recheckEnchantmentForStack != ignoredStack) {
            this.shouldTickEnchantments = stackHasTickableEnchantment(this.recheckEnchantmentForStack);
            this.recheckEnchantmentForStack = null;
        }
    }

    @Override
    public void lithium$notifyCount(final ItemStack publisher, final int zero, final int newCount) {
        if (newCount == 0) {
            publisher.lithium$unsubscribeWithData(this, zero);
        }

        this.onEquipmentReplaced(publisher, ItemStack.EMPTY);
    }

    @Override
    public void lithium$forceUnsubscribe(final ItemStack publisher, final int zero) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean lithium$isInLevel() {
        return this.inLevel;
    }

    @Override
    public void lithium$handleAddedToLevel(final net.minecraft.world.level.Level level) {
        this.inLevel = true;
        this.initializeData();

        net.caffeinemc.mods.lithium.common.world.in_world_tracking.MaybeInLevelObject.super.lithium$handleAddedToLevel(level);
    }

    @Override
    public void lithium$handleRemovedFromLevel(final net.minecraft.world.level.Level level) {
        this.inLevel = false;
        this.invalidateData();

        net.caffeinemc.mods.lithium.common.world.in_world_tracking.MaybeInLevelObject.super.lithium$handleRemovedFromLevel(level);
    }

    private void invalidateData() {
        this.shouldTickEnchantments = false;
        this.recheckEnchantmentForStack = null;
        this.hasUnsentEquipmentChanges = true;

        for (ItemStack oldStack : this.items) { // Leaf - Replace entity equipment items to array
            if (!oldStack.isEmpty()) {
                //noinspection unchecked
                oldStack.lithium$unsubscribeWithData(this, 0);
            }
        }
    }

    private void initializeData() {
        this.shouldTickEnchantments = false;
        this.recheckEnchantmentForStack = null;
        this.hasUnsentEquipmentChanges = true;

        for (ItemStack newStack : this.items) { // Leaf - Replace entity equipment items to array
            if (!newStack.isEmpty()) {
                if (!this.shouldTickEnchantments) {
                    this.shouldTickEnchantments = stackHasTickableEnchantment(newStack);
                }

                if (!newStack.isEmpty()) {
                    //noinspection unchecked
                    newStack.lithium$subscribe(this, 0);
                }
            }
        }
    }
    // Leaf end - Lithium - equipment tracking
}
