package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.util.Util;

public interface InsideBlockEffectApplier {
    InsideBlockEffectApplier NOOP = new InsideBlockEffectApplier() {
        @Override
        public void apply(final InsideBlockEffectType type) {
        }

        @Override
        public void runBefore(final InsideBlockEffectType type, final Consumer<Entity> effect) {
        }

        @Override
        public void runAfter(final InsideBlockEffectType type, final Consumer<Entity> effect) {
        }
    };

    void apply(InsideBlockEffectType type);

    void runBefore(InsideBlockEffectType type, Consumer<Entity> effect);

    void runAfter(InsideBlockEffectType type, Consumer<Entity> effect);

    class StepBasedCollector implements InsideBlockEffectApplier {
        private static final InsideBlockEffectType[] APPLY_ORDER = InsideBlockEffectType.values();
        private static final int NO_STEP = -1;
        // Leaf start - optimize checkInsideBlocks calls
        private final Consumer<Entity>[] effectsInStep = new Consumer[APPLY_ORDER.length];
        private final List<Consumer<Entity>>[] beforeEffectsInStep = new List[APPLY_ORDER.length];
        private final List<Consumer<Entity>>[] afterEffectsInStep = new List[APPLY_ORDER.length];
        private final it.unimi.dsi.fastutil.objects.ObjectArrayList<Consumer<Entity>> finalEffects = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        private int lastStep = -1;

        public StepBasedCollector() {
            for (int i = 0; i < APPLY_ORDER.length; i++) {
                beforeEffectsInStep[i] = new ArrayList<>(2);
                afterEffectsInStep[i] = new ArrayList<>(2);
            }
        }
        public void advanceStep(final int step, net.minecraft.core.BlockPos pos) { // Paper - track position inside effect was triggered on
            this.currentBlockPos = pos; // Paper - track position inside effect was triggered on
            if (this.lastStep != step) {
                this.lastStep = step;
                this.flushStep();
            }
        }

        public void applyAndClear(final Entity entity) {
            this.flushStep();

            it.unimi.dsi.fastutil.objects.ObjectArrayList<Consumer<Entity>> effects = this.finalEffects;
            Object[] raw = effects.elements();
            for (int i = 0, size = effects.size(); i < size; i++) {
                if (!entity.isAlive()) {
                    break;
                }

                ((Consumer<Entity>) raw[i]).accept(entity);
            }

            effects.clear();
            this.lastStep = -1;
        }

        private void flushStep() {
            final int len = APPLY_ORDER.length;
            final List<Consumer<Entity>>[] beforeArr = this.beforeEffectsInStep;
            final Consumer<Entity>[] effectArr = this.effectsInStep;
            final List<Consumer<Entity>>[] afterArr = this.afterEffectsInStep;
            final List<Consumer<Entity>> finalList = this.finalEffects;

            for (int i = 0; i < len; i++) {
                // Process before effects
                List<Consumer<Entity>> beforeEffects = beforeArr[i];
                if (!beforeEffects.isEmpty()) {
                    finalList.addAll(beforeEffects);
                    beforeEffects.clear();
                }

                // Process main effect
                Consumer<Entity> effect = effectArr[i];
                if (effect != null) {
                    finalList.add(effect);
                    effectArr[i] = null;
                }

                // Process after effects
                List<Consumer<Entity>> afterEffects = afterArr[i];
                if (!afterEffects.isEmpty()) {
                    finalList.addAll(afterEffects);
                    afterEffects.clear();
                }
            }
        }

        @Override
        public void apply(final InsideBlockEffectType type) {
            effectsInStep[type.ordinal()] = recorded(type);
        }

        @Override
        public void runBefore(final InsideBlockEffectType type, final Consumer<Entity> effect) {
            beforeEffectsInStep[type.ordinal()].add(effect);
        }

        @Override
        public void runAfter(final InsideBlockEffectType type, final Consumer<Entity> effect) {
            afterEffectsInStep[type.ordinal()].add(effect);
        }
        // Leaf end - optimize checkInsideBlocks calls

        // Paper start - track position inside effect was triggered on
        private net.minecraft.core.BlockPos currentBlockPos = null;

        private Consumer<Entity> recorded(final InsideBlockEffectType type) {
            return new RecordedEffect(this.currentBlockPos.immutable(), type.effect());
        }

        record RecordedEffect(
            net.minecraft.core.BlockPos blockPos,
            InsideBlockEffectType.Applier applier
        ) implements Consumer<Entity> {

            @Override
            public void accept(final Entity entity) {
                this.applier.affect(entity, blockPos);
            }
        }
        // Paper end - track position inside effect was triggered on
    }
}
