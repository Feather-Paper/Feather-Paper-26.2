package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class HopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper, net.caffeinemc.mods.lithium.common.block.entity.SleepingBlockEntity, net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementListener, net.caffeinemc.mods.lithium.api.inventory.LithiumInventory, net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener, net.caffeinemc.mods.lithium.common.hopper.UpdateReceiver, net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker { // Leaves - Lithium Sleeping Block Entity
    public static final int MOVE_ITEM_SPEED = 8;
    public static final int HOPPER_CONTAINER_SIZE = 5;
    private static final int[][] CACHED_SLOTS = new int[54][];
    private static final int NO_COOLDOWN_TIME = -1;
    private static final Component DEFAULT_NAME = Component.translatable("container.hopper");
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    public int cooldownTime = -1;
    private long tickedGameTime;
    private Direction facing;

    // CraftBukkit start - add fields and methods
    public List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    public HopperBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
        super(BlockEntityTypes.HOPPER, worldPosition, blockState);
        this.facing = blockState.getValue(HopperBlock.FACING);
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }

        this.cooldownTime = input.getIntOr("TransferCooldown", -1);
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }

        output.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public ItemStack removeItem(final int slot, final int count) {
        this.unpackLootTable(null);
        return ContainerHelper.removeItem(this.getItems(), slot, count);
    }

    @Override
    public void setItem(final int slot, final ItemStack itemStack) {
        this.unpackLootTable(null);
        this.getItems().set(slot, itemStack);
        itemStack.limitSize(this.getMaxStackSize(itemStack));
    }

    @Override
    public void setBlockState(final BlockState blockState) {
        if (net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled && this.level != null && !this.level.isClientSide() && blockState.getValue(HopperBlock.FACING) != this.getBlockState().getValue(HopperBlock.FACING)) this.invalidateCachedData(); // Leaves - Lithium Sleeping Block Entity
        super.setBlockState(blockState);
        this.facing = blockState.getValue(HopperBlock.FACING);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    public static void pushItemsTick(final Level level, final BlockPos pos, final BlockState state, final HopperBlockEntity entity) {
        entity.cooldownTime--;
        entity.tickedGameTime = level.getGameTime();
        if (!entity.isOnCooldown()) {
            entity.setCooldown(0);
            // Spigot start
            boolean result = tryMoveItems(level, pos, state, entity, () -> suckInItems(level, entity));
            if (net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled) entity.checkSleepingConditions(); // Leaves - Lithium Sleeping Block Entity
            if (!result && entity.level.spigotConfig.hopperCheck > 1) {
                entity.setCooldown(entity.level.spigotConfig.hopperCheck);
            }
            // Spigot end
        }
    }

    // Paper start - Perf: Optimize Hoppers
    private static final int HOPPER_EMPTY = 0;
    private static final int HOPPER_HAS_ITEMS = 1;
    private static final int HOPPER_IS_FULL = 2;

    private static int getFullState(final HopperBlockEntity hopper) {
        hopper.unpackLootTable(null);

        final List<ItemStack> hopperItems = hopper.items;

        boolean empty = true;
        boolean full = true;

        for (int i = 0, len = hopperItems.size(); i < len; ++i) {
            final ItemStack stack = hopperItems.get(i);
            if (stack.isEmpty()) {
                full = false;
                continue;
            }

            if (!full) {
                // can't be full
                return HOPPER_HAS_ITEMS;
            }

            empty = false;

            if (stack.getCount() != stack.getMaxStackSize()) {
                // can't be full or empty
                return HOPPER_HAS_ITEMS;
            }
        }

        return empty ? HOPPER_EMPTY : (full ? HOPPER_IS_FULL : HOPPER_HAS_ITEMS);
    }
    // Paper end - Perf: Optimize Hoppers

    private static boolean tryMoveItems(
        final Level level, final BlockPos pos, final BlockState state, final HopperBlockEntity entity, final BooleanSupplier action
    ) {
        if (level.isClientSide()) {
            return false;
        }

        if (!entity.isOnCooldown() && state.getValue(HopperBlock.ENABLED)) {
            boolean changed = false;
            final int fullState = getFullState(entity); // Paper - Perf: Optimize Hoppers
            if (fullState != HOPPER_EMPTY) { // Paper - Perf: Optimize Hoppers
                changed = ejectItems(level, pos, entity);
            }

            if (changed || fullState != HOPPER_IS_FULL) { // Paper - Perf: Optimize Hoppers
                changed |= action.getAsBoolean();
            }

            if (changed) {
                entity.setCooldown(level.spigotConfig.hopperTransfer); // Spigot
                setChanged(level, pos, state);
                // Leaves start - Lithium Sleeping Block Entity
                if (net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled
                    && !entity.isOnCooldown()
                    && !entity.isSleeping()
                    && !state.getValue(HopperBlock.ENABLED)) {
                    entity.lithium$startSleeping();
                }
                // Leaves end - Lithium Sleeping Block Entity
                return true;
            }
        }

        return false;
    }

    private boolean inventoryFull() {
        for (ItemStack itemStack : this.items) {
            if (itemStack.isEmpty() || itemStack.getCount() != itemStack.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    // Paper start - Perf: Optimize Hoppers
    public static boolean skipHopperEvents;
    private static boolean skipPullModeEventFire;
    private static boolean skipPushModeEventFire;

    private static boolean hopperPush(final Level level, final Container destination, final Direction direction, final HopperBlockEntity hopper) {
        skipPushModeEventFire = skipHopperEvents;
        boolean foundItem = false;
        for (int i = 0; i < hopper.getContainerSize(); ++i) {
            final ItemStack item = hopper.getItem(i);
            if (!item.isEmpty()) {
                foundItem = true;
                ItemStack origItemStack = item;
                ItemStack movedItem = origItemStack;

                final int originalItemCount = origItemStack.getCount();
                final int movedItemCount = Math.min(level.spigotConfig.hopperAmount, originalItemCount);
                origItemStack.setCount(movedItemCount);

                // We only need to fire the event once to give protection plugins a chance to cancel this event
                // Because nothing uses getItem, every event call should end up the same result.
                if (!skipPushModeEventFire) {
                    movedItem = callPushMoveEvent(destination, movedItem, hopper);
                    if (movedItem == null) { // cancelled
                        origItemStack.setCount(originalItemCount);
                        return false;
                    }
                }

                final ItemStack remainingItem = addItem(hopper, destination, movedItem, direction);
                final int remainingItemCount = remainingItem.getCount();
                if (remainingItemCount != movedItemCount) {
                    origItemStack = origItemStack.copy(true);
                    origItemStack.setCount(originalItemCount);
                    if (!origItemStack.isEmpty()) {
                        origItemStack.setCount(originalItemCount - movedItemCount + remainingItemCount);
                    }
                    hopper.setItem(i, origItemStack);
                    destination.setChanged();
                    return true;
                }
                origItemStack.setCount(originalItemCount);
            }
        }
        if (foundItem && level.paperConfig().hopper.cooldownWhenFull) { // Inventory was full - cooldown
            hopper.setCooldown(level.spigotConfig.hopperTransfer);
        }
        return false;
    }

    private static boolean hopperPull(final Level level, final Hopper hopper, final Container container, ItemStack origItemStack, final int i) {
        ItemStack movedItem = origItemStack;
        final int originalItemCount = origItemStack.getCount();
        final int movedItemCount = Math.min(level.spigotConfig.hopperAmount, originalItemCount);
        // Leaves start - Vanilla hopper
        if (net.feathermc.feather.config.modules.gameplay.VanillaHopper.enabled && movedItem.getCount() <= movedItemCount) {
            if (!skipPullModeEventFire) {
                movedItem = callPullMoveEvent(hopper, container, movedItem);
                if (movedItem == null) { // cancelled
                    origItemStack.setCount(originalItemCount);
                    // Drastically improve performance by returning true.
                    // No plugin could have relied on the behavior of false as the other call
                    // site for IMIE did not exhibit the same behavior
                    return true;
                }
            }
            movedItem = origItemStack.copy();
            final ItemStack remainingItem = addItem(container, hopper, container.removeItem(i, movedItemCount), null);
            if (remainingItem.isEmpty()) {
                container.setChanged();
                return true;
            }
            container.setItem(i, movedItem);
        } else {
            container.setChanged(); // original logic always marks source inv as changed even if no move happens.
            movedItem.setCount(movedItemCount);

            if (!skipPullModeEventFire) {
                movedItem = callPullMoveEvent(hopper, container, movedItem);
                if (movedItem == null) { // cancelled
                    origItemStack.setCount(originalItemCount);
                    // Drastically improve performance by returning true.
                    // No plugin could have relied on the behavior of false as the other call
                    // site for IMIE did not exhibit the same behavior
                    return true;
                }
            }

            final ItemStack remainingItem = addItem(container, hopper, movedItem, null);
            final int remainingItemCount = remainingItem.getCount();
            if (remainingItemCount != movedItemCount) {
                origItemStack = origItemStack.copy(true);
                origItemStack.setCount(originalItemCount);
                if (!origItemStack.isEmpty()) {
                    origItemStack.setCount(originalItemCount - movedItemCount + remainingItemCount);
                }

                ignoreBlockEntityUpdates = true;
                container.setItem(i, origItemStack);
                ignoreBlockEntityUpdates = false;
                container.setChanged();
                return true;
            }
            origItemStack.setCount(originalItemCount);
        }
        // Leaves end - Vanilla hopper

        if (level.paperConfig().hopper.cooldownWhenFull) {
            applyCooldown(hopper);
        }

        return false;
    }

    @Nullable
    private static ItemStack callPushMoveEvent(Container destination, ItemStack itemStack, HopperBlockEntity hopper) {
        final org.bukkit.inventory.Inventory destinationInventory = getInventory(destination);
        final io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent event = new io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent(
            hopper.getOwner(false).getInventory(),
            org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack),
            destinationInventory,
            true
        );
        final boolean result = event.callEvent();
        if (!event.calledGetItem && !event.calledSetItem) {
            skipPushModeEventFire = true;
        }
        if (!result) {
            applyCooldown(hopper);
            return null;
        }

        if (event.calledSetItem) {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
        } else {
            return itemStack;
        }
    }

    @Nullable
    private static ItemStack callPullMoveEvent(final Hopper hopper, final Container container, final ItemStack itemstack) {
        final org.bukkit.inventory.Inventory sourceInventory = getInventory(container);
        final org.bukkit.inventory.Inventory destination = getInventory(hopper);

        // Mirror is safe as no plugins ever use this item
        final io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent event = new io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent(sourceInventory, org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack), destination, false);
        final boolean result = event.callEvent();
        if (!event.calledGetItem && !event.calledSetItem) {
            skipPullModeEventFire = true;
        }
        if (!result) {
            applyCooldown(hopper);
            return null;
        }

        if (event.calledSetItem) {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
        } else {
            return itemstack;
        }
    }

    private static org.bukkit.inventory.Inventory getInventory(final Container container) {
        final org.bukkit.inventory.Inventory sourceInventory;
        if (container instanceof net.minecraft.world.CompoundContainer compoundContainer) {
            // Have to special-case large chests as they work oddly
            sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest(compoundContainer);
        } else if (container instanceof BlockEntity blockEntity) {
            sourceInventory = blockEntity.getOwner(false).getInventory();
        } else if (container.getOwner() != null) {
            sourceInventory = container.getOwner().getInventory();
        } else {
            sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventory(container);
        }
        return sourceInventory;
    }

    private static void applyCooldown(final Hopper hopper) {
        if (hopper instanceof HopperBlockEntity blockEntity && blockEntity.getLevel() != null) {
            blockEntity.setCooldown(blockEntity.getLevel().spigotConfig.hopperTransfer);
            blockEntity.skipNextSleepCheckAfterCooldown = true; // Leaves - Lithium Sleeping Block Entity
        }
    }

    private static boolean allMatch(Container container, Direction direction, java.util.function.BiPredicate<ItemStack, Integer> test) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            for (int slot : worldlyContainer.getSlotsForFace(direction)) {
                if (!test.test(container.getItem(slot), slot)) {
                    return false;
                }
            }
        } else {
            int size = container.getContainerSize();
            for (int slot = 0; slot < size; slot++) {
                if (!test.test(container.getItem(slot), slot)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean anyMatch(Container container, Direction direction, java.util.function.BiPredicate<ItemStack, Integer> test) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            for (int slot : worldlyContainer.getSlotsForFace(direction)) {
                if (test.test(container.getItem(slot), slot)) {
                    return true;
                }
            }
        } else {
            int size = container.getContainerSize();
            for (int slot = 0; slot < size; slot++) {
                if (test.test(container.getItem(slot), slot)) {
                    return true;
                }
            }
        }
        return true;
    }
    private static final java.util.function.BiPredicate<ItemStack, Integer> STACK_SIZE_TEST = (itemStack, _) -> itemStack.getCount() >= itemStack.getMaxStackSize();
    private static final java.util.function.BiPredicate<ItemStack, Integer> IS_EMPTY_TEST = (itemStack, _) -> itemStack.isEmpty();
    // Paper end - Perf: Optimize Hoppers

    private static boolean ejectItems(final Level level, final BlockPos blockPos, final HopperBlockEntity self) {
        Container container = net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled ? self.getInsertInventory(level) : getAttachedContainer(level, blockPos, self); // Leaves - Lithium Sleeping Block Entity
        if (container == null) {
            return false;
        }

        Direction direction = self.facing.getOpposite();
        // Leaves start - Lithium Sleeping Block Entity
        if (net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled) {
            Boolean res = lithiumInsert(level, blockPos, self, container);
            if (res != null) {
                return res;
            }
        }
        // Leaves end - Lithium Sleeping Block Entity
        if (isFullContainer(container, direction)) {
            return false;
        }

        return hopperPush(level, container, direction, self); // Paper - Perf: Optimize Hoppers
    }

    private static int[] getSlots(final Container container, final Direction direction) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            return worldlyContainer.getSlotsForFace(direction);
        } else {
            int containerSize = container.getContainerSize();
            if (containerSize < CACHED_SLOTS.length) {
                int[] cachedSlots = CACHED_SLOTS[containerSize];
                if (cachedSlots != null) {
                    return cachedSlots;
                }

                int[] slots = createFlatSlots(containerSize);
                CACHED_SLOTS[containerSize] = slots;
                return slots;
            } else {
                return createFlatSlots(containerSize);
            }
        }
    }

    private static int[] createFlatSlots(final int containerSize) {
        int[] slots = new int[containerSize];
        int i = 0;

        while (i < slots.length) {
            slots[i] = i++;
        }

        return slots;
    }

    private static boolean isFullContainer(final Container container, final Direction direction) {
        int[] slots = getSlots(container, direction);

        for (int slot : slots) {
            ItemStack itemStack = container.getItem(slot);
            if (itemStack.getCount() < itemStack.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    public static boolean suckInItems(final Level level, final Hopper hopper) {
        BlockPos blockPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
        BlockState blockState = level.getBlockState(blockPos);
        Container container = net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled ? getExtractInventory(level, hopper, blockPos, blockState) : getSourceContainer(level, hopper, blockPos, blockState); // Leaves - Lithium Sleeping Block Entity
        if (container != null) {
            Direction direction = Direction.DOWN;
            skipPullModeEventFire = skipHopperEvents; // Paper - Perf: Optimize Hoppers
            // Leaves start - Lithium Sleeping Block Entity
            if (net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled) {
                Boolean res = lithiumExtract(level, hopper, container);
                if (res != null) {
                    return res;
                }
            }
            // Leaves end - Lithium Sleeping Block Entity

            for (int slot : getSlots(container, direction)) {
                if (tryTakeInItemFromSlot(hopper, container, slot, direction, level)) { // Spigot
                    return true;
                }
            }

            return false;
        } else {
            boolean isBlocked = hopper.isGridAligned()
                && blockState.isCollisionShapeFullBlock(level, blockPos)
                && !blockState.is(BlockTags.DOES_NOT_BLOCK_HOPPERS);
            if (!isBlocked) {
                for (ItemEntity entity : net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled ? lithiumGetInputItemEntities(level, hopper) : getItemsAtAndAbove(level, hopper)) { // Leaves - Lithium Sleeping Block Entity
                    if (addItem(hopper, entity)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private static boolean tryTakeInItemFromSlot(final Hopper hopper, final Container container, final int slot, final Direction direction, final Level level) { // Spigot
        ItemStack itemStack = container.getItem(slot);
        if (!itemStack.isEmpty() && canTakeItemFromContainer(hopper, container, itemStack, slot, direction)) {
            return hopperPull(level, hopper, container, itemStack, slot); // Paper - Perf: Optimize Hoppers
        }

        return false;
    }

    public static boolean addItem(final Container container, final ItemEntity entity) {
        boolean changed = false;
        // CraftBukkit start
        if (org.bukkit.event.inventory.InventoryPickupItemEvent.getHandlerList().getRegisteredListeners().length > 0) { // Paper - optimize hoppers
        org.bukkit.event.inventory.InventoryPickupItemEvent event = new org.bukkit.event.inventory.InventoryPickupItemEvent(
            getInventory(container), (org.bukkit.entity.Item) entity.getBukkitEntity() // Paper - Perf: Optimize Hoppers; use getInventory() to avoid snapshot creation
        );
        if (!event.callEvent()) {
            return false;
        }
        // CraftBukkit end
        } // Paper - Perf: Optimize Hoppers
        ItemStack copy = entity.getItem().copy();
        ItemStack result = addItem(null, container, copy, null);
        if (result.isEmpty()) {
            changed = true;
            entity.setItem(ItemStack.EMPTY);
            entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
        } else {
            entity.setItem(result);
        }

        return changed;
    }

    public static ItemStack addItem(final @Nullable Container from, final Container container, ItemStack itemStack, final @Nullable Direction direction) {
        if (container instanceof WorldlyContainer worldly && direction != null) {
            int[] slots = worldly.getSlotsForFace(direction);

            for (int i = 0; i < slots.length && !itemStack.isEmpty(); i++) {
                itemStack = tryMoveInItem(from, container, itemStack, slots[i], direction);
            }
        } else {
            int size = container.getContainerSize();

            for (int i = 0; i < size && !itemStack.isEmpty(); i++) {
                itemStack = tryMoveInItem(from, container, itemStack, i, direction);
            }
        }

        return itemStack;
    }

    private static boolean canPlaceItemInContainer(final Container container, final ItemStack itemStack, final int slot, final @Nullable Direction direction) {
        return container.canPlaceItem(slot, itemStack)
            && !(container instanceof WorldlyContainer worldly && !worldly.canPlaceItemThroughFace(slot, itemStack, direction));
    }

    private static boolean canTakeItemFromContainer(
        final Container into, final Container from, final ItemStack itemStack, final int slot, final Direction direction
    ) {
        return from.canTakeItem(into, slot, itemStack)
            && !(from instanceof WorldlyContainer worldly && !worldly.canTakeItemThroughFace(slot, itemStack, direction));
    }

    private static ItemStack tryMoveInItem(
        final @Nullable Container from, final Container container, ItemStack itemStack, final int slot, final @Nullable Direction direction
    ) {
        ItemStack current = container.getItem(slot);
        if (canPlaceItemInContainer(container, itemStack, slot, direction)) {
            boolean success = false;
            boolean wasEmpty = container.isEmpty();
            if (current.isEmpty()) {
                // Spigot start - SPIGOT-6693, SimpleContainer#setItem
                ItemStack leftover = ItemStack.EMPTY; // Paper - Make hoppers respect inventory max stack size
                if (!itemStack.isEmpty() && itemStack.getCount() > container.getMaxStackSize()) {
                    leftover = itemStack; // Paper - Make hoppers respect inventory max stack size
                    itemStack = itemStack.split(container.getMaxStackSize());
                }
                // Spigot end
                ignoreBlockEntityUpdates = true; // Paper - Perf: Optimize Hoppers
                container.setItem(slot, itemStack);
                ignoreBlockEntityUpdates = false; // Paper - Perf: Optimize Hoppers
                itemStack = leftover; // Paper - Make hoppers respect inventory max stack size
                success = true;
            } else if (canMergeItems(current, itemStack)) {
                int space = Math.min(itemStack.getMaxStackSize(), container.getMaxStackSize()) - current.getCount(); // Paper - Make hoppers respect inventory max stack size
                int count = Math.min(itemStack.getCount(), space);
                itemStack.shrink(count);
                current.grow(count);
                success = count > 0;
            }

            if (success) {
                if (wasEmpty && container instanceof HopperBlockEntity hopperBlockEntity && !hopperBlockEntity.isOnCustomCooldown()) {
                    int skipTickCount = 0;
                    if (from instanceof HopperBlockEntity fromHopper && hopperBlockEntity.tickedGameTime >= fromHopper.tickedGameTime) {
                        skipTickCount = 1;
                    }

                    hopperBlockEntity.setCooldown(hopperBlockEntity.level.spigotConfig.hopperTransfer - skipTickCount); // Spigot
                }

                container.setChanged();
            }
        }

        return itemStack;
    }

    // CraftBukkit start
    private static @Nullable Container runHopperInventorySearchEvent(
        @Nullable Container container,
        org.bukkit.craftbukkit.block.CraftBlock hopper,
        org.bukkit.craftbukkit.block.CraftBlock searchLocation,
        org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType containerType
    ) {
        org.bukkit.event.inventory.HopperInventorySearchEvent event = new org.bukkit.event.inventory.HopperInventorySearchEvent(
            (container != null) ? new org.bukkit.craftbukkit.inventory.CraftInventory(container) : null,
            containerType,
            hopper,
            searchLocation
        );
        event.callEvent();
        return (event.getInventory() != null) ? ((org.bukkit.craftbukkit.inventory.CraftInventory) event.getInventory()).getInventory() : null;
    }
    // CraftBukkit end

    private static @Nullable Container getAttachedContainer(final Level level, final BlockPos blockPos, final HopperBlockEntity self) {
        // Paper start
        BlockPos searchPosition = blockPos.relative(self.facing);
        Container inventory = getContainerAt(level, searchPosition);
        if (org.bukkit.event.inventory.HopperInventorySearchEvent.getHandlerList().getRegisteredListeners().length == 0) return inventory;

        org.bukkit.craftbukkit.block.CraftBlock hopper = org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos);
        org.bukkit.craftbukkit.block.CraftBlock searchBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, searchPosition);
        return HopperBlockEntity.runHopperInventorySearchEvent(
            inventory,
            hopper,
            searchBlock,
            org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType.DESTINATION
        );
        // Paper end
    }

    private static @Nullable Container getSourceContainer(final Level level, final Hopper hopper, final BlockPos pos, final BlockState state) {
        // Paper start
        final Container inventory = HopperBlockEntity.getContainerAt(level, pos, state, hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
        if (org.bukkit.event.inventory.HopperInventorySearchEvent.getHandlerList().getRegisteredListeners().length == 0) return inventory;

        final BlockPos hopperPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ());
        org.bukkit.craftbukkit.block.CraftBlock hopperBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, hopperPos);
        org.bukkit.craftbukkit.block.CraftBlock containerBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, hopperPos.above());
        return HopperBlockEntity.runHopperInventorySearchEvent(
            inventory,
            hopperBlock,
            containerBlock,
            org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType.SOURCE
        );
        // Paper end
    }

    public static List<ItemEntity> getItemsAtAndAbove(final Level level, final Hopper hopper) {
        AABB aabb = hopper.getSuckAabb().move(hopper.getLevelX() - 0.5, hopper.getLevelY() - 0.5, hopper.getLevelZ() - 0.5);
        return level.getEntitiesOfClass(ItemEntity.class, aabb, EntitySelector.ENTITY_STILL_ALIVE);
    }

    public static @Nullable Container getContainerAt(final Level level, final BlockPos pos) {
        return getContainerAt(level, pos, level.getBlockState(pos), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, true); // Paper - Optimize hoppers
    }

    private static @Nullable Container getContainerAt(
        final Level level, final BlockPos pos, final BlockState state, final double x, final double y, final double z
    ) {
        // Paper start - Perf: Optimize Hoppers
        return getContainerAt(level, pos, state, x, y, z, false);
    }
    private static @Nullable Container getContainerAt(
        final Level level, final BlockPos pos, final BlockState state, final double x, final double y, final double z,
        final boolean optimizeEntities
    ) {
        // Paper end - Perf: Optimize Hoppers
        Container result = getBlockContainer(level, pos, state);
        if (result == null && (!optimizeEntities || !level.paperConfig().hopper.ignoreOccludingBlocks || !state.getBukkitMaterial().isOccluding())) { // Paper - Perf: Optimize Hoppers
            result = getEntityContainer(level, x, y, z);
        }

        return result;
    }

    private static @Nullable Container getBlockContainer(final Level level, final BlockPos pos, final BlockState state) {
        if (!level.spigotConfig.hopperCanLoadChunks && !level.hasChunkAt(pos)) return null; // Spigot
        Block block = state.getBlock();
        if (block instanceof WorldlyContainerHolder worldlyContainerHolder) {
            return worldlyContainerHolder.getContainer(state, level, pos);
        } else if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container container) {
            if (container instanceof ChestBlockEntity && block instanceof ChestBlock chestBlock) {
                container = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            }

            return container;
        } else {
            return null;
        }
    }

    private static @Nullable Container getEntityContainer(final Level level, final double x, final double y, final double z) {
        List<Entity> entities = level.getEntitiesOfClass( // Paper - Perf: Optimize hoppers
            (Class) Container.class, new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5), EntitySelector.CONTAINER_ENTITY_SELECTOR // Paper - Perf: Optimize hoppers
        );
        return !entities.isEmpty() ? (Container)entities.get(level.getRandom().nextInt(entities.size())) : null;
    }

    private static boolean canMergeItems(final ItemStack a, final ItemStack b) {
        return a.getCount() < a.getMaxStackSize() && ItemStack.isSameItemSameComponents(a, b); // Paper - Perf: Optimize Hoppers; used to return true for full itemstacks?!
    }

    @Override
    public double getLevelX() {
        return this.worldPosition.getX() + 0.5;
    }

    @Override
    public double getLevelY() {
        return this.worldPosition.getY() + 0.5;
    }

    @Override
    public double getLevelZ() {
        return this.worldPosition.getZ() + 0.5;
    }

    @Override
    public boolean isGridAligned() {
        return true;
    }

    public void setCooldown(final int time) {
        // Leaves start - Lithium Sleeping Block Entity
        if (net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled) {
            if (cooldownTime == 7) {
                if (this.tickedGameTime == Long.MAX_VALUE) {
                    this.sleepOnlyCurrentTick();
                } else {
                    this.wakeUpNow();
                }
            } else if (cooldownTime > 0 && this.sleepingTicker != null) {
                this.wakeUpNow();
            }
        }
        // Leaves end - Lithium Sleeping Block Entity
        this.cooldownTime = time;
    }

    private boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    private boolean isOnCustomCooldown() {
        return this.cooldownTime > 8;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(final NonNullList<ItemStack> items) {
        this.items = items;
        if (net.feathermc.feather.config.modules.opt.SleepingBlockEntity.enabled) this.lithium$emitStackListReplaced(); // Leaves - Lithium Sleeping Block Entity
    }

    public static void entityInside(final Level level, final BlockPos pos, final BlockState blockState, final Entity entity, final HopperBlockEntity hopper) {
        if (entity instanceof ItemEntity itemEntity
            && !itemEntity.getItem().isEmpty()
            && entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ()).intersects(hopper.getSuckAabb())) {
            tryMoveItems(level, pos, blockState, hopper, () -> addItem(hopper, itemEntity));
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
        return new HopperMenu(containerId, inventory, this);
    }

    // Leaves start - Lithium Sleeping Block Entity
    @org.jetbrains.annotations.Nullable private net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = null;
    @org.jetbrains.annotations.Nullable private TickingBlockEntity sleepingTicker = null;
    private long myModCountAtLastInsert, myModCountAtLastExtract, myModCountAtLastItemCollect;
    private boolean skipNextSleepCheckAfterCooldown = false;

    private net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory insertionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.UNKNOWN;
    private net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory extractionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.UNKNOWN;

    //The currently used block inventories
    @Nullable
    private Container insertBlockInventory, extractBlockInventory;

    //The currently used inventories (optimized type, if not present, skip optimizations)
    private net.caffeinemc.mods.lithium.api.inventory.@Nullable LithiumInventory insertInventory, extractInventory;
    //Null iff corresp. LithiumInventory field is null
    private net.caffeinemc.mods.lithium.common.hopper.@Nullable LithiumStackList insertStackList, extractStackList;
    //Mod count used to avoid transfer attempts that are known to fail (no change since last attempt)
    private long insertStackListModCount, extractStackListModCount;

    @Nullable
    private List<net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionItemEntityMovementTracker> collectItemEntityTracker;
    private boolean collectItemEntityTrackerWasEmpty;
    @Nullable
    private AABB collectItemEntityBox;
    private long collectItemEntityAttemptTime;

    @Nullable
    private List<net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionInventoryEntityTracker> extractInventoryEntityTracker;
    @Nullable
    private AABB extractInventoryEntityBox;
    private long extractInventoryEntityFailedSearchTime;

    @Nullable
    private List<net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionInventoryEntityTracker> insertInventoryEntityTracker;
    @Nullable
    private AABB insertInventoryEntityBox;
    private long insertInventoryEntityFailedSearchTime;

    private boolean shouldCheckSleep;

    private void checkSleepingConditions() {
        if (this.cooldownTime > 0 || this.getLevel() == null || skipNextSleepCheckAfterCooldown) {
            return;
        }
        if (isSleeping()) {
            return;
        }
        if (!this.shouldCheckSleep) {
            this.shouldCheckSleep = true;
            return;
        }
        boolean listenToExtractTracker = false;
        boolean listenToInsertTracker = false;
        boolean listenToExtractEntities = false;
        boolean listenToItemEntities = false;
        boolean listenToInsertEntities = false;

        net.caffeinemc.mods.lithium.common.hopper.LithiumStackList thisStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(this);

        if (this.extractionMode != net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE && thisStackList.getFullSlots() != thisStackList.size()) {
            if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
                Container blockInventory = this.extractBlockInventory;
                if (this.extractStackList != null &&
                    blockInventory instanceof net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) {
                    if (this.extractStackList.maybeSendsComparatorUpdatesOnFailedExtract() && this.extractStackList.getOccupiedSlots() != 0) {
                        if (blockInventory instanceof net.caffeinemc.mods.lithium.common.block.entity.inventory_comparator_tracking.ComparatorTracker comparatorTracker && !comparatorTracker.lithium$hasAnyComparatorNearby()) {
                            listenToExtractTracker = true;
                        } else {
                            //Inventory is not empty (0 != number of occupied slots) and maybe sends comparator
                            // updates on failed extract attempts, so hopper must not sleep to be able to send
                            // the observable comparator updates.
                            return;
                        }
                    } else {
                        listenToExtractTracker = true;
                    }
                } else {
                    return;
                }
            } else if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
                BlockState hopperState = this.getBlockState();
                listenToExtractEntities = true;

                BlockPos blockPos = this.getBlockPos().above();
                BlockState blockState = this.getLevel().getBlockState(blockPos);
                if (!blockState.isCollisionShapeFullBlock(this.getLevel(), blockPos) || blockState.is(BlockTags.DOES_NOT_BLOCK_HOPPERS)) {
                    listenToItemEntities = true;
                }
            } else {
                return;
            }
        }
        if (this.insertionMode != net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE && 0 < thisStackList.getOccupiedSlots()) {
            if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
                Container blockInventory = this.insertBlockInventory;
                if (this.insertStackList != null && blockInventory instanceof net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) {
                    listenToInsertTracker = true;
                } else {
                    return;
                }
            } else if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
                BlockState hopperState = this.getBlockState();
                listenToInsertEntities = true;
            } else {
                return;
            }
        }

        if (listenToExtractTracker) {
            ((net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) this.extractBlockInventory).listenForContentChangesOnce(this.extractStackList, this);
        }
        if (listenToInsertTracker) {
            ((net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) this.insertBlockInventory).listenForContentChangesOnce(this.insertStackList, this);
        }
        if (listenToInsertEntities) {
            if (this.insertInventoryEntityTracker == null || this.insertInventoryEntityTracker.isEmpty()) {
                return;
            }
            net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.listenToEntityMovementOnce(this, insertInventoryEntityTracker);
        }
        if (listenToExtractEntities) {
            if (this.extractInventoryEntityTracker == null || this.extractInventoryEntityTracker.isEmpty()) {
                return;
            }
            net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.listenToEntityMovementOnce(this, extractInventoryEntityTracker);
        }
        if (listenToItemEntities) {
            if (this.collectItemEntityTracker == null || this.collectItemEntityTracker.isEmpty()) {
                return;
            }
            net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.listenToEntityMovementOnce(this, collectItemEntityTracker);
        }

        this.listenForContentChangesOnce(thisStackList, this);
        lithium$startSleeping();
    }

    @Override
    public void lithium$setSleepingTicker(final @Nullable TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }

    @Override
    public @Nullable TickingBlockEntity lithium$getSleepingTicker() {
        return sleepingTicker;
    }

    @Override
    public void lithium$setTickWrapper(final net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper) {
        this.tickWrapper = tickWrapper;
        this.lithium$setSleepingTicker(null);
    }

    @Override
    public @org.jetbrains.annotations.Nullable net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper() {
        return tickWrapper;
    }

    @Override
    public boolean lithium$startSleeping() {
        if (this.isSleeping()) {
            return false;
        }

        net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (tickWrapper != null) {
            this.lithium$setSleepingTicker(tickWrapper.ticker);
            tickWrapper.rebind(net.caffeinemc.mods.lithium.common.block.entity.SleepingBlockEntity.SLEEPING_BLOCK_ENTITY_TICKER);

            // Set the last tick time to max value, so other hoppers transferring into this hopper will set it to 7gt
            // cooldown. Then when waking up, we make sure to not tick this hopper in the same gametick.
            // This makes the observable hopper cooldown not be different from vanilla.
            this.tickedGameTime = Long.MAX_VALUE;
            return true;
        }
        return false;
    }

    @Override
    public void handleEntityMovement() {
        this.wakeUpNow();
    }

    @Override
    public NonNullList<ItemStack> getInventoryLithium() {
        return items;
    }

    @Override
    public void setInventoryLithium(final NonNullList<ItemStack> inventory) {
        this.items = inventory;
    }

    @Override
    public void lithium$handleInventoryContentModified(final Container inventory) {
        wakeUpNow();
    }

    @Override
    public void lithium$handleInventoryRemoved(final Container inventory) {
        wakeUpNow();
        if (inventory == this.insertBlockInventory) {
            this.invalidateBlockInsertionData();
        }
        if (inventory == this.extractBlockInventory) {
            this.invalidateBlockExtractionData();
        }
        if (inventory == this) {
            this.invalidateCachedData();
        }
    }

    @Override
    public boolean lithium$handleComparatorAdded(final Container inventory) {
        if (inventory == this.extractBlockInventory) {
            wakeUpNow();
            return true;
        }
        return false;
    }

    @Override
    public void lithium$invalidateCacheOnNeighborUpdate(final boolean fromAbove) {
        //Clear the block inventory cache (composter inventories and no inventory present) on block update / observer update
        if (fromAbove) {
            if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE) {
                this.invalidateBlockExtractionData();
            }
        } else {
            if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE) {
                this.invalidateBlockInsertionData();
            }
        }
    }

    @Override
    public void lithium$invalidateCacheOnUndirectedNeighborUpdate() {
        if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE) {
            this.invalidateBlockExtractionData();
        }
        if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE) {
            this.invalidateBlockInsertionData();
        }
    }

    @Override
    public void lithium$invalidateCacheOnNeighborUpdate(final Direction fromDirection) {
        boolean fromAbove = fromDirection == Direction.UP;
        if (fromAbove || this.getBlockState().getValue(HopperBlock.FACING) == fromDirection) {
            this.lithium$invalidateCacheOnNeighborUpdate(fromAbove);
        }
    }

    private void invalidateCachedData() {
        this.shouldCheckSleep = false;
        this.invalidateInsertionData();
        this.invalidateExtractionData();
    }

    private void invalidateInsertionData() {
        if (this.level instanceof net.minecraft.server.level.ServerLevel) {
            if (this.insertInventoryEntityTracker != null) {
                net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.unregister(this.insertInventoryEntityTracker);
                this.insertInventoryEntityTracker = null;
                this.insertInventoryEntityBox = null;
                this.insertInventoryEntityFailedSearchTime = 0L;
            }
        }

        this.invalidateBlockInsertionData();
    }

    private void invalidateBlockInsertionData() {
        if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            assert this.insertBlockInventory != null;
            ((net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) this.insertBlockInventory).stopListenForMajorInventoryChanges(this);
        }

        this.insertionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.UNKNOWN;
        this.insertBlockInventory = null;
        this.insertInventory = null;
        this.insertStackList = null;
        this.insertStackListModCount = 0;

        wakeUpNow();
    }

    private void invalidateExtractionData() {
        if (this.level instanceof net.minecraft.server.level.ServerLevel) {
            if (this.extractInventoryEntityTracker != null) {
                net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.unregister(this.extractInventoryEntityTracker);
                this.extractInventoryEntityTracker = null;
                this.extractInventoryEntityBox = null;
                this.extractInventoryEntityFailedSearchTime = 0L;
            }
            if (this.collectItemEntityTracker != null) {
                net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.unregister(this.collectItemEntityTracker);
                this.collectItemEntityTracker = null;
                this.collectItemEntityBox = null;
                this.collectItemEntityTrackerWasEmpty = false;
            }
        }
        this.invalidateBlockExtractionData();
    }

    private void invalidateBlockExtractionData() {
        if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            assert this.extractBlockInventory != null;
            ((net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) this.extractBlockInventory).stopListenForMajorInventoryChanges(this);
        }

        this.extractionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.UNKNOWN;
        this.extractBlockInventory = null;
        this.extractInventory = null;
        this.extractStackList = null;
        this.extractStackListModCount = 0;

        this.wakeUpNow();
    }

    private static @Nullable Container getExtractInventory(final Level world, final Hopper hopper, final BlockPos extractBlockPos, final BlockState extractBlockState) {
        if (!(hopper instanceof HopperBlockEntity hopperBlockEntity)) {
            return getSourceContainer(world, hopper, extractBlockPos, extractBlockState); //Hopper Minecarts do not cache Inventories
        }

        Container blockInventory = hopperBlockEntity.lithium$getExtractBlockInventory(world, extractBlockPos, extractBlockState);
        if (blockInventory == null) {
            blockInventory = hopperBlockEntity.lithium$getExtractEntityInventory(world);
        }
        return org.bukkit.event.inventory.HopperInventorySearchEvent.getHandlerList().getRegisteredListeners().length == 0 ? blockInventory : runHopperInventorySearchEvent(
            blockInventory,
            org.bukkit.craftbukkit.block.CraftBlock.at(world, hopperBlockEntity.getBlockPos()),
            org.bukkit.craftbukkit.block.CraftBlock.at(world, extractBlockPos),
            org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType.SOURCE
        );
    }

    public @Nullable Container lithium$getExtractBlockInventory(final Level world, final BlockPos extractBlockPos, final BlockState extractBlockState) {
        Container blockInventory = this.extractBlockInventory;
        if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
            return null;
        } else if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE) {
            return blockInventory;
        } else if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            return blockInventory;
        } else if (this.extractionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_ENTITY) {
            BlockEntity blockEntity = (BlockEntity) java.util.Objects.requireNonNull(blockInventory);
            //Movable Block Entity compatibility - position comparison
            BlockPos pos = blockEntity.getBlockPos();
            if (!(blockEntity).isRemoved() && pos.equals(extractBlockPos)) {
                net.caffeinemc.mods.lithium.api.inventory.LithiumInventory optimizedInventory;
                if ((optimizedInventory = this.extractInventory) != null) {
                    net.caffeinemc.mods.lithium.common.hopper.LithiumStackList insertInventoryStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(optimizedInventory);
                    //This check is necessary as sometimes the stacklist is silently replaced (e.g. command making furnace read inventory from nbt)
                    if (insertInventoryStackList == this.extractStackList) {
                        return optimizedInventory;
                    } else {
                        this.invalidateBlockExtractionData();
                    }
                } else {
                    return blockInventory;
                }
            }
        }

        //No Cached Inventory: Get like vanilla and cache
        blockInventory = getBlockContainer(world, extractBlockPos, extractBlockState);
        blockInventory = net.caffeinemc.mods.lithium.common.hopper.HopperHelper.replaceDoubleInventory(blockInventory);
        this.cacheExtractBlockInventory(blockInventory);
        return blockInventory;
    }

    public @Nullable Container lithium$getInsertBlockInventory(final Level world) {
        Container blockInventory = this.insertBlockInventory;
        if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
            return null;
        } else if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE) {
            return blockInventory;
        } else if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            return blockInventory;
        } else if (this.insertionMode == net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_ENTITY) {
            BlockEntity blockEntity = (BlockEntity) java.util.Objects.requireNonNull(blockInventory);
            //Movable Block Entity compatibility - position comparison
            BlockPos pos = blockEntity.getBlockPos();
            Direction direction = this.facing;
            BlockPos transferPos = this.getBlockPos().relative(direction);
            if (!(blockEntity).isRemoved() &&
                pos.equals(transferPos)) {
                net.caffeinemc.mods.lithium.api.inventory.LithiumInventory optimizedInventory;
                if ((optimizedInventory = this.insertInventory) != null) {
                    net.caffeinemc.mods.lithium.common.hopper.LithiumStackList insertInventoryStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(optimizedInventory);
                    //This check is necessary as sometimes the stacklist is silently replaced (e.g. command making furnace read inventory from nbt)
                    if (insertInventoryStackList == this.insertStackList) {
                        return optimizedInventory;
                    } else {
                        this.invalidateBlockInsertionData();
                    }
                } else {
                    return blockInventory;
                }
            }
        }

        //No Cached Inventory: Get like vanilla and cache
        Direction direction = this.facing;
        BlockPos insertBlockPos = this.getBlockPos().relative(direction);
        BlockState blockState = world.getBlockState(insertBlockPos);
        blockInventory = getBlockContainer(world, insertBlockPos, blockState);
        blockInventory = net.caffeinemc.mods.lithium.common.hopper.HopperHelper.replaceDoubleInventory(blockInventory);
        this.cacheInsertBlockInventory(blockInventory);
        return blockInventory;
    }

    public @Nullable Container getInsertInventory(final Level world) {
        Container blockInventory = getInsertInventory0(world);
        return org.bukkit.event.inventory.HopperInventorySearchEvent.getHandlerList().getRegisteredListeners().length == 0 ? blockInventory : runHopperInventorySearchEvent(
            blockInventory,
            org.bukkit.craftbukkit.block.CraftBlock.at(world, this.getBlockPos()),
            org.bukkit.craftbukkit.block.CraftBlock.at(world, this.getBlockPos().relative(this.facing)),
            org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType.DESTINATION
        );
    }

    public @Nullable Container getInsertInventory0(final Level world) {
        Container blockInventory = this.lithium$getInsertBlockInventory(world);
        if (blockInventory != null) {
            return blockInventory;
        }

        if (this.insertInventoryEntityTracker == null) {
            this.initInsertInventoryTracker(world);
        }
        if (net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.isUnchangedSince(this.insertInventoryEntityFailedSearchTime, this.insertInventoryEntityTracker)) {
            this.insertInventoryEntityFailedSearchTime = this.tickedGameTime;
            return null;
        }
        this.insertInventoryEntityFailedSearchTime = Long.MIN_VALUE;
        this.shouldCheckSleep = false;

        List<Container> inventoryEntities = net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionInventoryEntityTracker.getEntities(world, this.insertInventoryEntityBox);
        if (inventoryEntities.isEmpty()) {
            this.insertInventoryEntityFailedSearchTime = this.tickedGameTime;
            //Remember failed entity search timestamp. This allows shortcutting if no entity movement happens.
            return null;
        }
        Container inventory = inventoryEntities.get(world.getRandom().nextInt(inventoryEntities.size()));
        if (inventory instanceof net.caffeinemc.mods.lithium.api.inventory.LithiumInventory optimizedInventory) {
            net.caffeinemc.mods.lithium.common.hopper.LithiumStackList insertInventoryStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(optimizedInventory);
            if (inventory != this.insertInventory || this.insertStackList != insertInventoryStackList) {
                this.cacheInsertLithiumInventory(optimizedInventory);
            }
        }

        return inventory;
    }

    private void initCollectItemEntityTracker() {
        assert this.level instanceof net.minecraft.server.level.ServerLevel;
        AABB inputBox = this.getSuckAabb().move(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        this.collectItemEntityBox = inputBox;
        this.collectItemEntityTracker =
            net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionItemEntityMovementTracker.registerAt(
                (net.minecraft.server.level.ServerLevel) this.level,
                inputBox
            );
        this.collectItemEntityAttemptTime = Long.MIN_VALUE;
    }

    private void initExtractInventoryTracker(final Level world) {
        assert world instanceof net.minecraft.server.level.ServerLevel;
        BlockPos pos = this.worldPosition.relative(Direction.UP);
        this.extractInventoryEntityBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        this.extractInventoryEntityTracker =
            net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionInventoryEntityTracker.registerAt(
                (net.minecraft.server.level.ServerLevel) this.level,
                this.extractInventoryEntityBox
            );
        this.extractInventoryEntityFailedSearchTime = Long.MIN_VALUE;
    }

    private void initInsertInventoryTracker(final Level world) {
        assert world instanceof net.minecraft.server.level.ServerLevel;
        Direction direction = this.facing;
        BlockPos pos = this.worldPosition.relative(direction);
        this.insertInventoryEntityBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        this.insertInventoryEntityTracker =
            net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionInventoryEntityTracker.registerAt(
                (net.minecraft.server.level.ServerLevel) this.level,
                this.insertInventoryEntityBox
            );
        this.insertInventoryEntityFailedSearchTime = Long.MIN_VALUE;
    }

    private @Nullable Container lithium$getExtractEntityInventory(final Level level) {
        if (this.extractInventoryEntityTracker == null) {
            this.initExtractInventoryTracker(level);
        }
        if (net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.isUnchangedSince(this.extractInventoryEntityFailedSearchTime, this.extractInventoryEntityTracker)) {
            this.extractInventoryEntityFailedSearchTime = this.tickedGameTime;
            return null;
        }
        this.extractInventoryEntityFailedSearchTime = Long.MIN_VALUE;
        this.shouldCheckSleep = false;

        List<Container> inventoryEntities = net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionInventoryEntityTracker.getEntities(level, this.extractInventoryEntityBox);
        if (inventoryEntities.isEmpty()) {
            this.extractInventoryEntityFailedSearchTime = this.tickedGameTime;
            //only set unchanged when no entity present. this allows shortcutting this case
            //shortcutting the entity present case requires checking its change counter
            return null;
        }
        Container inventory = inventoryEntities.get(level.getRandom().nextInt(inventoryEntities.size()));
        if (inventory instanceof net.caffeinemc.mods.lithium.api.inventory.LithiumInventory optimizedInventory) {
            net.caffeinemc.mods.lithium.common.hopper.LithiumStackList extractInventoryStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(optimizedInventory);
            if (inventory != this.extractInventory || this.extractStackList != extractInventoryStackList) {
                //not caching the inventory (NO_BLOCK_INVENTORY prevents it)
                //make change counting on the entity inventory possible, without caching it as block inventory
                this.cacheExtractLithiumInventory(optimizedInventory);
            }
        }
        return inventory;
    }

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param insertInventory Block inventory / Blockentity inventory to be remembered
     */
    private void cacheInsertBlockInventory(final @Nullable Container insertInventory) {
        assert !(insertInventory instanceof Entity);
        if (insertInventory instanceof net.caffeinemc.mods.lithium.api.inventory.LithiumInventory optimizedInventory) {
            this.cacheInsertLithiumInventory(optimizedInventory);
        } else {
            this.insertInventory = null;
            this.insertStackList = null;
            this.insertStackListModCount = 0;
        }

        if (insertInventory instanceof BlockEntity || insertInventory instanceof net.minecraft.world.CompoundContainer) {
            this.insertBlockInventory = insertInventory;
            if (insertInventory instanceof net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) {
                this.insertionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY;
                ((net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) insertInventory).listenForMajorInventoryChanges(this);
            } else {
                this.insertionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_ENTITY;
            }
        } else {
            if (insertInventory == null) {
                this.insertBlockInventory = null;
                this.insertionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY;
            } else {
                this.insertBlockInventory = insertInventory;
                this.insertionMode = insertInventory instanceof net.caffeinemc.mods.lithium.common.hopper.BlockStateOnlyInventory ? net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE : net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.UNKNOWN;
            }
        }
    }

    private void cacheInsertLithiumInventory(final net.caffeinemc.mods.lithium.api.inventory.LithiumInventory optimizedInventory) {
        net.caffeinemc.mods.lithium.common.hopper.LithiumStackList insertInventoryStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(optimizedInventory);
        this.insertInventory = optimizedInventory;
        this.insertStackList = insertInventoryStackList;
        this.insertStackListModCount = insertInventoryStackList.getModCount() - 1;
    }

    private void cacheExtractLithiumInventory(final net.caffeinemc.mods.lithium.api.inventory.LithiumInventory optimizedInventory) {
        net.caffeinemc.mods.lithium.common.hopper.LithiumStackList extractInventoryStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(optimizedInventory);
        this.extractInventory = optimizedInventory;
        this.extractStackList = extractInventoryStackList;
        this.extractStackListModCount = extractInventoryStackList.getModCount() - 1;
    }

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param extractInventory Block inventory / Blockentity inventory to be remembered
     */
    private void cacheExtractBlockInventory(final @Nullable Container extractInventory) {
        assert !(extractInventory instanceof Entity);
        if (extractInventory instanceof net.caffeinemc.mods.lithium.api.inventory.LithiumInventory optimizedInventory) {
            this.cacheExtractLithiumInventory(optimizedInventory);
        } else {
            this.extractInventory = null;
            this.extractStackList = null;
            this.extractStackListModCount = 0;
        }

        if (extractInventory instanceof BlockEntity || extractInventory instanceof net.minecraft.world.CompoundContainer) {
            this.extractBlockInventory = extractInventory;
            if (extractInventory instanceof net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) {
                this.extractionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY;
                ((net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker) extractInventory).listenForMajorInventoryChanges(this);
            } else {
                this.extractionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_ENTITY;
            }
        } else {
            if (extractInventory == null) {
                this.extractBlockInventory = null;
                this.extractionMode = net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY;
            } else {
                this.extractBlockInventory = extractInventory;
                this.extractionMode = extractInventory instanceof net.caffeinemc.mods.lithium.common.hopper.BlockStateOnlyInventory ? net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.BLOCK_STATE : net.caffeinemc.mods.lithium.common.hopper.HopperCachingState.BlockInventory.UNKNOWN;
            }
        }
    }

    private static List<ItemEntity> lithiumGetInputItemEntities(final Level world, final Hopper hopper) {
        if (!(hopper instanceof HopperBlockEntity hopperBlockEntity)) {
            return getItemsAtAndAbove(world, hopper); //optimizations not implemented for hopper minecarts
        }

        if (hopperBlockEntity.collectItemEntityTracker == null) {
            hopperBlockEntity.initCollectItemEntityTracker();
        }

        long modCount = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(hopperBlockEntity).getModCount();

        if ((hopperBlockEntity.collectItemEntityTrackerWasEmpty || hopperBlockEntity.myModCountAtLastItemCollect == modCount) &&
            net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker.isUnchangedSince(hopperBlockEntity.collectItemEntityAttemptTime, hopperBlockEntity.collectItemEntityTracker)) {
            hopperBlockEntity.collectItemEntityAttemptTime = hopperBlockEntity.tickedGameTime;
            return java.util.Collections.emptyList();
        }

        hopperBlockEntity.myModCountAtLastItemCollect = modCount;
        hopperBlockEntity.shouldCheckSleep = false;

        List<ItemEntity> itemEntities = net.caffeinemc.mods.lithium.common.tracking.entity.ChunkSectionItemEntityMovementTracker.getEntities(world, hopperBlockEntity.collectItemEntityBox);
        hopperBlockEntity.collectItemEntityAttemptTime = hopperBlockEntity.tickedGameTime;
        hopperBlockEntity.collectItemEntityTrackerWasEmpty = itemEntities.isEmpty();
        //set unchanged so that if this extract fails and there is no other change to hoppers or items, extracting
        // items can be skipped.
        return itemEntities;
    }

    private static @Nullable Boolean lithiumInsert(final Level world, final BlockPos pos, final HopperBlockEntity hopperBlockEntity, final @Nullable Container insertInventory) {
        if (insertInventory == null || hopperBlockEntity instanceof net.minecraft.world.WorldlyContainer) {
            //call the vanilla code to allow other mods inject features
            //e.g. carpet mod allows hoppers to insert items into wool blocks
            return null;
        }

        net.caffeinemc.mods.lithium.common.hopper.LithiumStackList hopperStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(hopperBlockEntity);
        if (hopperBlockEntity.insertInventory == insertInventory && hopperStackList.getModCount() == hopperBlockEntity.myModCountAtLastInsert) {
            if (hopperBlockEntity.insertStackList != null && hopperBlockEntity.insertStackList.getModCount() == hopperBlockEntity.insertStackListModCount) {
//                ComparatorUpdatePattern.NO_UPDATE.apply(hopperBlockEntity, hopperStackList); //commented because it's a noop, Hoppers do not send useless comparator updates
                return false;
            }
        }

        boolean insertInventoryWasEmptyHopperNotDisabled = insertInventory instanceof HopperBlockEntity hopperInv &&
            !hopperInv.isOnCustomCooldown() && hopperBlockEntity.insertStackList != null &&
            hopperBlockEntity.insertStackList.getOccupiedSlots() == 0;

        boolean insertInventoryHandlesModdedCooldown =
            insertInventory.canReceiveTransferCooldown() &&
                hopperBlockEntity.insertStackList != null ?
                hopperBlockEntity.insertStackList.getOccupiedSlots() == 0 :
                insertInventory.isEmpty();

        skipPushModeEventFire = skipHopperEvents;
        //noinspection ConstantConditions
        if (!(hopperBlockEntity.insertInventory == insertInventory && hopperBlockEntity.insertStackList.getFullSlots() == hopperBlockEntity.insertStackList.size())) {
            Direction fromDirection = hopperBlockEntity.facing.getOpposite();
            int size = hopperStackList.size();
            for (int i = 0; i < size; ++i) {
                ItemStack transferStack = hopperStackList.get(i);
                if (!transferStack.isEmpty()) {
                    if (!skipPushModeEventFire && canTakeItemFromContainer(insertInventory, hopperBlockEntity, transferStack, i, Direction.DOWN)) {
                        transferStack = callPushMoveEvent(insertInventory, transferStack, hopperBlockEntity);
                        if (transferStack == null) { // cancelled
                            break;
                        }
                    }
                    boolean transferSuccess = net.caffeinemc.mods.lithium.common.hopper.HopperHelper.tryMoveSingleItem(insertInventory, transferStack, fromDirection);
                    if (transferSuccess) {
                        if (insertInventoryWasEmptyHopperNotDisabled) {
                            HopperBlockEntity receivingHopper = (HopperBlockEntity) insertInventory;
                            int k = 8;
                            if (receivingHopper.tickedGameTime >= hopperBlockEntity.tickedGameTime) {
                                k = 7;
                            }
                            receivingHopper.setCooldown(k);
                        }
                        if (insertInventoryHandlesModdedCooldown) {
                            insertInventory.setTransferCooldown(hopperBlockEntity.tickedGameTime);
                        }
                        insertInventory.setChanged();
                        return true;
                    }
                }
            }
        }
        hopperBlockEntity.myModCountAtLastInsert = hopperStackList.getModCount();
        if (hopperBlockEntity.insertStackList != null) {
            hopperBlockEntity.insertStackListModCount = hopperBlockEntity.insertStackList.getModCount();
        }
        return false;
    }

    private static @Nullable Boolean lithiumExtract(final Level world, final Hopper to, final Container from) {
        if (!(to instanceof HopperBlockEntity hopperBlockEntity)) {
            return null; //optimizations not implemented for hopper minecarts
        }

        if (from != hopperBlockEntity.extractInventory || hopperBlockEntity.extractStackList == null) {
            return null; //from inventory is not an optimized inventory, vanilla fallback
        }

        net.caffeinemc.mods.lithium.common.hopper.LithiumStackList hopperStackList = net.caffeinemc.mods.lithium.common.hopper.InventoryHelper.getLithiumStackList(hopperBlockEntity);
        net.caffeinemc.mods.lithium.common.hopper.LithiumStackList fromStackList = hopperBlockEntity.extractStackList;

        if (hopperStackList.getModCount() == hopperBlockEntity.myModCountAtLastExtract) {
            if (fromStackList.getModCount() == hopperBlockEntity.extractStackListModCount) {
                if (!(from instanceof net.caffeinemc.mods.lithium.common.block.entity.inventory_comparator_tracking.ComparatorTracker comparatorTracker) || comparatorTracker.lithium$hasAnyComparatorNearby()) {
                    //noinspection CollectionAddedToSelf
                    fromStackList.runComparatorUpdatePatternOnFailedExtract(fromStackList, from);
                }
                return false;
            }
        }

        int[] availableSlots = from instanceof WorldlyContainer ? ((WorldlyContainer) from).getSlotsForFace(Direction.DOWN) : null;
        int fromSize = availableSlots != null ? availableSlots.length : from.getContainerSize();
        for (int i = 0; i < fromSize; i++) {
            int fromSlot = availableSlots != null ? availableSlots[i] : i;
            ItemStack itemStack = fromStackList.get(fromSlot);
            if (!itemStack.isEmpty() && canTakeItemFromContainer(to, from, itemStack, fromSlot, Direction.DOWN)) {
                if (!skipPullModeEventFire) {
                    itemStack = callPullMoveEvent(to, from, itemStack);
                    if (itemStack == null) { // cancelled
                        return true;
                    }
                }
                //calling removeStack is necessary due to its side effects (markDirty in LootableContainerBlockEntity)
                ItemStack takenItem = from.removeItem(fromSlot, 1);
                assert !takenItem.isEmpty();
                boolean transferSuccess = net.caffeinemc.mods.lithium.common.hopper.HopperHelper.tryMoveSingleItem(to, takenItem, null);
                if (transferSuccess) {
                    to.setChanged();
                    from.setChanged();
                    return true;
                }
                //put the item back similar to vanilla
                ItemStack restoredStack = fromStackList.get(fromSlot);
                if (restoredStack.isEmpty()) {
                    restoredStack = takenItem;
                } else {
                    restoredStack.grow(1);
                }
                //calling setStack is necessary due to its side effects (markDirty in LootableContainerBlockEntity)
                from.setItem(fromSlot, restoredStack);
            }
        }
        hopperBlockEntity.myModCountAtLastExtract = hopperStackList.getModCount();
        if (fromStackList != null) {
            hopperBlockEntity.extractStackListModCount = fromStackList.getModCount();
        }
        return false;
    }
    // Leaves end - Lithium Sleeping Block Entity
}
