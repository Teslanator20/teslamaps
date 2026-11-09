// Ported from ADM's p3sim/clear/mobs/FairyRoom.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.Station;
import com.teslamaps.adm.sim.TransientStand;
import com.teslamaps.adm.sim.clear.secrets.FloorDrops;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.util.KRandom;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;

public final class FairyRoom {
    private FairyRoom() {}

    public static final List<String> NAMES = List.of("Akia", "Aliya", "Desini", "Genevieve", "Ilene", "Lelani", "Mari", "Nymira", "Steve", "Synestra", "Trisha", "Tyene", "Ussaea", "Yve", "Zana");
    public static final String TEXTURE = "2eedcffc6a11a3834a28849cc316af7a2752a376d536cf84039cf79108b167ae";
    public static final double TAG_ABOVE = 0.94;
    public static final double SPEED = 0.14;
    public static final double MIN_R = 2.5;
    public static final double MAX_R = 7.0;
    public static final double MIN_Y = 69.3;
    public static final double MAX_Y = 74.0;

    public static String tag(String name) { return "§8[§r§7Lv0§r§8] §r§d" + name + " §r§a4§r§c❤"; }

    public static List<String> pickNames(KRandom rnd) {
        List<String> copy = new ArrayList<>(NAMES);
        KRandom.shuffle(copy, rnd);
        return new ArrayList<>(copy.subList(0, Math.min(4, copy.size())));
    }

    public static double[] nextTarget(double cx, double cz, KRandom rnd) {
        double a = rnd.nextDouble() * Math.PI * 2;
        double r = MIN_R + (MAX_R - MIN_R) * rnd.nextDouble();
        return new double[]{cx + Math.cos(a) * r, MIN_Y + (MAX_Y - MIN_Y) * rnd.nextDouble(), cz + Math.sin(a) * r};
    }

    private static final class Fairy {
        final String name;
        double x, y, z;
        double[] target;
        ArmorStand body = null;
        ArmorStand tag = null;

        Fairy(String name, double x, double y, double z, double[] target) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.target = target;
        }
    }

    private static final ArrayList<Fairy> fairies = new ArrayList<>();
    private static boolean planned = false;
    private static double cx = 0.0;
    private static double cz = 0.0;
    private static KRandom rnd = new KRandom(0);

    public static void tick(Run run) {
        if (!planned) {
            planned = true;
            PlacedRoom room = null;
            for (PlacedRoom r : run.plan.rooms) if (r.type == RoomType.FAIRY) { room = r; break; }
            if (room == null) return;
            if (room.tiles.isEmpty()) return;
            Tile tile = room.tiles.get(0);
            cx = DungeonFrame.tileCentre(tile.x) + 0.5; cz = DungeonFrame.tileCentre(tile.z) + 0.5;
            rnd = run.subRandom("fairies");
            for (String n : pickNames(rnd)) { double[] at = nextTarget(cx, cz, rnd); fairies.add(new Fairy(n, at[0], at[1], at[2], nextTarget(cx, cz, rnd))); }
        }
        if (fairies.isEmpty()) return;
        if (!RunBlocks.isLoaded((int) Math.floor(cx), (int) Math.floor(cz))) return;
        for (Fairy f : fairies) {
            if (f.body == null || f.body.isRemoved()) build(f);
            double dx = f.target[0] - f.x, dy = f.target[1] - f.y, dz = f.target[2] - f.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d <= SPEED) f.target = nextTarget(cx, cz, rnd);
            else { f.x += dx / d * SPEED; f.y += dy / d * SPEED; f.z += dz / d * SPEED; }
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            if (f.body != null) f.body.snapTo(f.x, f.y, f.z, yaw, 0f);
            if (f.tag != null) f.tag.snapTo(f.x, f.y + TAG_ABOVE, f.z, yaw, 0f);
        }
    }

    private static void build(Fairy f) {
        if (f.body != null) f.body.discard();
        if (f.tag != null) f.tag.discard();
        Ec.safely("ads fairy", () -> {
            TransientStand b = new TransientStand(Sim.level());
            b.snapTo(f.x, f.y, f.z, 0f, 0f);
            b.setInvisible(true); b.setPermanentlyInvulnerable(true); b.setSilent(true); b.setNoGravity(true);
            try { b.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) 3); } catch (Throwable ignored) {}
            b.setItemSlot(EquipmentSlot.HEAD, FloorDrops.head(TEXTURE, null));
            TransientStand t = new TransientStand(Sim.level());
            t.snapTo(f.x, f.y + TAG_ABOVE, f.z, 0f, 0f);
            t.setInvisible(true); t.setPermanentlyInvulnerable(true); t.setSilent(true); t.setNoGravity(true);
            Station.setLeverFlags(t);
            t.setCustomName(Sim.legacy(tag(f.name))); t.setCustomNameVisible(true);
            f.body = Sim.spawn(b); f.tag = Sim.spawn(t);
        });
    }

    public static void clear() {
        for (Fairy f : fairies) { if (f.body != null) f.body.discard(); if (f.tag != null) f.tag.discard(); }
        fairies.clear(); planned = false;
    }
}
