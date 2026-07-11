package io.papermc.paper.entity.temptation;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.frog.FrogAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The tempt state lookup holds onto cached temptation flags of players in the world.
 */
public class GlobalTemptationLookup {
    private static int registeredPredicateCounter = 0;

    public static final TemptationPredicate BEE_FOOD = register(i -> i.is(ItemTags.BEE_FOOD));
    public static final TemptationPredicate CHICKEN_FOOD = register(i -> i.is(ItemTags.CHICKEN_FOOD));
    public static final TemptationPredicate COW_FOOD = register(i -> i.is(ItemTags.COW_FOOD));
    public static final TemptationPredicate COW_FOOD_MUSHROOM = register(stack -> stack.is(Items.RED_MUSHROOM) || stack.is(Items.BROWN_MUSHROOM) || stack.is(ItemTags.COW_FOOD)); // Leaf - Paper PR: Optimise temptation lookups changes
    public static final TemptationPredicate PANDA_FOOD = register(i -> i.is(ItemTags.PANDA_FOOD));
    public static final TemptationPredicate PIG_CARROT_ON_A_STICK = register(i -> i.is(Items.CARROT_ON_A_STICK));
    public static final TemptationPredicate PIG = register(i -> i.is(ItemTags.PIG_FOOD));
    public static final TemptationPredicate RABBIT_FOOD = register(i -> i.is(ItemTags.RABBIT_FOOD));
    public static final TemptationPredicate SHEEP_FOOD = register(i -> i.is(ItemTags.SHEEP_FOOD));
    public static final TemptationPredicate TURTLE_FOOD = register(i -> i.is(ItemTags.TURTLE_FOOD));
    public static final TemptationPredicate HORSE_FOOD = register(i -> i.is(ItemTags.HORSE_TEMPT_ITEMS));
    public static final TemptationPredicate LLAMA_TEMPT_ITEMS = register(i -> i.is(ItemTags.LLAMA_TEMPT_ITEMS));
    public static final TemptationPredicate STRIDER_TEMPT_ITEMS = register(i -> i.is(ItemTags.STRIDER_TEMPT_ITEMS));
    public static final TemptationPredicate CAT_FOOD = register(i -> i.is(ItemTags.CAT_FOOD));
    public static final TemptationPredicate OCELOT_FOOD = register(i -> i.is(ItemTags.OCELOT_FOOD));
    public static final TemptationPredicate FROG_TEMPTATIONS = register(FrogAi.getTemptations());
    public static final TemptationPredicate CAMEL_TEMPTATIONS = register(i -> i.is(ItemTags.CAMEL_FOOD));
    public static final TemptationPredicate AXOLOTL_TEMPTATIONS = register(i -> i.is(net.minecraft.tags.ItemTags.AXOLOTL_FOOD));
    public static final TemptationPredicate GOAT_TEMPTATIONS = register(i -> i.is(net.minecraft.tags.ItemTags.GOAT_FOOD));
    public static final TemptationPredicate ARMADILLO_TEMPTATIONS = register(i -> i.is(ItemTags.ARMADILLO_FOOD));
    public static final TemptationPredicate SNIFFER_TEMPTATIONS = register(i -> i.is(ItemTags.SNIFFER_FOOD));
    public static final TemptationPredicate NAUTILUS_TEMPTATIONS = register(i -> i.is(ItemTags.NAUTILUS_FOOD));
    // Leaf start - Paper PR: Optimise temptation lookups changes
    public static final TemptationPredicate HAPPY_GHAST_FOOD = register(stack -> stack.is(ItemTags.HAPPY_GHAST_FOOD));
    public static final TemptationPredicate HAPPY_GHAST_TEMPT_ITEMS = register(stack -> stack.is(ItemTags.HAPPY_GHAST_TEMPT_ITEMS));
    public static final TemptationPredicate EMERALD_BLOCK_INGREDIENT = register(net.minecraft.world.entity.npc.villager.AbstractVillager.TEMPT_ITEMS);
    // Leaf end - Paper PR: Optimise temptation lookups changes

    public record TemptationPredicate(int index, Predicate<ItemStack> predicate) implements Predicate<ItemStack> {

        @Override
        public boolean test(final ItemStack itemStack) {
            return this.predicate.test(itemStack);
        }
    }

    public static int indexFor(final Predicate<ItemStack> predicate) {
        return predicate instanceof final TemptationPredicate temptationPredicate ? temptationPredicate.index() : -1;
    }

    private static TemptationPredicate register(final Predicate<ItemStack> predicate) {
        final TemptationPredicate val = new TemptationPredicate(registeredPredicateCounter, predicate);
        registeredPredicateCounter++;
        return val;
    }

    // Leaf start - Paper PR: Optimise temptation lookups changes
    private final BitSet[] precalculatedTemptItems;
    private final BitSet calculatedThisTick = new BitSet();
    private static final net.minecraft.server.level.ServerPlayer[] EMPTY_PLAYERS = {};
    private net.minecraft.server.level.ServerPlayer[] players = EMPTY_PLAYERS;

    {
        precalculatedTemptItems = new BitSet[registeredPredicateCounter];
        for (int i = 0; i < precalculatedTemptItems.length; i++) {
            this.precalculatedTemptItems[i] = new BitSet();
        }
    }

    public void tick(final net.minecraft.server.level.ServerLevel world) {
        for (int i = 0; i < registeredPredicateCounter; i++) {
            this.precalculatedTemptItems[i].clear();
        }
        this.calculatedThisTick.clear();

        int count = 0;
        final net.minecraft.server.level.ServerPlayer[] array = world.players().toArray(EMPTY_PLAYERS);
        for (int i = 0; i < array.length; i++) {
            final net.minecraft.server.level.ServerPlayer p = array[i];
            if (!p.isSpectator() && p.isAlive()) {
                array[count] = p;
                count++;
            }
        }
        this.players = it.unimi.dsi.fastutil.objects.ObjectArrays.setLength(array, count);
    }
    // Leaf end - Paper PR: Optimise temptation lookups changes

    public boolean isCalculated(final int index) {
        return this.calculatedThisTick.get(index);
    }

    public void setCalculated(final int index) {
        this.calculatedThisTick.set(index);
    }

    public BitSet getBitSet(final int index) {
        // Leaf start - Paper PR: Optimise temptation lookups changes
        return this.precalculatedTemptItems[index];
    }

    public net.minecraft.server.level.ServerPlayer[] players() {
        return this.players;
    }
    // Leaf end - Paper PR: Optimise temptation lookups changes
}
