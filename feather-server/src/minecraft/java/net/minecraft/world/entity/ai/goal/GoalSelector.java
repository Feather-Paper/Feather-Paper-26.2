package net.minecraft.world.entity.ai.goal;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class GoalSelector {
    private static final WrappedGoal NO_GOAL = new WrappedGoal(Integer.MAX_VALUE, new Goal() {
        @Override
        public boolean canUse() {
            return false;
        }
    }) {
        @Override
        public boolean isRunning() {
            return false;
        }
    };
    private final Map<Goal.Flag, WrappedGoal> lockedFlags = new EnumMap<>(Goal.Flag.class);
    private final net.feathermc.feather.util.map.BinaryGoalSet leaf$availableGoals = new net.feathermc.feather.util.map.BinaryGoalSet(); // Leaf - optimize goal selector
    private final Set<WrappedGoal> availableGoals = leaf$availableGoals; // Leaf - optimize goal selector
    private static final Goal.Flag[] GOAL_FLAG_VALUES = Goal.Flag.values(); // Paper - remove streams from GoalSelector
    private final ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<net.minecraft.world.entity.ai.goal.Goal.Flag> goalTypes = new ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<>(Goal.Flag.class); // Paper - remove streams from GoalSelector
    private int curRate; // Paper - EAR 2
    private final int[] lockedPriorities = new int[GOAL_FLAG_VALUES.length]; // Leaf - optimize goal selector

    public void addGoal(final int prio, final Goal goal) {
        this.availableGoals.add(new WrappedGoal(prio, goal));
    }

    public void removeAllGoals(final Predicate<Goal> predicate) {
        // Leaf start - optimize goal selector
        net.feathermc.feather.util.map.BinaryGoalSet availableGoals = this.leaf$availableGoals;
        WrappedGoal[] elements = availableGoals.elements();
        for (int i = 0, size = availableGoals.size(); i < size; i++) {
            final WrappedGoal availableGoal = elements[i];
            // Leaf end - optimize goal selector
            if (predicate.test(availableGoal.getGoal()) && availableGoal.isRunning()) {
                availableGoal.stop();
            }
        }

        this.availableGoals.removeIf(goal -> predicate.test(goal.getGoal()));
    }

    // Paper start - EAR 2
    public boolean inactiveTick() {
        this.curRate++;
        return this.curRate % 3 == 0; // TODO newGoalRate was already unused in 1.20.4, check if this is correct
    }

    public boolean hasTasks() {
        // Leaf start - optimize goal selector
        net.feathermc.feather.util.map.BinaryGoalSet availableGoals = this.leaf$availableGoals;
        WrappedGoal[] elements = availableGoals.elements();
        for (int i = 0, size = availableGoals.size(); i < size; i++) {
            final WrappedGoal task = elements[i];
            // Leaf end - optimize goal selector
            if (task.isRunning()) {
                return true;
            }
        }
        return false;
    }
    // Paper end - EAR 2

    public void removeGoal(final Goal toRemove) {
        this.removeAllGoals(goal -> goal == toRemove);
    }

    // Paper start - Perf: optimize goal types
    private static boolean goalContainsAnyFlags(final WrappedGoal goal, final ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<Goal.Flag> disabledFlags) {
        return goal.getFlags().hasCommonElements(disabledFlags);
    }

    private static boolean goalCanBeReplacedForAllFlags(final WrappedGoal goal, final Map<Goal.Flag, WrappedGoal> lockedFlags) {
        long flagIterator = goal.getFlags().getBackingSet();
        final int flagSize = goal.getFlags().size();
        for (int i = 0; i < flagSize; ++i) {
            final Goal.Flag flag = GOAL_FLAG_VALUES[Long.numberOfTrailingZeros(flagIterator)];
            flagIterator ^= ca.spottedleaf.common.util.IntegerUtil.getTrailingBit(flagIterator);
            // Paper end - Perf: optimize goal types
            if (!lockedFlags.getOrDefault(flag, NO_GOAL).canBeReplacedBy(goal)) {
                return false;
            }
        }

        return true;
    }

    public void tick() {
        // Leaf start - optimize goal selector
        final net.feathermc.feather.util.map.BinaryGoalSet availableGoals = this.leaf$availableGoals;
        final WrappedGoal[] elements = availableGoals.elements();
        final long disabled = this.goalTypes.getBackingSet();
        final int elemSize = availableGoals.size();
        final Map<Goal.Flag, WrappedGoal> lockedFlags = this.lockedFlags;
        final int[] lockedPriorities = this.lockedPriorities;
        long mask = 0L;
        // Leaf end - optimize goal selector

        ProfilerFiller profiler = Profiler.get();
        profiler.push("goalCleanup");

        // Leaf start - optimize goal selector
        for (int i = 0; i < elemSize; i++) {
            final WrappedGoal goal = elements[i];
            if (goal.isRunning() && ((disabled & goal.goal.getFlags().getBackingSet()) != 0 || !goal.canContinueToUse())) {
                goal.stop();
            }
        }

        for (int i = 0; i < GOAL_FLAG_VALUES.length; i++) {
            final Goal.Flag flag = GOAL_FLAG_VALUES[i];
            final WrappedGoal locked = lockedFlags.get(flag);
            if (locked == null) {
                lockedPriorities[i] = Integer.MAX_VALUE;
            } else if (!locked.isRunning()) {
                lockedFlags.remove(flag);
                lockedPriorities[i] = Integer.MAX_VALUE;
            } else {
                lockedPriorities[i] = locked.isInterruptable()
                    ? locked.getPriority()
                    : Integer.MIN_VALUE;
                mask |= (1L << i);
            }
        }
        // Leaf end - optimize goal selector

        profiler.pop();
        profiler.push("goalUpdate");

        // Leaf start - optimize goal selector
        for (int i = 0; i < elemSize; i++) {
            final WrappedGoal goal = elements[i];
            if (goal.isRunning()) {
                continue;
            }
            final long f = goal.goal.getFlags().getBackingSet();
            final int p = goal.getPriority();
            if ((disabled & f) != 0L
                || (f & mask) != 0L
                && ((f & 1L) != 0L && p >= lockedPriorities[0]
                || (f & 2L) != 0L && p >= lockedPriorities[1]
                || (f & 4L) != 0L && p >= lockedPriorities[2]
                || (f & 8L) != 0L && p >= lockedPriorities[3]
                || (f & 16L) != 0L && p >= lockedPriorities[4])) {
                continue;
            } else if (!goal.canUse()) {
                continue;
            }
            for (long iter = f; iter != 0L; iter &= iter - 1) {
                final int j = Long.numberOfTrailingZeros(iter);
                final Goal.Flag flag = GOAL_FLAG_VALUES[j];
                final WrappedGoal locked = lockedFlags.get(flag);
                if (locked != null) {
                    locked.stop();
                }

                lockedFlags.put(flag, goal);
                lockedPriorities[j] = goal.isInterruptable()
                    ? goal.getPriority()
                    : Integer.MIN_VALUE;
                mask |= (1L << j);
            }
            goal.start();
        }
        // Leaf end - optimize goal selector

        profiler.pop();
        this.tickRunningGoals(true);
    }

    public void tickRunningGoals(final boolean forceTickAllRunningGoals) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("goalTick");

        // Leaf start - optimize goal selector
        net.feathermc.feather.util.map.BinaryGoalSet availableGoals = this.leaf$availableGoals;
        WrappedGoal[] elements = availableGoals.elements();
        for (int i = 0, size = availableGoals.size(); i < size; i++) {
            final WrappedGoal goal = elements[i];
            // Leaf end - optimize goal selector
            if (goal.isRunning() && (forceTickAllRunningGoals || goal.requiresUpdateEveryTick())) {
                goal.tick();
            }
        }

        profiler.pop();
    }

    public Set<WrappedGoal> getAvailableGoals() {
        return this.availableGoals;
    }

    public void disableControlFlag(final Goal.Flag flag) {
        this.goalTypes.addUnchecked(flag); // Paper - remove streams from GoalSelector
    }

    public void enableControlFlag(final Goal.Flag flag) {
        this.goalTypes.removeUnchecked(flag); // Paper - remove streams from GoalSelector
    }

    public void setControlFlag(final Goal.Flag flag, final boolean enabled) {
        if (enabled) {
            this.enableControlFlag(flag);
        } else {
            this.disableControlFlag(flag);
        }
    }
}
