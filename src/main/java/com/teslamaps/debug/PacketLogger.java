package com.teslamaps.debug;

import com.teslamaps.TeslaMaps;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketLogger {

    private static final Set<String> NOISY = Set.of(
            "ClientboundKeepAlivePacket", "ServerboundKeepAlivePacket", "ClientboundPingPacket", "ServerboundPongPacket",
            "ClientboundPlayerInfoRemovePacket", "ClientboundPlayerInfoUpdatePacket", "ClientboundSetObjectivePacket",
            "ClientboundSetPlayerTeamPacket", "ClientboundSetScorePacket", "ClientboundSetExperiencePacket",
            "ClientboundSetHealthPacket", "ClientboundSetTimePacket", "ClientboundLevelChunkWithLightPacket",
            "ClientboundSectionBlocksUpdatePacket", "ClientboundBlockUpdatePacket", "ClientboundBlockEventPacket",
            "ClientboundForgetLevelChunkPacket", "ClientboundSetChunkCacheCenterPacket", "ClientboundLightUpdatePacket",
            "ClientboundBundlePacket", "ClientboundBundleDelimiterPacket",
            "ClientboundTeleportEntityPacket", "Pos", "PosRot", "Rot", "StatusOnly", "ClientboundRotateHeadPacket",
            "ClientboundEntityPositionSyncPacket", "ClientboundSetEntityMotionPacket", "ClientboundUpdateAttributesPacket",
            "ClientboundAnimatePacket", "ClientboundBossEventPacket", "ClientboundLevelParticlesPacket",
            "ServerboundMovePlayerPacket", "ServerboundClientTickEndPacket", "ServerboundPlayerCommandPacket",
            "ServerboundSetCarriedItemPacket", "ServerboundPlayerInputPacket", "ServerboundChunkBatchReceivedPacket",
            "ClientboundChunkBatchStartPacket", "ClientboundChunkBatchFinishedPacket");

    private record Entry(boolean in, Packet<?> packet) {}

    private static final ConcurrentLinkedQueue<Entry> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile long until = 0;
    private static volatile String filter = null;
    private static volatile boolean full = false;
    private static volatile int dir = 0;
    private static double rangeSq = 0;
    private static int entityId = -1;
    private static int logged = 0;

    public static void register() {
        Feature.of("PacketLogger").when(() -> until != 0).tickAlways(PacketLogger::tick);
    }

    public static String start(int seconds, String options) {
        String f = null;
        boolean fl = false;
        int d = 0;
        double r = 0;
        int id = -1;
        if (options != null) {
            for (String t : options.trim().split("\\s+")) {
                if (t.isEmpty()) continue;
                String l = t.toLowerCase(Locale.ROOT);
                try {
                    if (l.equals("full") || l.equals("all")) fl = true;
                    else if (l.equals("in")) d = 1;
                    else if (l.equals("out")) d = 2;
                    else if (l.startsWith("r=")) r = Double.parseDouble(l.substring(2));
                    else if (l.startsWith("id=")) id = Integer.parseInt(l.substring(3));
                    else f = l;
                } catch (NumberFormatException e) {
                    return "§cBad number in " + t;
                }
            }
        }
        QUEUE.clear();
        filter = f;
        full = fl || f != null;
        dir = d;
        rangeSq = r * r;
        entityId = id;
        logged = 0;
        until = System.currentTimeMillis() + seconds * 1000L;
        TeslaMaps.LOGGER.info("[PacketLog] armed {}s filter={} full={} dir={} range={} id={}", seconds, f, full, d, r, id);
        return "§fPacket log on for §e" + seconds + "s§f -> latest.log §7[PacketLog]";
    }

    public static void stop() {
        if (until != 0) TeslaMaps.LOGGER.info("[PacketLog] stopped, {} packets", logged);
        until = 0;
        QUEUE.clear();
    }

    public static void onPacket(Packet<?> packet, boolean in) {
        if (until == 0) return;
        if (packet instanceof net.minecraft.network.protocol.BundlePacket<?> b) {
            for (Packet<?> sub : b.subPackets()) onPacket(sub, in);
            return;
        }
        int d = dir;
        if (d == 1 && !in || d == 2 && in) return;
        String name = packet.getClass().getSimpleName();
        if (!full && NOISY.contains(name)) return;
        String f = filter;
        if (f != null && !name.toLowerCase(Locale.ROOT).contains(f)) return;
        if (QUEUE.size() < 20000) QUEUE.add(new Entry(in, packet));
    }

    private static void tick() {
        if (System.currentTimeMillis() > until) {
            TeslaMaps.LOGGER.info("[PacketLog] finished, {} packets", logged);
            until = 0;
            QUEUE.clear();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fPacket log done (" + logged + " packets)."));
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entry e;
        int n = 0;
        while (n++ < 5000 && (e = QUEUE.poll()) != null) log(mc, e);
    }

    private static void log(Minecraft mc, Entry e) {
        Packet<?> p = e.packet;
        int id = entityIdOf(p);
        if (entityId >= 0 && id != entityId) return;
        StringBuilder sb = new StringBuilder(e.in ? "<- " : "-> ").append(p.getClass().getSimpleName());
        if (id >= 0) {
            sb.append(" id=").append(id);
            Entity ent = mc.level == null ? null : mc.level.getEntity(id);
            if (ent == null) {
                if (rangeSq > 0) return;
                sb.append(" (no entity)");
            } else if (ent == mc.player) {
                sb.append(" own");
            } else {
                double dsq = mc.player == null ? 0 : mc.player.distanceToSqr(ent);
                if (rangeSq > 0 && dsq > rangeSq) return;
                sb.append(' ').append(ent.getType().toShortString())
                        .append(" dist=").append(Math.round(Math.sqrt(dsq) * 10) / 10.0);
            }
        } else if (p instanceof ClientboundRemoveEntitiesPacket r) {
            sb.append(" ids=").append(r.entityIds());
        }
        String s;
        try { s = String.valueOf(p); } catch (Throwable t) { s = "?"; }
        if (s.length() > 600) s = s.substring(0, 600) + "…";
        sb.append(" | ").append(s);
        logged++;
        TeslaMaps.LOGGER.info("[PacketLog] {}", sb);
    }

    private static int entityIdOf(Packet<?> p) {
        if (p instanceof ClientboundSetEntityDataPacket x) return x.id();
        if (p instanceof ClientboundUpdateAttributesPacket x) return x.getEntityId();
        if (p instanceof ClientboundSetEquipmentPacket x) return x.getEntity();
        if (p instanceof ClientboundSetEntityMotionPacket x) return x.id();
        if (p instanceof ClientboundSetEntityLinkPacket x) return x.getSourceId();
        if (p instanceof ClientboundAnimatePacket x) return x.getId();
        if (p instanceof ClientboundTeleportEntityPacket x) return x.id();
        if (p instanceof ClientboundUpdateMobEffectPacket x) return x.getEntityId();
        if (p instanceof ClientboundAddEntityPacket x) return x.getId();
        if (p instanceof ClientboundRotateHeadPacket x) {
            Minecraft mc = Minecraft.getInstance();
            Entity ent = mc.level == null ? null : x.getEntity(mc.level);
            return ent == null ? -1 : ent.getId();
        }
        if (p instanceof ClientboundEntityEventPacket x) {
            Minecraft mc = Minecraft.getInstance();
            Entity ent = mc.level == null ? null : x.getEntity(mc.level);
            return ent == null ? -1 : ent.getId();
        }
        if (p instanceof ClientboundMoveEntityPacket x) {
            Minecraft mc = Minecraft.getInstance();
            Entity ent = mc.level == null ? null : x.getEntity(mc.level);
            return ent == null ? -1 : ent.getId();
        }
        return -1;
    }
}
