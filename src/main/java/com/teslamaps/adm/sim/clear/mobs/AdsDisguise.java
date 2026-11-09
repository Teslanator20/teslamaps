// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

public class AdsDisguise extends Avatar implements AdsMobBody {
    public AdsDisguise(Level level) { super(EntityTypes.MANNEQUIN, level); }

    @Override public ResolvableProfile getProfile() { return Mannequin.DEFAULT_PROFILE; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(getId(), getUUID(), getX(), getY(), getZ(), getXRot(), getYRot(), EntityTypes.PLAYER, 0, getDeltaMovement(), (double) yHeadRot);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isEffectiveAi() { return true; }
}
