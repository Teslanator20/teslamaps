package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.WitherDragons;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class LastBreathSound {

    private static int ticks = 0;
    private static long useStart = -1;
    private static String hudLine = null;
    private static HudEditElement hud;

    // Charge HUD + per-dragon targets, concept from shizo's Debuff.kt
    public static void register() {
        hud = HudEditElement.of("lastBreathHud", "Last Breath Charge").section("Auto")
                .enabled(() -> TeslaMapsConfig.get().lastBreathSound && TeslaMapsConfig.get().lastBreathHud)
                .size(() -> Minecraft.getInstance().font.width("§aLB: 20"), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "§aLB: 20"));
        Feature.of("LastBreathHud").section("Auto")
                .when(() -> TeslaMapsConfig.get().lastBreathSound && TeslaMapsConfig.get().lastBreathHud)
                .hud("lastbreathhud", LastBreathSound::renderHud);
    }

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.lastBreathSound || mc.player == null || !isPullingLastBreath(mc)) {
            ticks = 0;
            useStart = -1;
            hudLine = null;
            return;
        }

        ticks++;
        if (useStart < 0) useStart = ServerTickTimer.now();
        int target = target(c, mc);
        float pitch = (float) (0.5 + Math.min(ticks, 20) / 20.0 * 1.5); // rescale 0..20 ticks -> 0.5..2.0
        var sound = (ticks >= target) ? SoundEvents.ARROW_HIT_PLAYER : SoundEvents.NOTE_BLOCK_PLING.value();
        ArrowHitSound.bypass = true;
        try { mc.player.playSound(sound, c.lastBreathVolume, pitch); } finally { ArrowHitSound.bypass = false; }

        if (c.lastBreathHud) {
            long charged = ServerTickTimer.now() - useStart;
            hudLine = (charged >= target ? "§aLB: " : "§cLB: ") + charged;
        } else hudLine = null;
    }

    private static int target(TeslaMapsConfig c, Minecraft mc) {
        if (!c.lastBreathPerDragon) return c.lastBreathThreshold;
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (f == null || f.getLevel() != 7 || mc.player.getY() > 45) return c.lastBreathThreshold;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1f).scale(100));
        WitherDragons.Dragon best = null;
        double bestDist = Double.MAX_VALUE;
        for (WitherDragons.Dragon d : WitherDragons.Dragon.values()) {
            var hit = d.box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double dist = hit.get().distanceToSqr(eye);
            if (dist < bestDist) { bestDist = dist; best = d; }
        }
        if (best == null) return c.lastBreathThreshold;
        return switch (best) {
            case RED -> c.lastBreathRed;
            case ORANGE -> c.lastBreathOrange;
            case GREEN -> c.lastBreathGreen;
            case BLUE -> c.lastBreathBlue;
            case PURPLE -> c.lastBreathPurple;
        };
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        String line = hudLine;
        if (line == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), line);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private static boolean isPullingLastBreath(Minecraft mc) {
        if (!mc.player.isUsingItem()) return false;
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() || mc.player.getUseItem() != held) return false;
        String id = skyblockId(held);
        return "LAST_BREATH".equals(id) || "STARRED_LAST_BREATH".equals(id);
    }

    private static String skyblockId(ItemStack stack) {
        return com.teslamaps.utils.ItemUtil.skyblockId(stack);
    }
}
