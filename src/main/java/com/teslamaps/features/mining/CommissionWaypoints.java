package com.teslamaps.features.mining;

// Locations ported from Skyblocker's MiningLocationLabel / CommissionLabels
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Labels with distance where the active commissions are done, plus the Emissaries once one is finished. */
public final class CommissionWaypoints {

    private record Spot(String key, String label, int color, BlockPos[] pos) {}

    private static final int MITHRIL = 0x45BDE0, TITANIUM = 0xD8D6D8;

    private static final Spot[] DWARVEN = {
            new Spot("Lava Springs", "Lava Springs", MITHRIL, new BlockPos[]{new BlockPos(60, 197, -15)}),
            new Spot("Cliffside Veins", "Cliffside Veins", MITHRIL, new BlockPos[]{new BlockPos(40, 128, 40)}),
            new Spot("Rampart's Quarry", "Rampart's Quarry", MITHRIL, new BlockPos[]{new BlockPos(-100, 150, -20)}),
            new Spot("Upper Mines", "Upper Mines", MITHRIL, new BlockPos[]{new BlockPos(-130, 174, -50)}),
            new Spot("Royal Mines", "Royal Mines", MITHRIL, new BlockPos[]{new BlockPos(130, 154, 30)}),
            new Spot("Glacite Walker", "§3Glacite Walker", 0x55FFFF, new BlockPos[]{new BlockPos(0, 128, 150)}),
    };

    private static final BlockPos[] EMISSARIES = {
            new BlockPos(58, 198, -8), new BlockPos(42, 134, 22), new BlockPos(-72, 153, -10),
            new BlockPos(-132, 174, -50), new BlockPos(171, 150, 31), new BlockPos(-37, 200, -131),
            new BlockPos(89, 198, -92),
    };

    private static final Spot[] GLACITE = {
            new Spot("Aquamarine", "§9Aquamarine", 0x334CB1, new BlockPos[]{new BlockPos(20, 136, 370), new BlockPos(-14, 132, 386),
                    new BlockPos(6, 137, 411), new BlockPos(50, 117, 302)}),
            new Spot("Onyx", "§8Onyx", 0x7F7F7F, new BlockPos[]{new BlockPos(4, 127, 307), new BlockPos(-3, 139, 434),
                    new BlockPos(77, 118, 411), new BlockPos(-68, 130, 404)}),
            new Spot("Peridot", "§2Peridot", 0x667F33, new BlockPos[]{new BlockPos(66, 144, 284), new BlockPos(94, 154, 284),
                    new BlockPos(-62, 147, 303), new BlockPos(-77, 119, 283), new BlockPos(87, 122, 394),
                    new BlockPos(-73, 122, 456)}),
            new Spot("Citrine", "§6Citrine", 0x9C6B3A, new BlockPos[]{new BlockPos(-86, 143, 261), new BlockPos(74, 150, 327),
                    new BlockPos(63, 137, 343), new BlockPos(38, 119, 386), new BlockPos(55, 150, 400),
                    new BlockPos(-45, 127, 415), new BlockPos(-60, 144, 424), new BlockPos(-54, 132, 410)}),
    };

    private static final BlockPos BASE_CAMP = new BlockPos(-7, 126, 229);

    private static final WaypointSet SET = new WaypointSet().beam(false).box(false);
    private static int lastVersion = -1, ticks;
    private static int lastState = -1;
    private static boolean show;

    private CommissionWaypoints() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CommissionWaypoints").section("Mining").when(() -> cfg().commissionWaypoints)
                .tick(CommissionWaypoints::tick)
                .world(CommissionWaypoints::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Commission Waypoints", () -> c.commissionWaypoints, v -> c.commissionWaypoints = v)
                            .desc("Labels with distance where your commissions are done: Dwarven areas, Glacite gemstone spots, and the Emissaries once one is finished.")
                            .tags("commission", "emissary", "lava springs", "royal mines", "glacite", "waypoint");
                    b.toggle("  Dwarven Mines", () -> c.commissionWaypointsDwarven, v -> c.commissionWaypointsDwarven = v);
                    b.toggle("  Glacite Tunnels", () -> c.commissionWaypointsGlacite, v -> c.commissionWaypointsGlacite = v);
                    b.toggle("    All Gemstones Always", () -> c.commissionWaypointsGlaciteAlways, v -> c.commissionWaypointsGlaciteAlways = v)
                            .desc("Marks every Aquamarine/Onyx/Peridot/Citrine spot, not only the ones for your open commissions");
                    b.toggle("    Nearest Spot Only", () -> c.commissionWaypointsGlaciteNearest, v -> c.commissionWaypointsGlaciteNearest = v);
                    b.toggle("  Base Camp", () -> c.commissionWaypointsBaseCamp, v -> c.commissionWaypointsBaseCamp = v);
                    b.toggle("  Emissaries When Done", () -> c.commissionWaypointsEmissary, v -> c.commissionWaypointsEmissary = v);
                    b.toggle("    Hide With Royal Pigeon", () -> c.commissionWaypointsPigeon, v -> c.commissionWaypointsPigeon = v);
                });
    }

    private static void tick() {
        show = SkyblockUtils.isInArea("Dwarven Mines");
        if (!show) {
            if (!SET.all().isEmpty()) SET.clear();
            lastVersion = -1;
            return;
        }
        if (++ticks % 20 == 0) {
            Minecraft mc = Minecraft.getInstance();
            boolean glacite = mc.player != null && mc.player.getZ() > 200;
            TeslaMapsConfig c = cfg();
            int state = (glacite ? 1 : 0) | (c.commissionWaypointsDwarven ? 2 : 0) | (c.commissionWaypointsGlacite ? 4 : 0)
                    | (c.commissionWaypointsBaseCamp ? 8 : 0) | (c.commissionWaypointsEmissary ? 16 : 0)
                    | (c.commissionWaypointsPigeon && hasPigeon(mc) ? 32 : 0);
            if (state != lastState || Commissions.version() != lastVersion || glacite && c.commissionWaypointsGlaciteNearest) {
                lastState = state;
                lastVersion = Commissions.version();
                rebuild(glacite, c, (state & 32) != 0);
            }
        }
        SET.tick();
    }

    private static boolean hasPigeon(Minecraft mc) {
        if (mc.player == null) return false;
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) {
            if (!st.isEmpty() && "ROYAL_PIGEON".equals(ItemUtil.skyblockId(st))) return true;
        }
        return false;
    }

    private static void rebuild(boolean glacite, TeslaMapsConfig c, boolean pigeon) {
        SET.clear();
        var list = Commissions.active();
        if (glacite) {
            if (c.commissionWaypointsGlacite) {
                for (Spot s : GLACITE) {
                    boolean want = c.commissionWaypointsGlaciteAlways;
                    for (var com : list) if (!com.done() && com.name().contains(s.key())) want = true;
                    if (!want) continue;
                    if (c.commissionWaypointsGlaciteNearest) SET.add(nearest(s.pos()), s.label(), s.color(), 0);
                    else for (BlockPos p : s.pos()) SET.add(p, s.label(), s.color(), 0);
                }
            }
            if (c.commissionWaypointsBaseCamp) SET.add(BASE_CAMP, "§cBase Camp", 0xFF5555, 0);
            return;
        }
        if (c.commissionWaypointsDwarven) {
            for (var com : list) {
                if (com.done()) continue;
                for (Spot s : DWARVEN) {
                    if (!com.name().contains(s.key())) continue;
                    boolean ti = com.name().contains("Titanium");
                    SET.add(s.pos()[0], (ti ? "§f" : s.key().equals("Glacite Walker") ? "" : "§b") + s.label(), ti ? TITANIUM : s.color(), 0);
                }
            }
        }
        if (c.commissionWaypointsEmissary && !pigeon && Commissions.anyDone()) {
            for (BlockPos p : EMISSARIES) SET.add(p, "§fEmissary", 0xFFFFFF, 0);
        }
    }

    private static BlockPos nearest(BlockPos[] ps) {
        var player = Minecraft.getInstance().player;
        if (player == null) return ps[0];
        BlockPos best = ps[0];
        double bd = Double.MAX_VALUE;
        for (BlockPos p : ps) {
            double d = player.distanceToSqr(Vec3.atCenterOf(p));
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (show) SET.render(pose, cam);
    }
}
