package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Station.kt

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class TransientStand extends SimStand {
    public int trackSquare = 0;

    public TransientStand(Level level) { super(level); }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return (trackSquare <= 0 || (Math.abs(player.getX() - getX()) <= trackSquare && Math.abs(player.getZ() - getZ()) <= trackSquare)) && super.broadcastToPlayer(player);
    }
}
