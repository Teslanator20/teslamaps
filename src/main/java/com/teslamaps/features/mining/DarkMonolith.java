package com.teslamaps.features.mining;

// Ported from SkyHanni's features/mining/DarkMonolithFeatures.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DarkMonolith {

    private static final Pattern DROP = Pattern.compile(
            "MONOLITH! You.*and were rewarded ?(?:([\\d,]+) Coins ?)?(?:!|and )?(?:([\\d,]+) ᠅ Mithril Powder!)?");
    private static final String POWDER = "SKYBLOCK_POWDER_MITHRIL";
    private static final String LOOTED = "MONOLITHS_LOOTED";
    private static final int RANGE = 40;

    private static Tracker tracker;
    private static HudEditElement hud;
    private static final List<BlockPos> known = new ArrayList<>();
    private static BlockPos found, lastFound;
    private static boolean inMines;
    private static int scanTicks, checkTicks;

    private DarkMonolith() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("dark_monolith", "§5§lDark Monolith")
                .name(POWDER, "§2Mithril Powder")
                .name(LOOTED, "§dMonoliths Looted");
        hud = HudEditElement.of("darkMonolithTracker", "Dark Monolith Tracker").section("Mining")
                .ctx(HudEditElement.HudCtx.MINING)
                .enabled(() -> cfg().darkMonolith && cfg().darkMonolithTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("DarkMonolith").section("Mining").when(() -> cfg().darkMonolith)
                .tick(DarkMonolith::tick)
                .serverChat(DarkMonolith::onChat)
                .world(DarkMonolith::render)
                .hud("darkmonolith", DarkMonolith::renderHud)
                .config("Mining", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Dark Monolith");
                    b.toggle("Dark Monolith Finder", () -> c.darkMonolith, v -> c.darkMonolith = v)
                            .desc("Finds the Dark Monolith dragon egg within 40 blocks in the Dwarven Mines once a side of it is visible.")
                            .tags("dragon egg", "monolith", "dwarven");
                    b.toggle("  Highlight Box", () -> c.darkMonolithBox, v -> c.darkMonolithBox = v);
                    b.toggle("  Beacon Beam", () -> c.darkMonolithBeacon, v -> c.darkMonolithBeacon = v);
                    b.toggle("  Title On Found", () -> c.darkMonolithTitle, v -> c.darkMonolithTitle = v);
                    b.toggle("  Loot Tracker", () -> c.darkMonolithTracker, v -> c.darkMonolithTracker = v)
                            .desc("Counts monoliths, coins and Mithril Powder. Move it in /tmap gui");
                    b.trackerButtons(tracker);
                });
    }

    private static void tick() {
        tracker.tick();
        Minecraft mc = Minecraft.getInstance();
        boolean now = SkyblockUtils.isInArea("Dwarven Mines");
        if (now != inMines) {
            inMines = now;
            known.clear();
            found = lastFound = null;
        }
        if (!inMines) return;
        if (++scanTicks >= 20) {
            scanTicks = 0;
            scan(mc);
        }
        if (++checkTicks >= 10) {
            checkTicks = 0;
            found = null;
            for (BlockPos p : known) {
                if (canSee(mc, p)) { found = p; break; }
            }
            if (found != null && !found.equals(lastFound)) {
                lastFound = found;
                if (cfg().darkMonolithTitle) {
                    mc.gui.hud.setTitle(Component.literal("§5§lDark Monolith found!"));
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §5Dark Monolith found!"));
                }
            }
        }
    }

    private static void scan(Minecraft mc) {
        known.clear();
        if (mc.level == null || mc.player == null) return;
        BlockPos c = mc.player.blockPosition();
        int minY = Math.max(mc.level.getMinY(), c.getY() - RANGE), maxY = Math.min(mc.level.getMaxY(), c.getY() + RANGE);
        for (int cx = (c.getX() - RANGE) >> 4; cx <= (c.getX() + RANGE) >> 4; cx++) {
            for (int cz = (c.getZ() - RANGE) >> 4; cz <= (c.getZ() + RANGE) >> 4; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length; si++) {
                    LevelChunkSection s = sections[si];
                    if (s == null || s.hasOnlyAir()) continue;
                    int baseY = mc.level.getSectionYFromSectionIndex(si) << 4;
                    if (baseY + 15 < minY || baseY > maxY) continue;
                    if (!s.maybeHas(st -> st.is(Blocks.DRAGON_EGG))) continue;
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        if (!s.getBlockState(x, y, z).is(Blocks.DRAGON_EGG)) continue;
                        BlockPos p = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                        if (p.distSqr(c) <= RANGE * RANGE) known.add(p);
                    }
                }
            }
        }
    }

    // any face but the bottom, 4x4 sample points, ray from the eyes
    private static boolean canSee(Minecraft mc, BlockPos p) {
        if (mc.player == null || mc.level == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        double[] off = {0.125, 0.375, 0.625, 0.875};
        for (int face = 0; face < 5; face++) {
            for (double a : off) for (double b : off) {
                Vec3 target = switch (face) {
                    case 0 -> new Vec3(p.getX() + a, p.getY() + 1.001, p.getZ() + b);
                    case 1 -> new Vec3(p.getX() - 0.001, p.getY() + a, p.getZ() + b);
                    case 2 -> new Vec3(p.getX() + 1.001, p.getY() + a, p.getZ() + b);
                    case 3 -> new Vec3(p.getX() + a, p.getY() + b, p.getZ() - 0.001);
                    default -> new Vec3(p.getX() + a, p.getY() + b, p.getZ() + 1.001);
                };
                BlockHitResult hit = mc.level.clip(new ClipContext(eye, target,
                        ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p)) return true;
            }
        }
        return false;
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (!t.startsWith("MONOLITH!")) return;
        Matcher m = DROP.matcher(t);
        if (!m.matches()) return;
        known.clear();
        found = null;
        if (m.group(1) != null) tracker.addCoins(Long.parseLong(m.group(1).replace(",", "")));
        if (m.group(2) != null) tracker.add(POWDER, Long.parseLong(m.group(2).replace(",", "")));
        tracker.add(LOOTED, 1);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        BlockPos p = found;
        if (!inMines || p == null) return;
        if (cfg().darkMonolithBox) ESPRenderer.drawFilledBox(pose, new AABB(p).inflate(0.02), 0x80AA00AA, cam, true);
        if (cfg().darkMonolithBeacon) ESPRenderer.drawBeaconBeam(pose, p, 0xFFAA00AA, cam);
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!inMines || !cfg().darkMonolithTracker || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
