package com.teslamaps.features.hud;

// Ported from Odin's features/impl/skyblock/SpringBoots.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpringBootsHeight {

    private static final float[] HIGH = {0.82539684f, 0.8888889f, 0.93650794f, 1.0476191f, 1.1746032f, 1.3174603f, 1.7777778f};
    private static final float[] BLOCKS = {
            0.0f, 3.0f, 6.5f, 9.0f, 11.5f, 13.5f, 16.0f, 18.0f, 19.0f,
            20.5f, 22.5f, 25.0f, 26.5f, 28.0f, 29.0f, 30.0f, 31.0f, 33.0f,
            34.0f, 35.5f, 37.0f, 38.0f, 39.5f, 40.0f, 41.0f, 42.5f, 43.5f,
            44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f,
            53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f};
    private static final String SAMPLE = "§dHeight: §a33.0";

    private static HudEditElement hud;
    private static int high, low;
    private static float blocks;
    private static String line;

    private SpringBootsHeight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("springBoots", "Spring Boots Height")
                .enabled(() -> cfg().springBootsHeight)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("SpringBootsHeight").when(() -> cfg().springBootsHeight)
                .tick(SpringBootsHeight::tick)
                .hudHiddenInInventory("springboots", SpringBootsHeight::render)
                .world(SpringBootsHeight::renderWorld)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Spring Boots");
                    b.toggle("Spring Boots Height", () -> c.springBootsHeight, v -> c.springBootsHeight = v)
                            .desc("While sneaking in Spring Boots: how many blocks the jump will take you. Position in /tmap gui.");
                    b.toggle("  Target Box", () -> c.springBootsBox, v -> c.springBootsBox = v)
                            .desc("Wireframe box at the height you would land on.");
                });
    }

    private static boolean wearing(LocalPlayer p) {
        return "SPRING_BOOTS".equals(ItemUtil.skyblockId(p.getItemBySlot(EquipmentSlot.FEET)));
    }

    /** Sound packet hook, both threads. */
    public static void onSound(SoundEvent sound, float pitch) {
        TeslaMapsConfig c = cfg();
        if (!c.springBootsHeight) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null) return;
        if (sound == SoundEvents.NOTE_BLOCK_PLING.value()) {
            if (!mc.player.isCrouching() || !wearing(mc.player) || !SkyblockUtils.isInSkyblock()) return;
            if (approx(pitch, 0.6984127f)) low = Math.min(low + 1, 2);
            else for (float h : HIGH) if (approx(pitch, h)) { high++; break; }
        } else if (sound == SoundEvents.FIREWORK_ROCKET_LAUNCH && (approx(pitch, 0.0952381f) || approx(pitch, 1.6984127f))) {
            high = 0;
            low = 0;
        } else {
            return;
        }
        blocks = BLOCKS[Math.max(0, Math.min(BLOCKS.length - 1, high + low))];
        line = blocks == 0 ? null : "§dHeight: " + color(blocks) + blocks;
    }

    private static boolean approx(float a, float b) { return Math.abs(a - b) < 0.001f; }

    private static void tick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p.isCrouching() && wearing(p)) return;
        high = 0;
        low = 0;
        blocks = 0;
        line = null;
    }

    private static String color(float b) {
        if (b <= 13.5f) return "§c";
        if (b <= 22.5f) return "§e";
        if (b <= 33.0f) return "§6";
        if (b <= 43.5f) return "§a";
        return "§b";
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        if (blocks == 0 || !cfg().springBootsBox || !SkyblockUtils.isInSkyblock()) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        double x = p.getX(), y = p.getY() + blocks, z = p.getZ();
        ESPRenderer.drawBoxOutline(pose, new AABB(x, y, z, x + 1, y + 1, z + 1), 0xFFFF5555, 2f, cam);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        Minecraft mc = Minecraft.getInstance();
        if (l == null || mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
