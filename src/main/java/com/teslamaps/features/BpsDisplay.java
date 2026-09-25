package com.teslamaps.features;

// After Odin's BPSDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** "BPS: x.x" for every block the player breaks, anywhere. Resets after 1 s without a break. */
public final class BpsDisplay {

    private static Feature feature;
    private static HudEditElement hud;
    private static long start, last;
    private static int broken;
    private static BlockPos lastPos;
    private static long lastPosTime;
    private static String text = "§6BPS: §f0.0";

    private BpsDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("bpsDisplay", "Blocks Per Second").section("Mining")
                .enabled(() -> cfg().bpsDisplay)
                .size(() -> Minecraft.getInstance().font.width("BPS: 00.0") + 8, () -> 16)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "§6BPS: §f12.5"));
        feature = Feature.of("BpsDisplay").section("Mining").when(() -> cfg().bpsDisplay)
                .tick(BpsDisplay::tick)
                .hudHiddenInInventory("bpsdisplay", BpsDisplay::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Blocks Per Second HUD", () -> c.bpsDisplay, v -> c.bpsDisplay = v)
                            .desc("Movable \"BPS\" counter for every block you break, anywhere: mining, foraging, farming. Resets after 1 s without a break. Position in /tmap gui.")
                            .tags("bps", "blocks per second", "speed", "mining", "foraging", "farming");
                });
    }

    public static boolean listening() {
        return feature != null && cfg().bpsDisplay && feature.active();
    }

    /** Client block update, main thread. Counts the crosshair block turning into air or bedrock. */
    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState || oldState.isAir() || oldState.is(Blocks.BEDROCK)) return;
        if (!newState.isAir() && !newState.is(Blocks.BEDROCK)) return;
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK || !bh.getBlockPos().equals(pos)) return;
        long now = System.currentTimeMillis();
        if (pos.equals(lastPos) && now - lastPosTime < 150) return;
        lastPos = pos.immutable();
        lastPosTime = now;
        if (start == 0) start = now;
        last = now;
        broken++;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (start != 0 && now - last > 1000) {
            start = 0;
            broken = 0;
        }
        double bps = 0;
        if (start != 0) {
            double secs = (now - start) / 1000.0;
            bps = secs < 1 ? broken : broken / secs;
        }
        text = "§6BPS: §f" + Math.round(bps * 10) / 10.0;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), text);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        Perf.begin("BpsDisplay.hud");
        int w = mc.font.width("BPS: 00.0") + 8;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, 16);
        g.text(mc.font, s, 4, 4, 0xFFFFFFFF, true);
        g.pose().popMatrix();
        Perf.end();
    }
}
