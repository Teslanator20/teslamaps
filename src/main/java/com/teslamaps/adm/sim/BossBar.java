package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Phases.kt

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.boss.wither.WitherBoss;

public final class BossBar {
    private BossBar() {}

    private static ServerBossEvent bar = null;

    public static void show(String name) { show(name, 1f); }

    public static void show(String name, float progress) {
        ServerBossEvent b = bar;
        if (b == null) {
            b = new ServerBossEvent(UUID.randomUUID(), Sim.legacy(name), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            bar = b;
        }
        b.setName(Sim.legacy(name));
        b.setProgress(Math.clamp(progress, 0f, 1f));
        List<ServerPlayer> all = Sim.players();
        if (!new HashSet<>(b.getPlayers()).equals(new HashSet<>(all))) {
            b.removeAllPlayers();
            for (ServerPlayer p : all) b.addPlayer(p);
        }
    }

    public static void showTo(ServerPlayer p) {
        ServerBossEvent b = bar;
        if (b != null) { if (!b.getPlayers().contains(p)) b.addPlayer(p); }
    }

    public static void progress(float f) {
        ServerBossEvent b = bar;
        if (b != null) b.setProgress(Math.clamp(f, 0f, 1f));
    }

    public static void resend() {
        ServerBossEvent b = bar;
        if (b == null) return;
        for (ServerPlayer p : b.getPlayers()) {
            p.connection.send(ClientboundBossEventPacket.createUpdateNamePacket(b));
            p.connection.send(ClientboundBossEventPacket.createUpdateStylePacket(b));
            p.connection.send(ClientboundBossEventPacket.createUpdateProgressPacket(b));
        }
    }

    public static float progress() {
        ServerBossEvent b = bar;
        return b != null ? b.getProgress() : 0f;
    }

    public static void hide() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    private static Field ownField;
    private static boolean ownFieldDone = false;

    private static synchronized Field ownField() {
        if (!ownFieldDone) {
            ownFieldDone = true;
            for (Field f : WitherBoss.class.getDeclaredFields()) {
                if (ServerBossEvent.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    ownField = f;
                    break;
                }
            }
        }
        return ownField;
    }

    public static void hideOwn(WitherBoss w) {
        try {
            Field f = ownField();
            Object ev = f != null ? f.get(w) : null;
            if (ev instanceof ServerBossEvent s) s.setVisible(false);
        } catch (Throwable ignored) {
        }
    }
}
