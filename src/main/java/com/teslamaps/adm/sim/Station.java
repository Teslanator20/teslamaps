package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Station.kt

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public class Station {
    public enum Kind { TERMINAL, LEVER, DEVICE }

    public final Kind kind;
    public final int section;
    public final Vec3 at;
    public final String label;
    public final BlockPos lever;

    public Station(Kind kind, int section, Vec3 at, String label, BlockPos lever) {
        this.kind = kind;
        this.section = section;
        this.at = at;
        this.label = label;
        this.lever = lever;
    }

    public Station(Kind kind, int section, Vec3 at, String label) { this(kind, section, at, label, null); }

    public boolean done = false;
    public String doneBy = null;
    public int doneAt = -1;
    public Terminals.Term term = null;
    public ArmorStand top = null;
    public ArmorStand bottom = null;

    public String id() { return "S" + section + " " + label; }

    public Terminals.Type nextType() {
        Terminals.Type f = Fight.forcedTerminal();
        return f != null ? f : Terminals.randomType();
    }

    public void complete(String by) { complete(by, false); }

    public void complete(String by, boolean twice) {
        if (Fight.phase instanceof GoldorPhase g) g.complete(this, by, twice);
    }

    private ArmorStand stand(ServerLevel level, double y, String name, boolean marker) {
        SimStand s = new SimStand(level);
        s.snapTo(at.x, y, at.z, 0f, 0f);
        s.setInvisible(true);
        s.setInvulnerable(true);
        s.setSilent(true);
        s.setCustomName(name.isEmpty() ? null : Sim.legacy(name));
        s.setCustomNameVisible(!name.isEmpty());
        if (marker) { setMarker(s); setLeverFlags(s); }
        return Sim.spawn(s);
    }

    public void spawnStands() {
        ServerLevel level = Sim.level();
        switch (kind) {
            case TERMINAL -> { top = stand(level, at.y + TOP_DY, INACTIVE, false); bottom = stand(level, at.y + BOTTOM_DY, CLICK_HERE, false); }
            case DEVICE -> { top = stand(level, at.y, "§cInactive", false); bottom = stand(level, at.y - 0.375, "§cDevice", false); }
            case LEVER -> top = stand(level, at.y, "§cNot Activated", true);
        }
        refreshStands();
    }

    private static void name(ArmorStand s, String n) {
        if (s == null) return;
        Component c = n.isEmpty() ? null : Sim.legacy(n);
        Component cur = s.getCustomName();
        String curS = cur != null ? cur.getString() : null;
        String cS = c != null ? c.getString() : null;
        if (!java.util.Objects.equals(curS, cS) || (cur == null) != (c == null)) {
            s.setCustomName(c); s.setCustomNameVisible(c != null);
        }
    }

    public void refreshStands() {
        List<ServerPlayer> ps = Sim.players();
        boolean far = kind != Kind.LEVER && !ps.isEmpty() && ps.stream().noneMatch(p -> p.position().distanceTo(at) < 25.0);
        if (far) { name(top, ""); name(bottom, ""); return; }
        switch (kind) {
            case TERMINAL -> {
                if (done) {
                    if (top != null && Math.abs(top.getY() - (at.y + BOTTOM_DY)) > 1e-3) top.snapTo(at.x, at.y + BOTTOM_DY, at.z, 0f, 0f);
                    name(top, "§aTerminal Active"); name(bottom, "");
                } else {
                    if (top != null && Math.abs(top.getY() - (at.y + TOP_DY)) > 1e-3) top.snapTo(at.x, at.y + TOP_DY, at.z, 0f, 0f);
                    name(top, INACTIVE); name(bottom, CLICK_HERE);
                }
            }
            case DEVICE -> {
                if (done) { name(top, "§aDevice"); name(bottom, "§aActive"); } else { name(top, "§cInactive"); name(bottom, "§cDevice"); }
            }
            case LEVER -> name(top, done ? "§aActivated" : "§cNot Activated");
        }
    }

    public void clearStands() {
        if (top != null) top.discard();
        if (bottom != null) bottom.discard();
        top = null; bottom = null;
    }

    public boolean owns(Entity e) { return e == top || e == bottom; }

    public static final double TOP_DY = 0.02;
    public static final double BOTTOM_DY = -0.355;
    public static final String INACTIVE = "§cInactive Terminal";
    public static final String CLICK_HERE = "§e§lCLICK HERE";

    private static Method markerMethod;

    private static synchronized Method markerMethod() throws NoSuchMethodException {
        if (markerMethod == null) {
            Method m = ArmorStand.class.getDeclaredMethod("setMarker", boolean.class);
            m.setAccessible(true);
            markerMethod = m;
        }
        return markerMethod;
    }

    public static void setMarker(ArmorStand s) {
        try { markerMethod().invoke(s, true); } catch (Throwable ignored) {}
    }

    public static void setLeverFlags(ArmorStand s) {
        try { s.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) 18); } catch (Throwable ignored) {}
    }

    private static Station t(int section, int n, double x, double y, double z) { return new Station(Kind.TERMINAL, section, new Vec3(x, y, z), "T" + n); }
    private static Station l(int section, String label, int x, int y, int z) { return new Station(Kind.LEVER, section, new Vec3(x + 0.5, y + 0.688, z + 0.5), label, new BlockPos(x, y, z)); }
    private static Station d(int section, String label, double x, double y, double z) { return new Station(Kind.DEVICE, section, new Vec3(x, y, z), label); }

    public static List<Station> all() {
        return Arrays.asList(
            t(1, 1, 110.5, 112.0, 73.5), t(1, 2, 110.5, 118.0, 79.5), t(1, 3, 90.5, 111.0, 92.5), t(1, 4, 90.5, 121.0, 101.5),
            l(1, "east lever", 106, 124, 113), l(1, "west lever", 94, 124, 113),
            d(1, "SS", 110.5, 119.0, 91.5),
            t(2, 1, 68.5, 108.0, 122.5), t(2, 2, 59.5, 119.0, 123.5), t(2, 3, 47.5, 108.0, 122.5), t(2, 4, 39.5, 107.0, 142.5), t(2, 5, 40.5, 123.0, 123.5),
            l(2, "low lever", 27, 124, 127), l(2, "high lever", 23, 132, 138),
            d(2, "Lights", 60.5, 131.0, 142.5),
            t(3, 1, -1.5, 108.0, 112.5), t(3, 2, -1.5, 118.0, 93.5), t(3, 3, 18.5, 122.0, 93.5), t(3, 4, -1.5, 108.0, 77.5),
            l(3, "west lever", 2, 122, 55), l(3, "east lever", 14, 122, 55),
            d(3, "Arrows", -1.5, 119.0, 74.5),
            t(4, 1, 41.5, 108.0, 30.5), t(4, 2, 44.5, 120.0, 30.5), t(4, 3, 67.5, 108.0, 30.5), t(4, 4, 72.5, 114.0, 47.5),
            l(4, "low lever", 84, 121, 34), l(4, "high lever", 86, 128, 46),
            d(4, "Target", 63.5, 126.0, 34.5)
        );
    }

    public static int total(int section) { return section == 2 ? 8 : 7; }
}
