package com.teslamaps.slayer;

// Clear view from SkyHanni's BlazeSlayerClearView.kt, totem from Skytils' SlayerFeatures totemPing,
// gummy warning from SkyHanni's GummyWarning.kt, gummy timer from WpcMod's GummyBearTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfernoHelper {

    private static final Pattern TOTEM = Pattern.compile("^(\\d+)s (\\d+) hits?$");
    private static final String GUMMY_MSG = "You ate a Re-heated Gummy Polar Bear!";
    private static final long GUMMY_MS = 3_600_000L;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String TOTEM_SAMPLE = "§6Totem: §e12s §7| §c5 hits";
    private static final String GUMMY_SAMPLE = "§fGummy Bear: §a00:42:10";

    private static boolean nearBlaze;
    private static String totemLine;
    private static List<String> totemLines = List.of(), gummyLines = List.of();
    private static int totemW, gummyW;
    private static int lastTotemSec = -1;
    private static String gummyLine;
    private static long lastWarn;
    private static int ticks;
    private static HudEditElement totemHud, gummyHud;

    private InfernoHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        totemHud = HudEditElement.of("infernoTotem", "Inferno Totem").section("Combat")
                .enabled(() -> cfg().infernoTotemDisplay)
                .size(() -> TextHud.width(List.of(TOTEM_SAMPLE)), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, List.of(TOTEM_SAMPLE), TextHud.width(List.of(TOTEM_SAMPLE))));
        gummyHud = HudEditElement.of("gummyTimer", "Gummy Bear Timer").section("Combat")
                .enabled(() -> cfg().gummyTimer)
                .size(() -> TextHud.width(List.of(GUMMY_SAMPLE)), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, List.of(GUMMY_SAMPLE), TextHud.width(List.of(GUMMY_SAMPLE))));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) onAttack();
            return InteractionResult.PASS;
        });
        Feature.of("InfernoHelper").section("Combat")
                .when(() -> cfg().blazeClearView || cfg().infernoTotemDisplay || cfg().gummyWarning || cfg().gummyTimer)
                .tick(InfernoHelper::tick)
                .serverChat(InfernoHelper::onChat)
                .hudHiddenInInventory("infernototem", InfernoHelper::renderTotem)
                .hudHiddenInInventory("gummytimer", InfernoHelper::renderGummy)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Inferno Demonlord");
                    b.toggle("Blaze Slayer Clear View", () -> c.blazeClearView, v -> c.blazeClearView = v)
                            .desc("Hides all particles and fireballs while an Inferno Demonlord, Quazii or Typhoeus is within 10 blocks.")
                            .tags("blaze", "inferno", "particles", "fireball");
                    b.toggle("Totem Display", () -> c.infernoTotemDisplay, v -> c.infernoTotemDisplay = v)
                            .desc("Timer and hits of the Demonlord totem during your boss fight. Position in /tmap gui")
                            .tags("totem", "inferno", "demonlord");
                    b.slider("  Totem Ping At (s, 0 = off)", 0, 30, () -> (float) c.infernoTotemPing,
                            v -> c.infernoTotemPing = Math.round(v), 0);
                    b.toggle("Gummy Polar Bear Warning", () -> c.gummyWarning, v -> c.gummyWarning = v)
                            .desc("Blaze quest: anvil sound, title and a Bazaar button in chat when you hit with a blaze dagger or Habanero armor and no Re-heated Gummy Polar Bear is active.")
                            .tags("gummy", "polar", "bear", "smoldering");
                    b.toggle("Gummy Polar Bear Timer", () -> c.gummyTimer, v -> c.gummyTimer = v)
                            .desc("60 min HUD timer, started or extended by eating a Re-heated Gummy Polar Bear. Position in /tmap gui");
                    b.toggle("  Show Depleted", () -> c.gummyTimerExpired, v -> c.gummyTimerExpired = v);
                });
    }

    public static boolean hideParticle() {
        return nearBlaze && cfg().blazeClearView && cfg().section("Combat");
    }

    public static boolean shouldHide(Entity e) {
        return nearBlaze && e instanceof AbstractHurtingProjectile && cfg().blazeClearView && cfg().section("Combat");
    }

    public static boolean gummyActive() {
        return cfg().gummyTimerEnd > System.currentTimeMillis()
                || com.teslamaps.features.hud.EffectTimers.expiresAt("Smoldering Polarization I") > System.currentTimeMillis();
    }

    private static void onChat(String raw) {
        if (!StringUtil.stripTrim(raw).equals(GUMMY_MSG)) return;
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        c.gummyTimerEnd = (c.gummyTimerEnd > now ? c.gummyTimerEnd : now) + GUMMY_MS;
        TeslaMapsConfig.save();
    }

    private static void onAttack() {
        TeslaMapsConfig c = cfg();
        if (!c.gummyWarning || !c.section("Combat") || !"Inferno Demonlord".equals(SlayerQuest.boss())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean dagger = isBlazeDagger(ItemUtil.skyblockId(mc.player.getMainHandItem()));
        if (!dagger && !habanero(mc)) return;
        if (gummyActive()) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < 10_000) return;
        lastWarn = now;
        TickScheduler.schedule(10, () -> {
            LoudSound.play(SoundEvents.ANVIL_LAND, 0.5f, 1.0f);
            mc.gui.setTimes(0, 40, 10);
            mc.gui.setTitle(Component.literal("§4§lNo Polar Bear Active!"));
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §cYou do not have an active Re-heated Gummy Polar Bear! ")
                    .append(Component.literal("§a[Bazaar]").withStyle(s -> s.withClickEvent(
                            new ClickEvent.RunCommand("/bz Re-heated Gummy Polar Bear")))));
        });
    }

    private static boolean isBlazeDagger(String id) {
        return id.endsWith("DUST_DAGGER") || id.endsWith("FIRE_DAGGER") || id.endsWith("MAW_DAGGER");
    }

    private static boolean habanero(Minecraft mc) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = mc.player.getItemBySlot(slot);
            if (st.isEmpty()) continue;
            CustomData d = st.get(DataComponents.CUSTOM_DATA);
            if (d != null && d.copyTag().getCompoundOrEmpty("enchantments").contains("ultimate_habanero_tactics")) return true;
        }
        return false;
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (c.blazeClearView && ticks % 10 == 0) nearBlaze = scanBlaze(mc);
        if (!c.blazeClearView) nearBlaze = false;
        if (c.infernoTotemDisplay && ticks % 2 == 0) scanTotem(mc, c);
        if (!c.infernoTotemDisplay) totemLine = null;
        if (totemLine == null) totemLines = List.of();
        else if (totemLines.isEmpty() || !totemLines.get(0).equals(totemLine)) {
            totemLines = List.of(totemLine);
            totemW = TextHud.width(totemLines);
        }
        if (c.gummyTimer && ticks % 10 == 0) {
            long left = (c.gummyTimerEnd - System.currentTimeMillis()) / 1000;
            if (left < 0) gummyLine = c.gummyTimerExpired && c.gummyTimerEnd > 0 ? "§fGummy Bear: §cDepleted" : null;
            else gummyLine = "§fGummy Bear: §a" + String.format("%02d:%02d:%02d", left / 3600, (left % 3600) / 60, left % 60);
            gummyLines = gummyLine == null ? List.of() : List.of(gummyLine);
            gummyW = TextHud.width(gummyLines);
        }
    }

    private static boolean scanBlaze(Minecraft mc) {
        if (!"Inferno Demonlord".equals(SlayerQuest.boss()) && !SlayerHUD.isActive()) return false;
        AABB box = mc.player.getBoundingBox().inflate(10);
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::hasCustomName)) {
            String n = as.getName().getString();
            if ((n.contains("Inferno Demonlord") || n.contains("ⓆⓊⒶⓏⒾⒾ") || n.contains("ⓉⓎⓅⒽⓄⒺⓊⓈ"))
                    && as.distanceToSqr(mc.player) < 100) return true;
        }
        return false;
    }

    private static void scanTotem(Minecraft mc, TeslaMapsConfig c) {
        if (!SlayerQuest.bossActive() || !"Inferno Demonlord".equals(SlayerQuest.boss())) {
            totemLine = null;
            lastTotemSec = -1;
            return;
        }
        Entity center = SlayerHUD.getBossEntity() != null ? SlayerHUD.getBossEntity() : mc.player;
        AABB box = center.getBoundingBox().inflate(25, 15, 25);
        ArmorStand best = null;
        Matcher bestM = null;
        double bestD = Double.MAX_VALUE;
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::hasCustomName)) {
            String n = StringUtil.stripTrim(as.getName().getString());
            if (!n.endsWith("hits") && !n.endsWith("hit")) continue;
            Matcher m = TOTEM.matcher(n);
            if (!m.matches()) continue;
            double d = as.distanceToSqr(center);
            if (d < bestD) {
                bestD = d;
                best = as;
                bestM = m;
            }
        }
        if (best == null) {
            totemLine = null;
            lastTotemSec = -1;
            return;
        }
        int sec = Integer.parseInt(bestM.group(1));
        totemLine = "§6Totem: §e" + sec + "s §7| §c" + bestM.group(2) + " hits";
        if (c.infernoTotemPing > 0 && sec == c.infernoTotemPing && sec != lastTotemSec) {
            mc.gui.setTimes(0, 20, 5);
            mc.gui.setTitle(Component.literal("§6§lTotem!"));
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
        }
        lastTotemSec = sec;
    }

    private static void renderTotem(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = totemLines;
        if (totemHud == null || ls.isEmpty() || Minecraft.getInstance().options.hideGui) return;
        TextHud.draw(g, Minecraft.getInstance(), totemHud.x(), totemHud.y(), totemHud.scale(), ls, totemW);
    }

    private static void renderGummy(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = gummyLines;
        if (gummyHud == null || ls.isEmpty() || !cfg().gummyTimer || Minecraft.getInstance().options.hideGui) return;
        TextHud.draw(g, Minecraft.getInstance(), gummyHud.x(), gummyHud.y(), gummyHud.scale(), ls, gummyW);
    }
}
