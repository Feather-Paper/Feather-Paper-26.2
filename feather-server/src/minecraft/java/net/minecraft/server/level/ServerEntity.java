package net.minecraft.server.level;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TOLERANCE_LEVEL_ROTATION = 1;
    private static final double TOLERANCE_LEVEL_POSITION = 7.6293945E-6F;
    public static final int FORCED_POS_UPDATE_PERIOD = 60;
    private static final int FORCED_TELEPORT_PERIOD = 400;
    private final ServerLevel level; // Leaf - Multithreaded tracker - diff on change
    public final Entity entity; // Leaf - Multithreaded tracker - private -> public
    private final int updateInterval;
    private final boolean trackDelta;
    private final ServerEntity.Synchronizer synchronizer; // Leaf - Multithreaded tracker - diff on change
    private final VecDeltaCodec positionCodec = new VecDeltaCodec();
    private byte lastSentYRot;
    private byte lastSentXRot;
    private byte lastSentYHeadRot;
    private Vec3 lastSentMovement;
    private int tickCount;
    private int teleportDelay;
    private List<Entity> lastPassengers = com.google.common.collect.ImmutableList.of(); // Paper - optimize passenger checks
    private boolean wasRiding;
    private boolean wasOnGround;
    public @Nullable List<SynchedEntityData.DataValue<?>> trackedDataValues; // Leaf - Multithreaded tracker - private -> public
    private final Set<net.minecraft.server.network.ServerPlayerConnection> trackedPlayers; // Paper

    public ServerEntity(
        final ServerLevel level, final Entity entity, final int updateInterval, final boolean trackDelta, final ServerEntity.Synchronizer synchronizer, final Set<net.minecraft.server.network.ServerPlayerConnection> trackedPlayers // Paper
    ) {
        this.trackedPlayers = trackedPlayers; // Paper
        this.level = level;
        this.synchronizer = synchronizer; // Leaf - Multithreaded tracker - diff on change
        this.entity = entity;
        this.updateInterval = updateInterval;
        this.trackDelta = trackDelta;
        this.positionCodec.setBase(entity.trackingPosition());
        this.lastSentMovement = entity.getDeltaMovement();
        this.lastSentYRot = Mth.packDegrees(entity.getYRot());
        this.lastSentXRot = Mth.packDegrees(entity.getXRot());
        this.lastSentYHeadRot = Mth.packDegrees(entity.getYHeadRot());
        this.wasOnGround = entity.onGround();
        this.trackedDataValues = entity.getEntityData().getNonDefaultValues();
    }

    // Paper start - fix desync when a player is added to the tracker
    private boolean forceStateResync; // Leaf - Multithreaded tracker - diff on change
    public void onPlayerAdd() {
        this.forceStateResync = true;
    }
    // Paper end - fix desync when a player is added to the tracker

    public void sendChanges() { // Leaf - Multithreaded tracker - diff on change
        // Paper start - optimise collisions
        if (((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)this.entity).moonrise$isHardColliding()) {
            this.teleportDelay = 9999;
        }
        // Leaf - Multithreaded tracker - diff on change
        // Paper end - optimise collisions
        this.entity.updateDataBeforeSync(); // Leaf - Multithreaded tracker - diff on change
        List<Entity> passengers = this.entity.getPassengers(); // Leaf - Multithreaded tracker - diff on change
        if (!passengers.equals(this.lastPassengers)) {
            this.synchronizer
                .sendToTrackingPlayersFiltered( // Leaf - Multithreaded tracker - diff on change
                    new ClientboundSetPassengersPacket(this.entity), player -> passengers.contains(player) == this.lastPassengers.contains(player)
                );
            // Paper start - Allow riding players // Leaf - Multithreaded tracker - diff on change
            if (this.entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetPassengersPacket(this.entity));
            }
            // Paper end - Allow riding players
            this.lastPassengers = passengers; // Leaf - Multithreaded tracker - diff on change
        }

        if (!this.trackedPlayers.isEmpty() && this.entity instanceof ItemFrame frame /*&& this.tickCount % 10 == 0*/) { // CraftBukkit - moved tickCount below // Paper - Perf: Only tick item frames if players can see it // Leaf - Multithreaded tracker - diff on change
            ItemStack itemStack = frame.getItem();
            if (this.level.paperConfig().maps.itemFrameCursorUpdateInterval > 0 && this.tickCount % this.level.paperConfig().maps.itemFrameCursorUpdateInterval == 0 && itemStack.getItem() instanceof MapItem) { // CraftBukkit - Moved this.tickCounter % 10 logic here so item frames do not enter the other blocks // Paper - Make item frame map cursor update interval configurable
                MapId id = frame.cachedMapId; // Paper - Perf: Cache map ids on item frames
                MapItemSavedData data = MapItem.getSavedData(id, this.level); // Leaf - Multithreaded tracker - diff on change
                if (data != null) {
                    for (final net.minecraft.server.network.ServerPlayerConnection connection : this.trackedPlayers) { // Paper
                        final ServerPlayer player = connection.getPlayer(); // Paper
                        data.tickCarriedBy(player, itemStack, frame);
                        Packet<?> packet = data.getUpdatePacket(id, player); // Leaf - Multithreaded tracker - diff on change
                        if (packet != null) {
                            player.connection.send(packet); // Leaf - Multithreaded tracker - diff on change
                        }
                    }
                }
            }

            this.sendDirtyEntityData(); // Leaf - Multithreaded tracker - diff on change
        }

        if (this.entity.syncPosition) {
            this.tickCount = this.tickCount / this.updateInterval * this.updateInterval + this.updateInterval;
            this.entity.syncPosition = false;
        }

        if (this.forceStateResync || this.tickCount % this.updateInterval == 0 || this.entity.needsSync || this.entity.getEntityData().isDirty()) { // Paper - fix desync when a player is added to the tracker // Leaf - Multithreaded tracker - diff on change
            byte yRotn = Mth.packDegrees(this.entity.getYRot());
            byte xRotn = Mth.packDegrees(this.entity.getXRot());
            boolean shouldSendRotation = Math.abs(yRotn - this.lastSentYRot) >= 1 || Math.abs(xRotn - this.lastSentXRot) >= 1; // Leaf - Multithreaded tracker - diff on change
            if (this.entity.isPassenger()) {
                if (shouldSendRotation) { // Leaf - Multithreaded tracker - diff on change
                    this.synchronizer.sendToTrackingPlayers(new ClientboundMoveEntityPacket.Rot(this.entity.getId(), yRotn, xRotn, this.entity.onGround()));
                    this.lastSentYRot = yRotn;
                    this.lastSentXRot = xRotn;
                } // Leaf - Multithreaded tracker - diff on change

                this.positionCodec.setBase(this.entity.trackingPosition()); // Leaf - Multithreaded tracker - diff on change
                this.sendDirtyEntityData();
                this.wasRiding = true;
            } else if (this.entity instanceof AbstractMinecart minecart && minecart.getBehavior() instanceof NewMinecartBehavior newMinecartBehavior) {
                this.handleMinecartPosRot(newMinecartBehavior, yRotn, xRotn, shouldSendRotation); // Leaf - Multithreaded tracker - diff on change
            } else {
                this.teleportDelay++;
                Vec3 currentPosition = this.entity.trackingPosition(); // Leaf - Multithreaded tracker - diff on change
                // Paper start - reduce allocation of Vec3D here
                Vec3 base = this.positionCodec.base;
                double vec3_dx = currentPosition.x - base.x;
                double vec3_dy = currentPosition.y - base.y;
                double vec3_dz = currentPosition.z - base.z; // Leaf - Multithreaded tracker - diff on change
                boolean positionChanged = (vec3_dx * vec3_dx + vec3_dy * vec3_dy + vec3_dz * vec3_dz) >= 7.62939453125E-6D;
                // Paper end - reduce allocation of Vec3D here
                Packet<ClientGamePacketListener> packet = null; // Leaf - Multithreaded tracker - diff on change
                boolean pos = positionChanged || this.tickCount % 60 == 0;
                boolean sentPosition = false;
                boolean sentRotation = false;
                long xa = this.positionCodec.encodeX(currentPosition);
                long ya = this.positionCodec.encodeY(currentPosition);
                long za = this.positionCodec.encodeZ(currentPosition); // Leaf - Multithreaded tracker - diff on change
                boolean deltaTooBig = xa < -32768L || xa > 32767L || ya < -32768L || ya > 32767L || za < -32768L || za > 32767L;
                boolean onGroundChanged = this.wasOnGround != this.entity.onGround(); // Purpur - Dont send useless entity packets
                if (this.forceStateResync || this.entity.getRequiresPrecisePosition() // Paper - fix desync when a player is added to the tracker
                    || deltaTooBig
                    || this.teleportDelay > 400
                    || this.wasRiding
                    || onGroundChanged) { // Purpur - Dont send useless entity packets // Leaf - Multithreaded tracker - diff on change
                    this.wasOnGround = this.entity.onGround();
                    this.teleportDelay = 0;
                    packet = ClientboundEntityPositionSyncPacket.of(this.entity);
                    sentPosition = true;
                    sentRotation = true; // Leaf - Multithreaded tracker - diff on change
                    // Gale start - Airplane - better checking for useless move packets
                } else { // Leaf - Multithreaded tracker - diff on change
                if (pos || shouldSendRotation || this.entity instanceof AbstractArrow) {
                    if ((!pos || !shouldSendRotation) && !(this.entity instanceof AbstractArrow)) { // Leaf - Multithreaded tracker - diff on change
                        // Gale end - Airplane - better checking for useless move packets
                    if (pos) {
                        packet = new ClientboundMoveEntityPacket.Pos(this.entity.getId(), (short)xa, (short)ya, (short)za, this.entity.onGround());
                        sentPosition = true;
                    } else if (shouldSendRotation) { // Leaf - Multithreaded tracker - diff on change
                        packet = new ClientboundMoveEntityPacket.Rot(this.entity.getId(), yRotn, xRotn, this.entity.onGround());
                        sentRotation = true;
                    }
                } else { // Leaf - Multithreaded tracker - diff on change
                    packet = new ClientboundMoveEntityPacket.PosRot(this.entity.getId(), (short)xa, (short)ya, (short)za, yRotn, xRotn, this.entity.onGround());
                    sentPosition = true;
                    sentRotation = true;
                    }
                } // Gale - Airplane - better checking for useless move packets
                } // Leaf - Multithreaded tracker - diff on change

                if (net.feathermc.feather.config.modules.opt.ReduceUselessPackets.reduceUselessEntityMovePackets && !onGroundChanged && isUselessMoveEntityPacket(packet)) packet = null; // Purpur - Dont send useless entity packets

                if (this.entity.needsSync || this.trackDelta || this.entity instanceof LivingEntity livingEntity && livingEntity.isFallFlying()) {
                    Vec3 movement = this.entity.getDeltaMovement();
                    if (movement != this.lastSentMovement) { // SparklyPaper start - skip distanceToSqr call in ServerEntity#sendChanges if the delta movement hasn't changed
                    double diff = movement.distanceToSqr(this.lastSentMovement);
                    if (diff > 1.0E-7 || diff > 0.0 && movement.lengthSqr() == 0.0) {
                        this.lastSentMovement = movement; // Leaf - Multithreaded tracker - diff on change
                        if (this.entity instanceof AbstractHurtingProjectile projectile) {
                            this.synchronizer
                                .sendToTrackingPlayers(
                                    new ClientboundBundlePacket(
                                        List.of( // Leaf - Multithreaded tracker - diff on change
                                            new ClientboundSetEntityMotionPacket(this.entity.getId(), this.lastSentMovement),
                                            new ClientboundProjectilePowerPacket(projectile.getId(), projectile.accelerationPower)
                                        )
                                    )
                                );
                        } else if (!net.feathermc.feather.config.modules.opt.ReduceUselessPackets.filterClientboundSetEntityMotionPacket || // Canvas - filter ClientboundSetEntityMotionPacket
                            (this.entity instanceof net.minecraft.world.entity.item.ItemEntity && positionChanged) ||
                            this.entity instanceof net.minecraft.world.entity.projectile.EyeOfEnder ||
                            this.entity instanceof net.minecraft.world.entity.animal.squid.Squid ||
                            this.entity instanceof net.minecraft.world.entity.projectile.ShulkerBullet
                        ) { // Leaf - Multithreaded tracker - diff on change
                            this.synchronizer.sendToTrackingPlayers(new ClientboundSetEntityMotionPacket(this.entity.getId(), this.lastSentMovement));
                        }
                    }
                    } // SparklyPaper end
                }

                if (packet != null) { // Leaf - Multithreaded tracker - diff on change
                    this.synchronizer.sendToTrackingPlayers(packet);
                }

                this.sendDirtyEntityData(); // Leaf - Multithreaded tracker - diff on change
                if (sentPosition) {
                    this.positionCodec.setBase(currentPosition);
                }

                if (sentRotation) { // Leaf - Multithreaded tracker - diff on change
                    this.lastSentYRot = yRotn;
                    this.lastSentXRot = xRotn;
                }

                this.wasRiding = false; // Leaf - Multithreaded tracker - diff on change
            }

            byte yHeadRot = Mth.packDegrees(this.entity.getYHeadRot()); // Leaf - Multithreaded tracker - diff on change
            if (Math.abs(yHeadRot - this.lastSentYHeadRot) >= 1) {
                this.synchronizer.sendToTrackingPlayers(new ClientboundRotateHeadPacket(this.entity, yHeadRot));
                this.lastSentYHeadRot = yHeadRot;
            }

            this.entity.needsSync = false; // Leaf - Multithreaded tracker - diff on change
            this.forceStateResync = false; // Paper - fix desync when a player is added to the tracker
        }

        this.tickCount++; // Leaf - Multithreaded tracker - diff on change
        if (this.entity.hurtMarked) {
            // CraftBukkit start - Create PlayerVelocity event
            boolean cancelled = false;

            if (this.entity instanceof ServerPlayer) { // Leaf - Multithreaded tracker - diff on change
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) this.entity.getBukkitEntity();
                org.bukkit.util.Vector velocity = player.getVelocity();

                org.bukkit.event.player.PlayerVelocityEvent event = new org.bukkit.event.player.PlayerVelocityEvent(player, velocity.clone()); // Leaf - Multithreaded tracker - diff on change
                if (!event.callEvent()) {
                    cancelled = true; // Leaf - Multithreaded tracker - diff on change
                } else if (!velocity.equals(event.getVelocity())) {
                    player.setVelocity(event.getVelocity());
                } // Leaf - Multithreaded tracker - diff on change
            }

            if (cancelled) {
                return;
            } // Leaf - Multithreaded tracker - diff on change
            // CraftBukkit end
            this.entity.hurtMarked = false;
            this.synchronizer.sendToTrackingPlayersAndSelf(new ClientboundSetEntityMotionPacket(this.entity)); // Leaf - Multithreaded tracker - diff on change
        }
    }

    // Purpur start - Dont send useless entity packets
    private boolean isUselessMoveEntityPacket(final @Nullable Packet<?> packet) {
        if (!(packet instanceof ClientboundMoveEntityPacket moveEntityPacket)) return false;
        return switch (packet) {
            case ClientboundMoveEntityPacket.Pos ignored ->
                moveEntityPacket.getXa() == 0 && moveEntityPacket.getYa() == 0 && moveEntityPacket.getZa() == 0;
            case ClientboundMoveEntityPacket.PosRot ignored ->
                moveEntityPacket.getXa() == 0 && moveEntityPacket.getYa() == 0 && moveEntityPacket.getZa() == 0 && moveEntityPacket.getYRot() == 0 && moveEntityPacket.getXRot() == 0;
            case ClientboundMoveEntityPacket.Rot ignored ->
                moveEntityPacket.getYRot() == 0 && moveEntityPacket.getXRot() == 0;
            default -> false;
        };
    }
    // Purpur end - Dont send useless entity packets

    private void handleMinecartPosRot(final NewMinecartBehavior newMinecartBehavior, final byte yRotn, final byte xRotn, final boolean shouldSendRotation) {
        this.sendDirtyEntityData();
        if (newMinecartBehavior.lerpSteps.isEmpty()) {
            Vec3 movement = this.entity.getDeltaMovement(); // Leaf - Multithreaded tracker - diff on change
            double diff = movement.distanceToSqr(this.lastSentMovement);
            Vec3 currentPosition = this.entity.trackingPosition();
            boolean positionChanged = this.positionCodec.delta(currentPosition).lengthSqr() >= 7.6293945E-6F;
            boolean shouldSendPosition = positionChanged || this.tickCount % 60 == 0;
            if (shouldSendPosition || shouldSendRotation || diff > 1.0E-7) {
                this.synchronizer
                    .sendToTrackingPlayers( // Leaf - Multithreaded tracker - diff on change
                        new ClientboundMoveMinecartPacket(
                            this.entity.getId(),
                            List.of(
                                new NewMinecartBehavior.MinecartStep(
                                    this.entity.position(), this.entity.getDeltaMovement(), this.entity.getYRot(), this.entity.getXRot(), 1.0F
                                )
                            )
                        )
                    );
            }
        } else {
            this.synchronizer.sendToTrackingPlayers(new ClientboundMoveMinecartPacket(this.entity.getId(), List.copyOf(newMinecartBehavior.lerpSteps)));
            newMinecartBehavior.lerpSteps.clear();
        }

        this.lastSentYRot = yRotn;
        this.lastSentXRot = xRotn;
        this.positionCodec.setBase(this.entity.position());
    }

    public void removePairing(final ServerPlayer player) {
        if (net.feathermc.feather.config.modules.async.MultithreadedTracker.enabled) { ((ServerLevel) this.entity.level()).leaf$asyncTracker.ctx().stopSeenByPlayer(player.connection, this.entity); return; } // Leaf - Multithreaded tracker
        this.entity.stopSeenByPlayer(player);
        player.connection.send(new ClientboundRemoveEntitiesPacket(this.entity.getId()));
    }

    public void addPairing(final ServerPlayer player) {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        this.sendPairingData(player, packets::add);
        player.connection.send(new ClientboundBundlePacket(packets));
        this.entity.startSeenByPlayer(player);
    }

    public void sendPairingData(final ServerPlayer player, final Consumer<Packet<ClientGamePacketListener>> broadcast) {
        this.entity.updateDataBeforeSync();
        if (this.entity.isRemoved()) {
            // CraftBukkit start - Remove useless error spam, just return
            // LOGGER.warn("Fetching packet for removed entity {}", this.entity);
            return;
            // CraftBukkit end
        }

        Packet<ClientGamePacketListener> packet = this.entity.getAddEntityPacket(this);
        broadcast.accept(packet);
        if (this.trackedDataValues != null) {
            broadcast.accept(new ClientboundSetEntityDataPacket(this.entity.getId(), this.trackedDataValues));
        }

        if (this.entity instanceof LivingEntity livingEntity) {
            Collection<AttributeInstance> attributes = livingEntity.getAttributes().getSyncableAttributes();
            if (!attributes.isEmpty()) {
                // CraftBukkit start - If sending own attributes send scaled health instead of current maximum health
                if (this.entity.getId() == player.getId()) {
                    ((ServerPlayer) this.entity).getBukkitEntity().injectScaledMaxHealth(attributes, false);
                }
                // CraftBukkit end
                broadcast.accept(new ClientboundUpdateAttributesPacket(this.entity.getId(), attributes));
            }
        }

        if (this.entity instanceof LivingEntity livingEntity) {
            List<Pair<EquipmentSlot, ItemStack>> slots = Lists.newArrayList();

            for (EquipmentSlot slot : EquipmentSlot.VALUES_ARRAY) { // Gale - JettPack - reduce array allocations
                ItemStack itemStack = livingEntity.getItemBySlot(slot);
                if (!itemStack.isEmpty()) {
                    slots.add(Pair.of(slot, itemStack.copy()));
                }
            }

            if (!slots.isEmpty()) {
                broadcast.accept(new ClientboundSetEquipmentPacket(this.entity.getId(), slots, true)); // Paper - data sanitization
            }
            ((LivingEntity) this.entity).detectEquipmentUpdates(); // CraftBukkit - SPIGOT-3789: sync again immediately after sending
        }

        if (!this.entity.getPassengers().isEmpty()) {
            broadcast.accept(new ClientboundSetPassengersPacket(this.entity));
        }

        if (this.entity.isPassenger()) {
            broadcast.accept(new ClientboundSetPassengersPacket(this.entity.getVehicle()));
        }

        if (this.entity instanceof Leashable leashable && leashable.isLeashed()) {
            broadcast.accept(new ClientboundSetEntityLinkPacket(this.entity, leashable.getLeashHolder()));
        }
    }

    public Vec3 getPositionBase() {
        return this.positionCodec.getBase();
    }

    public Vec3 getLastSentMovement() {
        return this.lastSentMovement;
    }

    public float getLastSentXRot() {
        return Mth.unpackDegrees(this.lastSentXRot);
    }

    public float getLastSentYRot() {
        return Mth.unpackDegrees(this.lastSentYRot);
    }

    public float getLastSentYHeadRot() {
        return Mth.unpackDegrees(this.lastSentYHeadRot);
    }

    // Leaf - Multithreaded tracker - diff on change
    private void sendDirtyEntityData() {
        SynchedEntityData entityData = this.entity.getEntityData();
        List<SynchedEntityData.DataValue<?>> packedValues = entityData.packDirty();
        if (packedValues != null) {
            this.trackedDataValues = entityData.getNonDefaultValues();
            this.synchronizer.sendToTrackingPlayersAndSelf(new ClientboundSetEntityDataPacket(this.entity.getId(), packedValues));
        }
        // Leaf - Multithreaded tracker - diff on change

        if (this.entity instanceof LivingEntity livingEntity) {
            Set<AttributeInstance> attributes = livingEntity.getAttributes().getAttributesToSync();
            if (!attributes.isEmpty()) {
                // Leaf - Multithreaded tracker - diff on change
                // CraftBukkit start - Send scaled max health
                if (this.entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.getBukkitEntity().injectScaledMaxHealth(attributes, false);
                }
                // CraftBukkit end
                this.synchronizer.sendToTrackingPlayersAndSelf(new ClientboundUpdateAttributesPacket(this.entity.getId(), attributes));
            }

            //attributes.clear(); // Leaf - optimize attribute
        }
    }

    // Leaf start - Multithreaded tracker
    public void leaf$sendChanges(final net.feathermc.feather.async.tracker.TrackerCtx ctx, final ChunkMap.TrackedEntity tracker, final boolean force) {
        if (this.forceStateResync && !force) {
            this.forceStateResync = false;
            ctx.forceResync(tracker);
            return;
        }
        if (this.entity.leaf$wantUpdateData()) {
            ctx.wantUpdateData(tracker);
        }
        // Paper start - optimise collisions
        if (this.entity.moonrise$isHardColliding()) {
            this.teleportDelay = 9999;
        }
        // Paper end - optimise collisions
        //this.entity.updateDataBeforeSync(); // Move to AsyncTracker#handlePlayer
        List<Entity> passengers = this.entity.getPassengers();
        if (!passengers.equals(this.lastPassengers)) {
            ctx.sendToTrackingPlayersFiltered(
                tracker,
                new ClientboundSetPassengersPacket(this.entity),
                player -> passengers.contains(player) == this.lastPassengers.contains(player)
                );
            // Paper start - Allow riding players
            if (this.entity instanceof ServerPlayer player) {
                ctx.send(player.connection, new ClientboundSetPassengersPacket(this.entity));
            }
            // Paper end - Allow riding players
            this.lastPassengers = passengers;
        }
        if (!tracker.seenBy.isEmpty() && this.entity instanceof ItemFrame frame /*&& this.tickCount % 10 == 0*/) { // CraftBukkit - moved tickCount below // Paper - Perf: Only tick item frames if players can see it
            if (this.level.paperConfig().maps.itemFrameCursorUpdateInterval > 0 && this.tickCount % this.level.paperConfig().maps.itemFrameCursorUpdateInterval == 0 && frame.getItem().getItem() instanceof MapItem) { // CraftBukkit - Moved this.tickCounter % 10 logic here so item frames do not enter the other blocks // Paper - Make item frame map cursor update interval configurable
                ctx.updateItemFrame(frame);
            }
            ctx.sendDirtyEntityData(tracker);
        }

        if (force || this.tickCount % this.updateInterval == 0 || this.entity.needsSync || this.entity.getEntityData().isDirty()) { // Paper - fix desync when a player is added to the tracker
            byte yRotn = Mth.packDegrees(this.entity.getYRot());
            byte xRotn = Mth.packDegrees(this.entity.getXRot());
            boolean shouldSendRotation = Math.abs(yRotn - this.lastSentYRot) >= 1 || Math.abs(xRotn - this.lastSentXRot) >= 1;
            if (this.entity.isPassenger()) {
                if (shouldSendRotation) {
                    ctx.sendToTrackingPlayers(tracker, new ClientboundMoveEntityPacket.Rot(this.entity.getId(), yRotn, xRotn, this.entity.onGround()));
                    this.lastSentYRot = yRotn;
                    this.lastSentXRot = xRotn;
                }

                this.positionCodec.setBase(this.entity.trackingPosition());
                ctx.sendDirtyEntityData(tracker);
                this.wasRiding = true;
            } else if (this.entity instanceof AbstractMinecart minecart
                && minecart.getBehavior() instanceof NewMinecartBehavior newMinecartBehavior) {
                this.leaf$HandleMinecartPosRot(ctx, tracker, newMinecartBehavior, yRotn, xRotn, shouldSendRotation);
            } else {
                this.teleportDelay++;
                Vec3 currentPosition = this.entity.trackingPosition();
                // Paper start - reduce allocation of Vec3D here
                Vec3 base = this.positionCodec.base;
                double vec3_dx = currentPosition.x - base.x;
                double vec3_dy = currentPosition.y - base.y;
                double vec3_dz = currentPosition.z - base.z;
                boolean positionChanged = (vec3_dx * vec3_dx + vec3_dy * vec3_dy + vec3_dz * vec3_dz) >= 7.62939453125E-6D;
                // Paper end - reduce allocation of Vec3D here
                Packet<ClientGamePacketListener> packet = null;
                boolean pos = positionChanged || this.tickCount % 60 == 0;
                boolean sentPosition = false;
                boolean sentRotation = false;
                long xa = this.positionCodec.encodeX(currentPosition);
                long ya = this.positionCodec.encodeY(currentPosition);
                long za = this.positionCodec.encodeZ(currentPosition);
                boolean deltaTooBig = xa < -32768L || xa > 32767L || ya < -32768L || ya > 32767L || za < -32768L || za > 32767L;
                boolean onGroundChanged = this.wasOnGround != this.entity.onGround();
                if (force || this.entity.getRequiresPrecisePosition() // Paper - fix desync when a player is added to the tracker
                    || deltaTooBig
                    || this.teleportDelay > 400
                    || this.wasRiding
                    || onGroundChanged) {
                    this.wasOnGround = this.entity.onGround();
                    this.teleportDelay = 0;
                    packet = ClientboundEntityPositionSyncPacket.of(this.entity);
                    sentPosition = true;
                    sentRotation = true;
                    // Gale start - Airplane - better checking for useless move packets
                } else {
                    if (pos || shouldSendRotation || this.entity instanceof AbstractArrow) {
                        if ((!pos || !shouldSendRotation) && !(this.entity instanceof AbstractArrow)) {
                            if (pos) {
                                packet = new ClientboundMoveEntityPacket.Pos(this.entity.getId(), (short) xa, (short) ya, (short) za, this.entity.onGround());
                                sentPosition = true;
                            } else if (shouldSendRotation) {
                                packet = new ClientboundMoveEntityPacket.Rot(this.entity.getId(), yRotn, xRotn, this.entity.onGround());
                                sentRotation = true;
                            }
                        } else {
                            packet = new ClientboundMoveEntityPacket.PosRot(this.entity.getId(), (short) xa, (short) ya, (short) za, yRotn, xRotn, this.entity.onGround());
                            sentPosition = true;
                            sentRotation = true;
                        }
                    }
                }
                // Gale end - Airplane - better checking for useless move packets

                if (net.feathermc.feather.config.modules.opt.ReduceUselessPackets.reduceUselessEntityMovePackets && !onGroundChanged && isUselessMoveEntityPacket(packet)) packet = null; // Purpur - Dont send useless entity packets

                if (this.entity.needsSync || this.trackDelta || this.entity instanceof LivingEntity && ((LivingEntity)this.entity).isFallFlying()) {
                    Vec3 movement = this.entity.getDeltaMovement();
                    if (movement != this.lastSentMovement) { // SparklyPaper start - skip distanceToSqr call in ServerEntity#sendChanges if the delta movement hasn't changed
                        double diff = movement.distanceToSqr(this.lastSentMovement);
                        if (diff > 1.0E-7 || diff > 0.0 && movement.lengthSqr() == 0.0) {
                            this.lastSentMovement = movement;
                            if (this.entity instanceof AbstractHurtingProjectile projectile) {
                                ctx.sendToTrackingPlayers(tracker, new ClientboundBundlePacket(List.of(
                                    new ClientboundSetEntityMotionPacket(this.entity.getId(), this.lastSentMovement),
                                    new ClientboundProjectilePowerPacket(projectile.getId(), projectile.accelerationPower)
                                )));
                            } else if (!net.feathermc.feather.config.modules.opt.ReduceUselessPackets.filterClientboundSetEntityMotionPacket || // Canvas - filter ClientboundSetEntityMotionPacket
                                (this.entity instanceof net.minecraft.world.entity.item.ItemEntity && positionChanged) ||
                                this.entity instanceof net.minecraft.world.entity.projectile.EyeOfEnder ||
                                this.entity instanceof net.minecraft.world.entity.animal.squid.Squid ||
                                this.entity instanceof net.minecraft.world.entity.projectile.ShulkerBullet
                            ) {
                                ctx.sendToTrackingPlayers(tracker, new ClientboundSetEntityMotionPacket(this.entity.getId(), this.lastSentMovement));
                            }
                        }
                    } // SparklyPaper end
                }

                if (packet != null) {
                    ctx.sendToTrackingPlayers(tracker, packet);
                }

                ctx.sendDirtyEntityData(tracker);
                if (sentPosition) {
                    this.positionCodec.setBase(currentPosition);
                }

                if (sentRotation) {
                    this.lastSentYRot = yRotn;
                    this.lastSentXRot = xRotn;
                }

                this.wasRiding = false;
            }

            byte yHeadRot = Mth.packDegrees(this.entity.getYHeadRot());
            if (Math.abs(yHeadRot - this.lastSentYHeadRot) >= 1) {
                ctx.sendToTrackingPlayers(tracker, new ClientboundRotateHeadPacket(this.entity, yHeadRot));
                this.lastSentYHeadRot = yHeadRot;
            }

            this.entity.needsSync = false;
            //this.forceStateResync = false; // Paper - fix desync when a player is added to the tracker // move up
        }

        this.tickCount++;
        if (this.entity.hurtMarked && !(this.entity instanceof ServerPlayer)) {
            this.entity.hurtMarked = false;
            ctx.sendToTrackingPlayersAndSelf(tracker, new ClientboundSetEntityMotionPacket(this.entity));
        }
    }

    private void leaf$HandleMinecartPosRot(final net.feathermc.feather.async.tracker.TrackerCtx ctx, final ChunkMap.TrackedEntity tracker, final NewMinecartBehavior newMinecartBehavior, final byte yRotn, final byte xRotn, final boolean shouldSendRotation) {
        ctx.sendDirtyEntityData(tracker);
        if (newMinecartBehavior.lerpSteps.isEmpty()) {
            Vec3 movement = this.entity.getDeltaMovement();
            double diff = movement.distanceToSqr(this.lastSentMovement);
            Vec3 currentPosition = this.entity.trackingPosition();
            boolean positionChanged = this.positionCodec.delta(currentPosition).lengthSqr() >= 7.6293945E-6F;
            boolean shouldSendPosition = positionChanged || this.tickCount % 60 == 0;
            if (shouldSendPosition || shouldSendRotation || diff > 1.0E-7) {
                ctx.sendToTrackingPlayers(tracker, new ClientboundMoveMinecartPacket(
                    this.entity.getId(),
                    List.of(
                        new NewMinecartBehavior.MinecartStep(
                            this.entity.position(), this.entity.getDeltaMovement(), this.entity.getYRot(), this.entity.getXRot(), 1.0F
                        ))));
            }
        } else {
            ctx.sendToTrackingPlayers(tracker, new ClientboundMoveMinecartPacket(this.entity.getId(), List.copyOf(newMinecartBehavior.lerpSteps)));
            newMinecartBehavior.lerpSteps.clear();
        }

        this.lastSentYRot = yRotn;
        this.lastSentXRot = xRotn;
        this.positionCodec.setBase(this.entity.position());
    }
    // Leaf end - Multithreaded tracker

    public interface Synchronizer {
        void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet);

        void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet);

        void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> predicate);
    }
}
