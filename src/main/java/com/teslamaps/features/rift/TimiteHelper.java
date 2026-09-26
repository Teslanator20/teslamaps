package com.teslamaps.features.rift;

// After SkyHanni's TimiteHelper.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TimiteHelper {

    private record Label(Vec3 pos, String text) {}

    private static final String SAMPLE = "§b1.35s";
    private static final long EXPIRY_MS = 31_000, SHOW_MS = 6_000;

    private static final Map<BlockPos, Long> seen = new HashMap<>();
    private static List<Label> labels = List.of();
    private static BlockPos curPos;
    private static BlockState curState;
    private static long holdStart, lastClick;
    private static boolean doubleShot;
    private static String timer;
    private static Object lastLevel;
    private static int ticks;
    private static HudEditElement hud;

    private TimiteHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("riftTimiteTimer", "Timite Evolution Timer").section("Rift")
                .enabled(() -> cfg().riftTimiteHelper && cfg().riftTimiteEvolution)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 4f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("TimiteHelper").section("Rift").when(() -> cfg().riftTimiteHelper)
                .tick(TimiteHelper::tick)
                .world(TimiteHelper::renderWorld)
                .hud("rifttimite", TimiteHelper::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Timite Helper", () -> c.riftTimiteHelper, v -> c.riftTimiteHelper = v)
                            .desc("Mountaintop: helps timing Timite with the Time Gun.")
                            .tags("rift", "timite", "time gun", "mountaintop");
                    b.toggle("  Evolution Timer", () -> c.riftTimiteEvolution, v -> c.riftTimiteEvolution = v)
                            .desc("Countdown while shooting a blue pane with the Time Gun (2s, 1.8s when double-shooting). Move it in /tmap gui.");
                    b.toggle("  Expiry Timer", () -> c.riftTimiteExpiry, v -> c.riftTimiteExpiry = v)
                            .desc("Tracks blue panes within 15 blocks for 31s, the last 6s show in red above the block.");
                });
    }

    private static boolean timitePane(BlockState st) {
        return st.is(Blocks.BLUE_STAINED_GLASS_PANE) || st.is(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seen.clear();
            labels = List.of();
        }
        ticks++;
        if (!RiftSpot.inMountaintop()) {
            timer = null;
            labels = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        boolean gun = ItemUtil.skyblockId(mc.player.getMainHandItem()).equals("TIME_GUN");

        if (c.riftTimiteEvolution && gun && mc.options.keyUse.isDown()
                && mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos().immutable();
            BlockState st = mc.level.getBlockState(pos);
            if (!pos.equals(curPos) || st != curState) {
                lastClick = 0;
                if (pos.equals(curPos)) {
                    seen.put(pos, now);
                    doubleShot = true;
                } else {
                    doubleShot = false;
                }
            }
            curPos = pos;
            curState = st;
            if (timitePane(st)) {
                if (now - lastClick > 300) holdStart = now;
                lastClick = now;
            }
        }
        timer = null;
        if (c.riftTimiteEvolution && gun) {
            if (now - lastClick > 400) {
                holdStart = 0;
                doubleShot = false;
            }
            if (holdStart != 0 && curState != null && curState.getBlock() instanceof StainedGlassPaneBlock) {
                long left = holdStart + (doubleShot ? 1800 : 2000) - now;
                if (left > 0) timer = String.format(Locale.US, "§b%.2fs", left / 1000.0);
            }
        }

        if (!c.riftTimiteExpiry) {
            labels = List.of();
            return;
        }
        if (ticks % 20 == 0) {
            BlockPos pp = mc.player.blockPosition();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -15; dx <= 15; dx++) for (int dy = -15; dy <= 15; dy++) for (int dz = -15; dz <= 15; dz++) {
                m.set(pp.getX() + dx, pp.getY() + dy, pp.getZ() + dz);
                if (mc.level.getBlockState(m).is(Blocks.BLUE_STAINED_GLASS_PANE)) seen.putIfAbsent(m.immutable(), now);
            }
            seen.entrySet().removeIf(e -> {
                BlockState st = mc.level.getBlockState(e.getKey());
                return st.isAir() || st.is(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE)
                        || now - e.getValue() > EXPIRY_MS + 30_000 || e.getKey().distSqr(pp) > 64 * 64;
            });
        }
        if (seen.isEmpty()) {
            labels = List.of();
            return;
        }
        List<Label> out = new ArrayList<>();
        for (Map.Entry<BlockPos, Long> e : seen.entrySet()) {
            long left = e.getValue() + EXPIRY_MS - now;
            if (left <= 0 || left >= SHOW_MS) continue;
            BlockPos p = e.getKey();
            out.add(new Label(new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), "§c" + (left / 1000 + 1) + "s"));
        }
        labels = out;
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        List<Label> l = labels;
        for (int i = 0; i < l.size(); i++) ESPRenderer.drawText(pose, l.get(i).text, l.get(i).pos, 1.5f, cam);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String t = timer;
        if (t == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), t);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
