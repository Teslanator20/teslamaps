// Ported from ADM's p3sim/clear/bots/BotBody.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

/**
 * A teammate's server body: a light mob (an Avatar) that the client is told is a real minecraft:player with the bot's own profile id. It
 * touches nothing: not pushable, not pickable, takes no damage.
 */
public class AdsBotBody extends Avatar {
    public AdsBotBody(Level level, UUID profileId) {
        super(EntityType.MANNEQUIN, level);
        setUUID(profileId);
        // second skin layers on
        entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);
    }

    @Override public ResolvableProfile getProfile() { return Mannequin.DEFAULT_PROFILE; }

    /** The add packet as the client wants it: a PLAYER with this body's position, look and profile id. */
    public Packet<ClientGamePacketListener> addPacket() {
        return new ClientboundAddEntityPacket(getId(), getUUID(), getX(), getY(), getZ(), getXRot(), getYRot(), EntityType.PLAYER, 0, getDeltaMovement(), (double) yHeadRot);
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) { return addPacket(); }

    @Override public boolean shouldBeSaved() { return false; }

    @Override public boolean fireImmune() { return true; }

    @Override public boolean isPushable() { return false; }

    @Override public boolean isPickable() { return false; }

    /** Teammates do not push. */
    @Override protected void pushEntities() {}
}
