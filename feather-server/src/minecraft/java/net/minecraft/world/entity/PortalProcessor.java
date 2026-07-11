package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;

public class PortalProcessor {
    private final Portal portal;
    private BlockPos entryPosition;
    private int portalTime;
    private boolean insidePortalThisTick;
    private ParallelPendingTeleportState pendingTeleport = ParallelPendingTeleportState.INACTIVE; // Leaf - SparklyPaper - parallel world ticking - prevent clearing portal process

    public PortalProcessor(final Portal portal, final BlockPos portalEntryPosition) {
        this.portal = portal;
        this.entryPosition = portalEntryPosition;
        this.insidePortalThisTick = true;
    }

    public boolean processPortalTeleportation(final ServerLevel serverLevel, final Entity entity, final boolean allowedToTeleport) {
        if (this.isParallelTeleportScheduled()) return false; // Leaf - SparklyPaper - parallel world ticking - prevent clearing portal process
        if (!this.insidePortalThisTick) {
            this.decayTick();
            return false;
        } else {
            this.insidePortalThisTick = false;
            return allowedToTeleport && this.portalTime++ >= this.portal.getPortalTransitionTime(serverLevel, entity);
        }
    }

    public @Nullable TeleportTransition getPortalDestination(final ServerLevel serverLevel, final Entity entity) {
        return this.portal.getPortalDestination(serverLevel, entity, this.entryPosition);
    }

    public Portal.Transition getPortalLocalTransition() {
        return this.portal.getLocalTransition();
    }

    private void decayTick() {
        this.portalTime = Math.max(this.portalTime - 4, 0);
    }

    public boolean hasExpired() {
        return !this.isParallelTeleportScheduled() && this.portalTime <= 0; // Leaf - SparklyPaper - parallel world ticking - prevent clearing portal process
    }

    public BlockPos getEntryPosition() {
        return this.entryPosition;
    }

    public void updateEntryPosition(final BlockPos entryPosition) {
        this.entryPosition = entryPosition;
    }

    public int getPortalTime() {
        return this.portalTime;
    }

    public boolean isInsidePortalThisTick() {
        return this.insidePortalThisTick;
    }

    public void setAsInsidePortalThisTick(final boolean insidePortal) {
        this.insidePortalThisTick = insidePortal;
    }

    public boolean isSamePortal(final Portal portal) {
        return this.portal == portal;
    }

    // Leaf start - SparklyPaper - parallel world ticking - prevent clearing portal process
    public boolean isParallelTeleportPending() {
        return this.pendingTeleport == ParallelPendingTeleportState.PENDING;
    }

    public boolean isParallelTeleportScheduled() {
        return this.pendingTeleport != ParallelPendingTeleportState.INACTIVE;
    }

    public boolean isParallelCancelledByPlugin() {
        return this.pendingTeleport == ParallelPendingTeleportState.CANCELLED;
    }

    public void setParallelAsScheduled() {
        this.pendingTeleport = ParallelPendingTeleportState.PENDING;
    }

    public void confirmParallelAsHandled() {
        this.pendingTeleport = ParallelPendingTeleportState.INACTIVE;
    }

    public void setParallelAsCancelled() {
        this.pendingTeleport = ParallelPendingTeleportState.CANCELLED;
    }

    private enum ParallelPendingTeleportState {
        INACTIVE,
        PENDING,
        CANCELLED
    }
    // Leaf end - SparklyPaper - parallel world ticking - prevent clearing portal process
}
