package com.teslamaps.features.kuudra;

// Ported from Odin's nether/PearlWaypoints.kt (lineups + calculatePearl), timer from legitcatmod's PearlWaypoints,
// pearl interact from IQ's PearlCancelFeatureMixin
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KuudraPearls {

    enum Drop {
        Triangle(-94, 78, -106), X(-106, 78, -112), Equals(-98, 78, -99), Slash(-106, 78, -99),
        Shop(-98, 78, -112), xCannon(-110, 78, -106), Square(0, 0, 0), None(0, 0, 0);

        final BlockPos pos;
        Drop(int x, int y, int z) { pos = new BlockPos(x, y, z); }
    }

    record Lineup(Drop supply, BlockPos[] start, BlockPos[] lineups, int color) {}
    record Aim(Vec3 pos, String name, int ticks) {}

    private static final Lineup[] LINEUPS = {
            new Lineup(Drop.Shop, new BlockPos[]{new BlockPos(-71, 79, -135), new BlockPos(-86, 78, -129)},
                    new BlockPos[]{new BlockPos(-146, 107, -60), new BlockPos(-147, 111, -69)}, 0xFF5555),
            new Lineup(Drop.Triangle, new BlockPos[]{new BlockPos(-68, 77, -123)},
                    new BlockPos[]{new BlockPos(-149, 104, -70)}, 0xFF55FF),
            new Lineup(Drop.X, new BlockPos[]{new BlockPos(-135, 77, -139)},
                    new BlockPos[]{new BlockPos(-59, 115, -71)}, 0xFFFF55),
            new Lineup(Drop.xCannon, new BlockPos[]{new BlockPos(-131, 79, -114)},
                    new BlockPos[]{new BlockPos(-59, 106, -59), new BlockPos(-51, 108, -67), new BlockPos(-39, 93, -76)}, 0xFFFFFF),
            new Lineup(Drop.Square, new BlockPos[]{new BlockPos(-141, 78, -91)},
                    new BlockPos[]{new BlockPos(-59, 106, -59), new BlockPos(-58, 127, -148), new BlockPos(-146, 107, -60),
                            new BlockPos(-149, 104, -70), new BlockPos(-168, 124, -118), new BlockPos(-65, 109, -162)}, 0x55FFFF),
            new Lineup(Drop.Equals, new BlockPos[]{new BlockPos(-66, 76, -88)},
                    new BlockPos[]{new BlockPos(-168, 124, -118)}, 0x55FF55),
            new Lineup(Drop.Slash, new BlockPos[]{new BlockPos(-115, 77, -69)},
                    new BlockPos[]{new BlockPos(-65, 109, -162)}, 0x5555FF),
    };

    private static final Pattern PICKUP = Pattern.compile("\\[\\|+]\\s*(\\d+)%");
    private static final double GRAV = 0.05, E_VEL = 1.67;
    private static final long THROW_AT_MS = 4250;

    private static Drop missing = Drop.None;
    private static List<Lineup> shown = List.of();
    private static List<Aim> aims = List.of();
    private static String[] aimLabels = new String[0];
    private static volatile long pickupStart = 0, lastPickupTitle = 0;

    private KuudraPearls() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean active() {
        return Kuudra.inKuudra() && Kuudra.phase() == Kuudra.Phase.SUPPLIES;
    }

    public static void register() {
        Kuudra.LISTENERS.add((prev, now) -> {
            if (now != Kuudra.Phase.SUPPLIES) {
                missing = Drop.None;
                pickupStart = 0;
            }
        });
        Feature.of("KuudraPearls").section("Kuudra").when(() -> cfg().kuuPearlSpots && active())
                .chat(KuudraPearls::onChat)
                .tick(KuudraPearls::tick)
                .world(KuudraPearls::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Pearls");
                    b.toggle("Pearl Waypoints", () -> c.kuuPearlSpots, v -> c.kuuPearlSpots = v)
                            .desc("Supply phase: pearl start spots and lineup blocks per drop-off, colored per supply");
                    b.toggle("  Preset Lineups", () -> c.kuuPearlPresets, v -> c.kuuPearlPresets = v);
                    b.toggle("  Only Closest", () -> c.kuuPearlClosest, v -> c.kuuPearlClosest = v)
                            .desc("Only the lineup set nearest to you");
                    b.toggle("  Dynamic Waypoints", () -> c.kuuPearlDynamic, v -> c.kuuPearlDynamic = v)
                            .desc("Aim points for the high and the flat throw from where you stand");
                    b.color("    Dynamic Color", () -> c.kuuPearlDynamicColor, v -> c.kuuPearlDynamicColor = v);
                    b.toggle("    Throw Timer", () -> c.kuuPearlTimer, v -> c.kuuPearlTimer = v)
                            .desc("Time left until the throw lands as the pickup finishes, from the pickup bar title");
                    b.slider("    Timer Offset (ms)", -500f, 500f, () -> c.kuuPearlTimerOffset, v -> c.kuuPearlTimerOffset = v, 0);
                    b.toggle("Pearl Block Interact", () -> c.kuuPearlInteract, v -> c.kuuPearlInteract = v).cheat()
                            .desc("Right-clicking a block with an Ender Pearl throws the pearl instead of using the block (supply phase)");
                    b.toggle("  Everywhere", () -> c.kuuPearlInteractEverywhere, v -> c.kuuPearlInteractEverywhere = v)
                            .desc("Not only in the Kuudra supply phase");
                });
    }

    /** ClientPlayerInteractionManagerMixin.useItemOn HEAD. True = skip the block, throw the pearl. */
    public static boolean passBlock(LocalPlayer player, InteractionHand hand) {
        TeslaMapsConfig c = cfg();
        if (!c.kuuPearlInteract || hand != InteractionHand.MAIN_HAND) return false;
        if (!player.getItemInHand(hand).is(Items.ENDER_PEARL)) return false;
        if (!c.section("Kuudra") || LegitMode.blocksCheats()) return false;
        return c.kuuPearlInteractEverywhere || active();
    }

    /** Title/subtitle packets, any thread: only stamps times. */
    public static void onTitle(String text) {
        if (text.indexOf('[') < 0 || !text.contains("|")) return;
        TeslaMapsConfig c = cfg();
        if (!c.kuuPearlSpots || !c.kuuPearlDynamic || !c.kuuPearlTimer) return;
        Matcher m = PICKUP.matcher(StringUtil.strip(text));
        if (!m.find()) return;
        long now = System.currentTimeMillis();
        int pct = Integer.parseInt(m.group(1));
        if (pct >= 100) pickupStart = 0;
        else if (pct == 0 && (pickupStart == 0 || now - lastPickupTitle > 750)) pickupStart = now;
        lastPickupTitle = now;
    }

    private static void onChat(String raw) {
        Matcher m = KuudraAlerts.NO_SPOT.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        String name = KuudraNoPre.canonical(m.group(2));
        if (name != null) missing = Drop.valueOf(name);
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        BlockPos me = p.blockPosition();
        List<Lineup> sorted = new ArrayList<>(List.of(LINEUPS));
        sorted.sort((a, b) -> Double.compare(minDist(a, me), minDist(b, me)));
        shown = c.kuuPearlClosest ? sorted.subList(0, 1) : sorted;

        if (!c.kuuPearlDynamic) {
            aims = List.of();
            return;
        }
        List<Aim> out = new ArrayList<>();
        for (Lineup l : shown) {
            Drop dest = l.supply == Drop.Square ? missing : l.supply;
            if (dest == Drop.None || dest == Drop.Square) continue;
            calculate(p, dest, out);
        }
        long now = System.currentTimeMillis();
        if (now - lastPickupTitle > 750) pickupStart = 0;
        long start = pickupStart;
        String[] labels = new String[out.size()];
        for (int i = 0; i < out.size(); i++) {
            Aim a = out.get(i);
            if (!c.kuuPearlTimer || start == 0) { labels[i] = a.name; continue; }
            long left = THROW_AT_MS + (long) c.kuuPearlTimerOffset - a.ticks * 50L - (now - start);
            labels[i] = a.name + ": " + (left <= 0 ? "§2Ready" : "§e" + left + "ms");
        }
        aims = out;
        aimLabels = labels;
    }

    private static double minDist(Lineup l, BlockPos me) {
        double d = Double.MAX_VALUE;
        for (BlockPos s : l.start) d = Math.min(d, s.distSqr(me));
        return d;
    }

    private static void calculate(LocalPlayer player, Drop dest, List<Aim> out) {
        BlockPos t = dest.pos;
        double posX = player.getX(), posY = player.getY(), posZ = player.getZ();
        double offX = t.getX() - posX, offY = t.getY() - (posY + 1.62), offZ = t.getZ() - posZ;
        double offHor = Math.hypot(offX, offZ);
        double v2 = E_VEL * E_VEL, v4 = v2 * v2;
        double g = GRAV * (1.0 + offHor * 0.0012);
        double disc = v4 - g * (g * offHor * offHor + 2 * offY * v2);
        if (disc < 0 || offHor < 0.01) return;
        double root = Math.sqrt(disc);
        double a1 = Math.toDegrees(Math.atan((v2 + root) / (g * offHor)));
        double a2 = Math.toDegrees(Math.atan((v2 - root) / (g * offHor)));
        double up = Math.max(a1, a2), flat = Math.min(a1, a2);
        double radY = -Math.atan2(offX, offZ);
        if (up > 0) {
            double radP = Math.toRadians(-up);
            double vY = E_VEL * Math.sin(Math.toRadians(up));
            double factor = Math.pow(1.0012, Math.max(offHor / 15, 1.0)) * 0.8;
            double fT = (vY + Math.sqrt(vY * vY + 2 * GRAV * (posY + 1.62 - t.getY()))) / GRAV;
            int timing = (int) Math.floor((fT / Math.pow(0.992, fT)) * factor) - 2;
            double cp = Math.cos(radP);
            Vec3 aim = new Vec3(posX - cp * Math.sin(radY) * 10, posY - Math.sin(radP) * 10, posZ + cp * Math.cos(radY) * 10);
            out.add(new Aim(aim, dest.name() + " High", timing));
        }
        if (flat > 0) {
            double radP = Math.toRadians(-flat);
            double vX = E_VEL * Math.cos(Math.toRadians(flat));
            double ticks = Math.log(1 - (offHor * (1 - 0.978) / vX)) / Math.log(0.978);
            int timing = Double.isNaN(ticks) ? (int) (offHor / vX) : (int) Math.ceil(ticks);
            double cp = Math.cos(radP);
            Vec3 aim = new Vec3(posX - cp * Math.sin(radY) * 10, posY + 1.2 - Math.sin(radP) * 10, posZ + cp * Math.cos(radY) * 10);
            out.add(new Aim(aim, dest.name() + " Flat", timing));
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        List<Lineup> list = shown;
        if (c.kuuPearlPresets) {
            for (Lineup l : list) {
                for (BlockPos s : l.start) ESPRenderer.drawBoxOutline(pose, new AABB(s), 0xFF000000 | l.color, 2f, cam, true);
                for (BlockPos b : l.lineups) {
                    if (l.supply == Drop.Square && missing != Drop.None && missing != Drop.Square && !b.equals(squareLineup(missing))) continue;
                    ESPRenderer.drawFilledBox(pose, new AABB(b), 0x80000000 | l.color, cam, true);
                }
            }
        }
        List<Aim> a = aims;
        String[] labels = aimLabels;
        if (a.isEmpty()) return;
        int dyn = TeslaMapsConfig.parseColor(c.kuuPearlDynamicColor) | 0xFF000000;
        for (int i = 0; i < a.size(); i++) {
            Vec3 p = a.get(i).pos;
            ESPRenderer.drawFilledBox(pose, new AABB(p.x - 0.06, p.y - 0.06, p.z - 0.06, p.x + 0.06, p.y + 0.06, p.z + 0.06), dyn, cam, true);
            if (i < labels.length) ESPRenderer.drawText(pose, labels[i], p.add(0, -0.2, 0), 0.5f, cam);
        }
        for (Lineup l : list) {
            if (l.supply == Drop.Square) continue;
            ESPRenderer.drawBoxOutline(pose, new AABB(l.supply.pos.above()), dyn, 2f, cam, true);
        }
    }

    private static BlockPos squareLineup(Drop d) {
        return switch (d) {
            case xCannon -> LINEUPS[4].lineups[0];
            case X -> LINEUPS[4].lineups[1];
            case Shop -> LINEUPS[4].lineups[2];
            case Triangle -> LINEUPS[4].lineups[3];
            case Equals -> LINEUPS[4].lineups[4];
            case Slash -> LINEUPS[4].lineups[5];
            default -> null;
        };
    }
}
