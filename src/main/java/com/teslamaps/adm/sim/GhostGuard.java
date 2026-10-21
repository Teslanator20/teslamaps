package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/GhostGuard.kt

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class GhostGuard {
    private GhostGuard() {}

    private static final int EVERY = 15;
    private static final int R = 5;
    private static int ticks = 0;

    private static Map<BlockPos, BlockState> suspects = null;

    public static void clientTick() {
        if (!P3Sim.inSim() || SimServer.level() == null) return;
        if (++ticks % EVERY != 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        ClientLevel level = mc.level;
        if (level == null) return;
        BlockPos c = p.blockPosition();
        HashMap<BlockPos, BlockState> snap = new HashMap<>(1200);
        for (int y = -3; y <= 4; y++) for (int z = -R; z <= R; z++) for (int x = -R; x <= R; x++) {
            BlockPos pos = c.offset(x, y, z);
            snap.put(pos, level.getBlockState(pos));
        }
        SimServer.run("ghost check", s -> compare(snap));
    }

    private static String shortId(BlockState s) {
        String d = s.getBlock().getDescriptionId();
        int i = d.lastIndexOf('.');
        return i < 0 ? d : d.substring(i + 1);
    }

    private static void compare(Map<BlockPos, BlockState> snap) {
        ServerLevel level = SimServer.level();
        if (level == null) return;
        ServerPlayer player = SimServer.player();
        if (player == null) return;
        HashMap<BlockPos, BlockState> diff = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : snap.entrySet())
            if (level.getBlockState(e.getKey()).getBlock() != e.getValue().getBlock()) diff.put(e.getKey(), e.getValue());
        Map<BlockPos, BlockState> last = suspects != null ? suspects : Map.of();
        HashMap<BlockPos, BlockState> ghosts = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : diff.entrySet()) if (e.getValue().equals(last.get(e.getKey()))) ghosts.put(e.getKey(), e.getValue());
        HashMap<BlockPos, BlockState> next = new HashMap<>(diff);
        next.keySet().removeAll(ghosts.keySet());
        suspects = next;
        if (ghosts.isEmpty()) return;
        for (BlockPos pos : ghosts.keySet()) player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        List<String> parts = new ArrayList<>();
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> e : ghosts.entrySet()) {
            if (i++ >= 8) break;
            BlockPos pos = e.getKey();
            parts.add(pos.getX() + "," + pos.getY() + "," + pos.getZ() + " game " + shortId(e.getValue()) + " server " + shortId(level.getBlockState(pos)));
        }
        Fight.Phase ph = Fight.phase;
        Ec.LOG.info("[p3sim] ghost blocks fixed ({}): {}", ghosts.size(), String.join("; ", parts)
                + " | phase " + (ph != null ? ph.getClass().getSimpleName() : null) + " practice " + Practice.active() + " n " + Fight.serverTick);
    }
}
