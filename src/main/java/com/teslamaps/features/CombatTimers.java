package com.teslamaps.features;

// Wither shield + tactical ported from Devonian's WitherShieldTimer.kt / TacticalInsertionTimer.kt,
// return waypoint from NoammAddons' TacTimer.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public class CombatTimers {

    private static final String SHIELD_SAMPLE = "§6Shield: §aREADY";
    private static final String TAC_SAMPLE = "§a3.00s";

    private static long shieldUse = 0;
    private static int shieldCooldown = 0;
    private static boolean shieldPending = false;
    private static String shieldLine = null;

    private static long tacticalClick = 0;
    private static final ServerTickTimer TACTICAL = new ServerTickTimer();
    private static BlockPos tacticalPos = null;
    private static String tacticalLine = null;

    private static Object lastLevel = null;
    private static HudEditElement shieldHud;
    private static HudEditElement tacticalHud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        shieldHud = HudEditElement.of("witherShieldHud", "Wither Shield").section("Timers")
                .enabled(() -> cfg().witherShieldTimer && cfg().witherShieldHud)
                .size(() -> Minecraft.getInstance().font.width(cfg().witherShieldCompact ? "§a5.00s" : SHIELD_SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, cfg().witherShieldCompact ? "§a5.00s" : SHIELD_SAMPLE));
        tacticalHud = HudEditElement.of("tacticalHud", "Tactical Insertion").section("Timers")
                .enabled(() -> cfg().tacticalInsertionTimer && cfg().tacticalHud)
                .size(() -> Minecraft.getInstance().font.width(TAC_SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, TAC_SAMPLE));
        Feature.of("WitherShieldHud").section("Timers").when(() -> cfg().witherShieldTimer && cfg().witherShieldHud)
                .hud("withershieldhud", CombatTimers::renderShield);
        Feature.of("TacticalHud").section("Timers").when(() -> cfg().tacticalInsertionTimer && cfg().tacticalHud)
                .hud("tacticalhud", CombatTimers::renderTactical);
        Feature.of("TacticalWaypoint").section("Timers").when(() -> cfg().tacticalInsertionTimer && cfg().tacticalWaypoint)
                .world(CombatTimers::renderWaypoint);
    }

    public static void onUseItem(ItemStack item) {
        TeslaMapsConfig c = cfg();
        if (c.witherShieldTimer && !(shieldCooldown > 0 && ServerTickTimer.now() < shieldUse + shieldCooldown)) {
            var scrolls = ItemUtil.abilityScrolls(item);
            if (scrolls.contains("WITHER_SHIELD_SCROLL")) {
                shieldUse = ServerTickTimer.now();
                shieldCooldown = scrolls.size() >= 3 ? 100 : 200;
                shieldPending = true;
            }
        }
        if (c.tacticalInsertionTimer && ItemUtil.skyblockId(item).equals("TACTICAL_INSERTION")) {
            tacticalClick = System.currentTimeMillis();
        }
    }

    public static void onSound(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;
        TeslaMapsConfig c = cfg();
        if (c.witherShieldTimer && shieldCooldown > 0) {
            if (sound == SoundEvents.GENERIC_EXPLODE.value() && volume == 1f && pitch == 1f) shieldPending = false;
            else if (sound == SoundEvents.PLAYER_LEVELUP && volume == 1f && pitch == 3f) shieldReady(c);
        }
        if (c.tacticalInsertionTimer && sound == SoundEvents.FLINTANDSTEEL_USE && Math.abs(pitch - 0.74603176f) < 0.01f
                && tacticalClick != 0 && System.currentTimeMillis() - tacticalClick < 500 && !TACTICAL.active()) {
            TACTICAL.start(60);
            tacticalClick = 0;
            tacticalPos = mc.player != null ? mc.player.blockPosition() : null;
        }
    }

    private static void shieldReady(TeslaMapsConfig c) {
        boolean wasCounting = shieldCooldown > 0;
        shieldCooldown = 0;
        shieldPending = false;
        if (wasCounting && c.witherShieldChime) LoudSound.play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.5f);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        TeslaMapsConfig c = cfg();

        long ttl = 0;
        if (shieldCooldown > 0) {
            ttl = shieldUse + shieldCooldown - ServerTickTimer.now();
            if (ttl < 0 && (shieldPending || ttl < -20)) {
                shieldReady(c);
                ttl = 0;
            }
        }
        if (c.witherShieldHud) {
            DungeonTimers.clear("witherShield");
            String str = shieldCooldown > 0 ? color(ttl, shieldCooldown) + ServerTickTimer.seconds(Math.max(0, ttl)) : "§aREADY";
            shieldLine = c.witherShieldCompact ? str : "§6Shield: " + str;
        } else {
            shieldLine = null;
            if (ttl > 0) DungeonTimers.set("witherShield", "§dW-Impact§f: §c" + ServerTickTimer.seconds(ttl));
            else DungeonTimers.clear("witherShield");
        }

        if (TACTICAL.active()) {
            long rem = TACTICAL.remaining();
            if (c.tacticalHud) {
                DungeonTimers.clear("tactical");
                double s = rem / 20.0;
                tacticalLine = (s > 1.5 ? "§a" : s > 0.5 ? "§e" : "§c") + String.format(Locale.ROOT, "%.2fs", s);
            } else {
                tacticalLine = null;
                DungeonTimers.set("tactical", "§eTactical§f: §c" + ServerTickTimer.seconds(rem));
            }
        } else {
            tacticalLine = null;
            tacticalPos = null;
            DungeonTimers.clear("tactical");
        }
    }

    private static String color(long ttl, long max) {
        if (ttl >= max * 0.75) return "§2";
        if (ttl >= max * 0.50) return "§e";
        if (ttl >= max * 0.25) return "§6";
        return "§4";
    }

    private static void renderShield(GuiGraphicsExtractor g, DeltaTracker d) {
        String line = shieldLine;
        if (line == null || shieldHud == null || !SkyblockUtils.isInSkyblock()) return;
        if (cfg().witherShieldHideReady && shieldCooldown == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, shieldHud.x(), shieldHud.y(), shieldHud.scale(), line);
    }

    private static void renderTactical(GuiGraphicsExtractor g, DeltaTracker d) {
        String line = tacticalLine;
        if (line == null || tacticalHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, tacticalHud.x(), tacticalHud.y(), tacticalHud.scale(), line);
    }

    private static void renderWaypoint(PoseStack pose, Vec3 cam) {
        BlockPos p = tacticalPos;
        if (p == null) return;
        EspStyle.of(cfg().tacticalWaypointEsp).drawBox(pose, new AABB(p), cam);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    public static void reset() {
        shieldUse = 0; shieldCooldown = 0; shieldPending = false; shieldLine = null;
        tacticalClick = 0; TACTICAL.stop(); tacticalPos = null; tacticalLine = null;
        DungeonTimers.clear("witherShield");
        DungeonTimers.clear("tactical");
    }
}
