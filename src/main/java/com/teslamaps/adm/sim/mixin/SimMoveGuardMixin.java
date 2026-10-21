package com.teslamaps.adm.sim.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.adm.sim.SimServer;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The sim's server never snaps a player back for a move it would call wrong (the sim's blocks change under them). */
@Mixin(ServerGamePacketListenerImpl.class)
public class SimMoveGuardMixin {
    @Shadow public ServerPlayer player;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Shadow private int awaitingTeleport;

    private boolean teslamaps$simServer() {
        return SimServer.server != null && this.player != null && this.player.level().getServer() == SimServer.server;
    }

    @Inject(method = "handleAcceptTeleportPacket", at = @At("HEAD"), cancellable = true)
    private void teslamaps$dropStaleAccept(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        if (teslamaps$simServer() && packet.getId() == this.awaitingTeleport && this.awaitingPositionFromClient == null) ci.cancel();
    }

    @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean teslamaps$noWrongMoveSnap(ServerLevel level, Entity entity, AABB box, Operation<Boolean> original) {
        return !teslamaps$simServer() && original.call(level, entity, box);
    }

    @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isEntityCollidingWithAnythingNew(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;DDD)Z"))
    private boolean teslamaps$noNewCollisionSnap(ServerGamePacketListenerImpl self, LevelReader level, Entity entity, AABB box, double x, double y, double z, Operation<Boolean> original) {
        return !teslamaps$simServer() && original.call(self, level, entity, box, x, y, z);
    }
}
