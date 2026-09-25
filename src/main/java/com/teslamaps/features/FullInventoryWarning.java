package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public final class FullInventoryWarning {

    private static boolean full;
    private static int timer;
    private static long shownAt;

    private FullInventoryWarning() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FullInventoryWarning").section("Inventory").when(() -> cfg().fullInventoryWarning)
                .tick(FullInventoryWarning::tick)
                .hud("fullinventorywarning", FullInventoryWarning::render)
                .config("Inventory", b -> {
                    b.label("Full Inventory Warning");
                    b.toggle("Full Inventory Warning", () -> cfg().fullInventoryWarning, v -> cfg().fullInventoryWarning = v)
                            .desc("Big red title when your inventory runs out of free slots.");
                    b.slider("  Warn At Free Slots", 0f, 9f,
                            () -> (float) cfg().fullInventorySlots, v -> cfg().fullInventorySlots = Math.round(v), 0);
                    b.toggle("  Play Sound", () -> cfg().fullInventorySound, v -> cfg().fullInventorySound = v);
                });
    }

    private static void tick() {
        if (timer-- > 0) return;
        timer = 5;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator() || !SkyblockUtils.isInSkyblock()) {
            full = false;
            return;
        }
        int empty = 0;
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) if (st.isEmpty()) empty++;
        boolean now = empty <= cfg().fullInventorySlots;
        if (now && !full) {
            shownAt = System.currentTimeMillis();
            if (cfg().fullInventorySound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f);
        }
        full = now;
    }

    private static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        if (shownAt == 0 || System.currentTimeMillis() - shownAt > 2500) return;
        Minecraft mc = Minecraft.getInstance();
        String t = "§c§lInventory Full";
        ctx.pose().pushMatrix();
        ctx.pose().translate(ctx.guiWidth() / 2f, ctx.guiHeight() / 2f - 40);
        ctx.pose().scale(3f, 3f);
        ctx.text(mc.font, t, -mc.font.width(t) / 2, 0, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }
}
