package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Spots.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class Spots {
    private Spots() {}

    public static final class Spot {
        public final String name;
        public final double x, y, z;
        public final float yaw, pitch;

        public Spot(String name, double x, double y, double z, float yaw, float pitch) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }

        public Spot(String name, double x, double y, double z, float yaw) { this(name, x, y, z, yaw, 0f); }
    }

    public static final Spot LOBBY = new Spot("Red pad (P3 drop)", 100.5, 169.0, 40.5, 0f);

    public static final Spot P1 = new Spot("P1 start", 73.5, 221.0, 14.5, 0f);
    public static final Spot P2 = new Spot("P2 (Storm floor)", 73.5, 165.0, 40.5, 0f);
    public static final Spot P3_DROP = LOBBY;
    public static final Spot S1 = new Spot("S1 start", 100.5, 116.0625, 40.5, 0f);
    public static final Spot SS = new Spot("Simon Says", 108.5, 120.0, 94.0, -90f);
    public static final Spot S2 = new Spot("S2 start", 100.5, 115.0625, 128.5, 90f);
    public static final Spot S3 = new Spot("S3 start", 12.5, 115.0625, 131.5, 135f);
    public static final Spot S4 = new Spot("S4 start", 8.5, 115.0625, 44.5, -90f);
    public static final Spot CORE = new Spot("Core (in front)", 54.5, 115.0, 51.5, 0f);
    public static final Spot P4 = new Spot("P4 (Necron)", 54.5, 64.0, 108.5, 180f);

    public static final Spot PURPLE_PAD = new Spot("Purple pad", 114.5, 170.0, 94.5, 90f);
    public static final Spot YELLOW_PAD = new Spot("Yellow pad", 32.5, 170.0, 94.5, -90f);
    public static final Spot GREEN_PAD = new Spot("Green pad", 32.5, 170.0, 12.5, -90f);
    public static final Spot RED_PAD = new Spot("Red pad", 114.5, 170.0, 12.5, 90f);
    public static final Spot LIGHTS = new Spot("Lights device", 60.5, 132.0, 140.0, 0f);

    public static Spot p3Start(int from) {
        switch (from) {
            case 2: return S2;
            case 3: return S3;
            case 4: return S4;
            case 5: return CORE;
            default: break;
        }
        ServerPlayer a = Sim.acting();
        if (a != null && !Sim.isHost(a)) {
            Spot s = Players.spotOf(a, "spawn");
            if (s != null) return s;
        }
        Spot custom = P3Plan.customSpot("spawn");
        if (custom != null) return custom;
        String first;
        Pair<Integer, Double> s3 = P3Plan.plan().times.get("S3 Arrows");
        if (P3Plan.isMine("S4 Target")) first = "S4 Target";
        else if (P3Plan.isMine("S3 Arrows") && s3 != null && s3.first == 1) first = "S3 Arrows";
        else {
            first = null;
            for (String m : P3Plan.mine()) if (m.startsWith("S1 T")) { first = m; break; }
            if (first == null) for (String j : P3Plan.jobsIn(1)) if (P3Plan.isMine(j)) { first = j; break; }
        }
        if (first != null) {
            Vec3 it = Party.STANDS.get(first);
            if (it != null) {
                Pair<Float, Float> l = startLook(first);
                return new Spot(first, it.x, it.y, it.z, l.first, l.second);
            }
        }
        return SS;
    }

    public static Pair<Float, Float> startLook(String job) {
        return switch (job) {
            case "S1 east lever", "S1 west lever" -> Pair.of(10f, -19f);
            case "S4 Target" -> Pair.of(283f, 19f);
            case "S3 Arrows" -> Pair.of(89.7f, 3.2f);
            case "S1 T4" -> Pair.of(40f, 1f);
            case "S1 T2" -> Pair.of(179f, 48f);
            case "S1 SS" -> Pair.of(-131f, 1.4f);
            default -> Pair.of(180f, 0f);
        };
    }

    public static final List<Pair<String, List<Spot>>> teleportGroups = List.of(
            Pair.of("P3", List.of(P3_DROP, S1, SS, S2, LIGHTS, S3, S4, CORE)),
            Pair.of("Pads", List.of(PURPLE_PAD, YELLOW_PAD, GREEN_PAD, RED_PAD)),
            Pair.of("Phases", List.of(P1, P2, P4)));

    public static final List<Spot> teleports;

    static {
        List<Spot> t = new ArrayList<>();
        for (Pair<String, List<Spot>> g : teleportGroups) t.addAll(g.second);
        teleports = List.copyOf(t);
    }
}
