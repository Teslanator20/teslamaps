package com.teslamaps.features;

// Concept from Zen's FatalTempoOverlay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayDeque;

public final class FatalTempo {

    private static final String SAMPLE = "§6Fatal Tempo: §a200%";
    private static final long WINDOW_MS = 3000;

    private static final ArrayDeque<Long> HITS = new ArrayDeque<>();
    private static ItemStack lastStack = null;
    private static int level = 0;
    private static String line = null;
    private static HudEditElement hud;

    private FatalTempo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("fatalTempo", "Fatal Tempo").section("Combat")
                .enabled(() -> cfg().fatalTempoHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("FatalTempo").section("Combat").when(() -> cfg().fatalTempoHud)
                .tick(FatalTempo::tick)
                .hudHiddenInInventory("fataltempo", FatalTempo::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Fatal Tempo");
                    b.toggle("Fatal Tempo HUD", () -> c.fatalTempoHud, v -> c.fatalTempoHud = v)
                            .desc("While holding a Fatal Tempo weapon: hits of the last 3s as the current buff (max 200%). Position in /tmap gui.")
                            .tags("ft", "terminator");
                });
    }

    /** Melee attack or arrow hit, main thread. */
    public static void onHit() {
        if (level <= 0 || !cfg().fatalTempoHud) return;
        HITS.addLast(System.currentTimeMillis());
    }

    public static void onSound(SoundEvent sound) {
        if (sound == SoundEvents.ARROW_HIT_PLAYER && Minecraft.getInstance().isSameThread()) onHit();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        if (held != lastStack) {
            lastStack = held;
            level = readLevel(held);
        }
        long now = System.currentTimeMillis();
        while (!HITS.isEmpty() && now - HITS.peekFirst() > WINDOW_MS) HITS.pollFirst();
        if (level <= 0 || !SkyblockUtils.isInSkyblock()) {
            line = null;
            return;
        }
        int pct = Math.min(200, HITS.size() * level * 10);
        line = "§6Fatal Tempo: " + (pct >= 200 ? "§a" : "§c") + pct + "%";
    }

    private static int readLevel(ItemStack stack) {
        if (stack.isEmpty() || ItemUtil.skyblockId(stack).isEmpty()) return 0;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return 0;
        return d.copyTag().getCompoundOrEmpty("enchantments").getIntOr("ultimate_fatal_tempo", 0);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
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
