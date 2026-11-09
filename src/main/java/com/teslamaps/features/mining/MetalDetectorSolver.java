package com.teslamaps.features.mining;

// Ported from Skyblocker's MetalDetector; all-tools alert after SkyHanni's MetalDetectorAllToolsAlert
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mines of Divan: narrows the treasure down from the TREASURE distance in the action bar. */
public final class MetalDetectorSolver {

    private static final Pattern TREASURE = Pattern.compile("TREASURE: (\\d+\\.?\\d?)m");
    private static final Pattern KEEPER = Pattern.compile("Keeper of (\\w+)");
    private static final String DETECTOR = "DWARVEN_METAL_DETECTOR";
    private static final String[] TOOLS = {"DWARVEN_LAPIS_SWORD", "DWARVEN_DIAMOND_AXE", "DWARVEN_EMERALD_HAMMER", "DWARVEN_GOLD_HAMMER"};

    private static final Map<String, Vec3i> KEEPER_OFFSETS = Map.of(
            "Diamond", new Vec3i(33, 0, 3),
            "Lapis", new Vec3i(-33, 0, -3),
            "Emerald", new Vec3i(-3, 0, 33),
            "Gold", new Vec3i(3, 0, -33));

    private static final Vec3i[] CHESTS = {
            new Vec3i(-38, -22, 26), new Vec3i(38, -22, -26), new Vec3i(-40, -22, 18), new Vec3i(-41, -20, 22),
            new Vec3i(-5, -21, 16), new Vec3i(40, -22, -30), new Vec3i(-42, -20, -28), new Vec3i(-43, -22, -40),
            new Vec3i(42, -19, -41), new Vec3i(43, -21, -16), new Vec3i(-1, -22, -20), new Vec3i(6, -21, 28),
            new Vec3i(7, -21, 11), new Vec3i(7, -21, 22), new Vec3i(-12, -21, -44), new Vec3i(12, -22, 31),
            new Vec3i(12, -22, -22), new Vec3i(12, -21, 7), new Vec3i(12, -21, -43), new Vec3i(-14, -21, 43),
            new Vec3i(-14, -21, 22), new Vec3i(-17, -21, 20), new Vec3i(-20, -22, 0), new Vec3i(1, -21, 20),
            new Vec3i(19, -22, 29), new Vec3i(20, -22, 0), new Vec3i(20, -21, -26), new Vec3i(-23, -22, 40),
            new Vec3i(22, -21, -14), new Vec3i(-24, -22, 12), new Vec3i(23, -22, 26), new Vec3i(23, -22, -39),
            new Vec3i(24, -22, 27), new Vec3i(25, -22, 17), new Vec3i(29, -21, -44), new Vec3i(-31, -21, -12),
            new Vec3i(-31, -21, -40), new Vec3i(30, -21, -25), new Vec3i(-32, -21, -40), new Vec3i(-36, -20, 42),
            new Vec3i(-37, -21, -14), new Vec3i(-37, -21, -22)
    };

    private static Feature feature;
    private static BlockPos center;
    private static double lastDistance = -1;
    private static Vec3 lastPos;
    private static boolean fresh = true;
    private static List<BlockPos> candidates = new ArrayList<>();
    private static boolean inDivan, allTools, alertedTools;
    private static int ticks;
    private static Object lastLevel;

    private MetalDetectorSolver() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("MetalDetectorSolver").section("Mining").when(() -> cfg().metalDetectorSolver)
                .tick(MetalDetectorSolver::tick)
                .serverChat(MetalDetectorSolver::onChat)
                .world(MetalDetectorSolver::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Mines of Divan");
                    b.toggle("Metal Detector Solver", () -> c.metalDetectorSolver, v -> c.metalDetectorSolver = v)
                            .desc("Narrows the treasure down from the TREASURE distance in the action bar. Stand still for a second at a few spots; possible spots get a waypoint, the last one a yellow Treasure waypoint with a line.")
                            .tags("metal detector", "divan", "treasure", "crystal hollows", "automaton");
                    b.toggle("  All Tools Alert", () -> c.metalDetectorAllTools, v -> c.metalDetectorAllTools = v)
                            .desc("ALL TOOLS title once all four Divan tools are in your inventory; the solver stops then");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
            center = null;
            alertedTools = false;
        }
        if (++ticks % 10 != 0) return;
        inDivan = false;
        if (!SkyblockUtils.isInArea("Crystal Hollows")) return;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (ScoreboardUtils.cleanLine(line).contains("Mines of Divan")) { inDivan = true; break; }
        }
        if (inDivan && center == null) findCenter(mc);
    }

    private static void reset() {
        candidates = new ArrayList<>();
        fresh = true;
        lastDistance = -1;
        lastPos = null;
    }

    private static void findCenter(Minecraft mc) {
        for (Entity e : EntityCache.armorStands()) {
            if (!(e instanceof ArmorStand as) || !as.hasCustomName()) continue;
            Matcher m = KEEPER.matcher(StringUtil.stripTrim(as.getName().getString()));
            if (!m.matches()) continue;
            Vec3i off = KEEPER_OFFSETS.get(m.group(1));
            if (off == null) continue;
            center = as.blockPosition().offset(off);
            return;
        }
    }

    private static void onChat(String text) {
        if (inDivan && StringUtil.stripTrim(text).startsWith("You found")) reset();
    }

    /** From InGameHudMixin, main thread. */
    public static void onActionBar(Component message) {
        if (feature == null || !inDivan || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String text = StringUtil.strip(message.getString());
        if (!text.contains("TREASURE:")) return;
        checkTools(mc);
        if (allTools && cfg().metalDetectorAllTools) {
            if (!candidates.isEmpty()) reset();
            return;
        }
        Matcher m = TREASURE.matcher(text);
        if (!m.find()) return;
        double distance = Double.parseDouble(m.group(1));
        Vec3 pos = mc.player.position();
        int before = candidates.size();
        if (distance == lastDistance && pos.equals(lastPos)) update(distance, pos, mc);
        if (candidates.size() != before && !candidates.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal(candidates.size() == 1
                    ? "§b[TeslaMaps] §aTreasure found!"
                    : "§b[TeslaMaps] §7Possible treasure spots: §e" + candidates.size()));
        }
        lastDistance = distance;
        lastPos = pos;
    }

    private static void update(double distance, Vec3 pos, Minecraft mc) {
        if (fresh) {
            candidates = new ArrayList<>();
            fresh = false;
            if (center != null) {
                for (Vec3i off : CHESTS) {
                    BlockPos p = center.offset(off).above();
                    if (Math.abs(pos.distanceTo(Vec3.atLowerCornerOf(p)) - distance) < 0.25) candidates.add(p);
                }
            } else {
                int r = (int) distance;
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos p = BlockPos.containing(pos.x + x, pos.y, pos.z + z);
                        if (Math.abs(pos.distanceTo(Vec3.atLowerCornerOf(p)) - distance) < 0.25) candidates.add(p);
                    }
                }
            }
        } else {
            candidates.removeIf(p -> Math.abs(pos.distanceTo(Vec3.atLowerCornerOf(p)) - distance) >= 0.25);
        }
        if (candidates.isEmpty()) {
            fresh = true;
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cMetal detector: no spot fits, starting over."));
        }
    }

    private static void checkTools(Minecraft mc) {
        if (!DETECTOR.equals(ItemUtil.skyblockId(mc.player.getMainHandItem()))) return;
        int found = 0;
        for (String tool : TOOLS) {
            for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) {
                if (tool.equals(ItemUtil.skyblockId(st))) { found++; break; }
            }
        }
        allTools = found == TOOLS.length;
        if (!allTools) {
            alertedTools = false;
            return;
        }
        if (!cfg().metalDetectorAllTools) return;
        mc.gui.hud.setTitle(Component.literal("§cALL TOOLS"));
        mc.gui.hud.setTimes(0, 20, 5);
        if (!alertedTools) {
            alertedTools = true;
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f);
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<BlockPos> list = candidates;
        if (!inDivan || list.isEmpty() || list.size() > 8) return;
        if (list.size() == 1) {
            BlockPos p = list.get(0).below();
            WaypointSet.draw(pose, cam, p, 0xFFFF55, "§eTreasure", true, true, true);
            ESPRenderer.drawTracerFromCamera(pose, Vec3.atCenterOf(p), 0xFFC0C0C0, cam, true, 3f);
            return;
        }
        for (BlockPos p : list) WaypointSet.draw(pose, cam, p, 0xFFFFFF, "§fPossible", true, true, true);
    }
}
