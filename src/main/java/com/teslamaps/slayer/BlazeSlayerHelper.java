package com.teslamaps.slayer;

// Daggers from SkyHanni's BlazeSlayerDaggerHelper, fire pits from SkyHanni's BlazeSlayerFirePitsWarning
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BlazeSlayerHelper {

    private static final Pattern SHIELD_TAG = Pattern.compile("(SPIRIT|ASHEN|CRYSTAL|AURIC)\\s*♨");

    private static final String[][] DAGGER_NAMES = {
            {"Twilight Dagger", "Mawdredge Dagger", "Deathripper Dagger"},
            {"Firedust Dagger", "Kindlebane Dagger", "Pyrochaos Dagger"}};
    private static final String[][] SHIELDS = {{"SPIRIT", "CRYSTAL"}, {"ASHEN", "AURIC"}};

    private static final String DAGGER_SAMPLE_TOP = "§7[§fSpirit§7] §8Ashen";
    private static final String DAGGER_SAMPLE_BOTTOM = "§bCrystal §eAuric";

    private static final int[] active = {0, 0};
    private static final boolean[] updated = {false, false};
    private static boolean clientClicked = false;
    private static String nearestShield = null;
    private static String daggerTop = null;
    private static String daggerBottom = null;
    private static long lastDaggerCheck = 0;
    private static int ticks = 0;

    private static ArmorStand pitBoss = null;
    private static double pitMaxHp = 0;
    private static double pitLastHp = 0;
    private static long pitWarnAt = 0;
    private static int pitTicks = 0;

    private static Object lastLevel = null;
    private static HudEditElement daggerHud;

    private BlazeSlayerHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        daggerHud = HudEditElement.of("blazeDaggerHud", "Blaze Daggers").section("Combat")
                .enabled(() -> cfg().blazeDaggerHud)
                .size(() -> Minecraft.getInstance().font.width(DAGGER_SAMPLE_TOP), () -> 19)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, DAGGER_SAMPLE_TOP, DAGGER_SAMPLE_BOTTOM));

        Feature.of("BlazeDaggerHud").section("Combat").when(() -> cfg().blazeDaggerHud)
                .tick(BlazeSlayerHelper::tickDagger)
                .hud("blazedaggerhud", BlazeSlayerHelper::renderDagger);
        Feature.of("InfernoFirePits").section("Combat").when(() -> cfg().infernoFirePitWarning)
                .tick(BlazeSlayerHelper::tickFirePits)
                .config("Combat", b -> {
                    b.toggle("Blaze Dagger HUD", () -> cfg().blazeDaggerHud, v -> cfg().blazeDaggerHud = v)
                            .desc("While holding a Twilight or Firedust dagger: top line = active attunement of both daggers, "
                                    + "bottom line = the other one. Move it in /tmap gui.");
                    b.toggle("  Mark Right Attunement", () -> cfg().blazeDaggerMarkShield, v -> cfg().blazeDaggerMarkShield = v)
                            .desc("Green = your dagger matches the nearest shield, red = wrong one, gold = switch to that one");
                    b.toggle("  Hide Attunement Title", () -> cfg().blazeDaggerHideTitle, v -> cfg().blazeDaggerHideTitle = v)
                            .desc("Hides Hypixel's ASHEN/SPIRIT/... title after switching (the HUD shows it)");
                    b.toggle("Fire Pits Warning", () -> cfg().infernoFirePitWarning, v -> cfg().infernoFirePitWarning = v)
                            .desc("Title + sound when your T3/T4 Inferno Demonlord drops below 33% HP");
                    b.info("Hellion Shield chat lines: Chat > More Filters");
                });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !cfg().blazeDaggerHud || clientClicked) return InteractionResult.PASS;
            int d = daggerOf(player.getItemInHand(hand));
            if (d >= 0) {
                active[d] = 1 - active[d];
                clientClicked = true;
            }
            return InteractionResult.PASS;
        });
    }

    private static void resetOnWorldChange() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == lastLevel) return;
        lastLevel = mc.level;
        pitBoss = null;
        updated[0] = updated[1] = false;
    }

    private static int daggerOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        String name = stack.getHoverName().getString();
        for (int d = 0; d < 2; d++) for (String n : DAGGER_NAMES[d]) if (name.contains(n)) return d;
        return -1;
    }

    private static void tickDagger() {
        resetOnWorldChange();
        Minecraft mc = Minecraft.getInstance();
        int holding = mc.player == null ? -1 : daggerOf(mc.player.getMainHandItem());
        if (holding < 0) {
            daggerTop = daggerBottom = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDaggerCheck > 1000) {
            lastDaggerCheck = now;
            readFromInventory(mc);
        }
        if (++ticks % 2 == 0) nearestShield = cfg().blazeDaggerMarkShield ? findNearest(mc) : null;
        daggerTop = format(holding, true, 0) + " " + format(holding, true, 1);
        daggerBottom = format(holding, false, 0) + " " + format(holding, false, 1);
    }

    private static void readFromInventory(Minecraft mc) {
        for (int d = 0; d < 2; d++) {
            if (updated[d]) continue;
            for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
                if (daggerOf(stack) != d) continue;
                for (String line : com.teslamaps.utils.ItemUtil.loreLines(stack)) {
                    if (!line.contains("Attuned: ")) continue;
                    String up = line.toUpperCase();
                    for (int i = 0; i < 2; i++) {
                        if (up.contains(SHIELDS[d][i])) {
                            active[d] = i;
                            updated[d] = true;
                        }
                    }
                }
                if (updated[d]) break;
            }
        }
    }

    private static String findNearest(Minecraft mc) {
        String best = null;
        double bestD = 10 * 10;
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            String raw = s.getName().getString();
            if (raw.indexOf('♨') < 0) continue;
            double dist = s.distanceToSqr(mc.player);
            if (dist >= bestD) continue;
            Matcher m = SHIELD_TAG.matcher(StringUtil.strip(raw));
            if (!m.find()) continue;
            best = m.group(1);
            bestD = dist;
        }
        return best;
    }

    private static String format(int holding, boolean activeRow, int dagger) {
        boolean inHand = holding == dagger;
        String shield = SHIELDS[dagger][activeRow ? active[dagger] : 1 - active[dagger]];
        String c = color(shield);
        String name = shield.charAt(0) + shield.substring(1).toLowerCase();
        if (inHand && activeRow) {
            if (nearestShield == null) return "§7[" + c + name + "§7]";
            if (shield.equals(nearestShield)) return "§a[" + c + shield + "§a]";
            return "§c[§m" + c + name + "§c]";
        }
        if (shield.equals(nearestShield)) return "§6[" + c + name + "§6]";
        return c + name;
    }

    private static String color(String shield) {
        return switch (shield) {
            case "AURIC" -> "§e";
            case "ASHEN" -> "§8";
            case "CRYSTAL" -> "§b";
            default -> "§f";
        };
    }

    /** Hypixel's attunement title after a dagger switch. Main thread. True = hide it. */
    public static boolean onTitle(String raw) {
        TeslaMapsConfig c = cfg();
        if (!c.blazeDaggerHud || !c.section("Combat")) return false;
        String t = StringUtil.stripTrim(raw);
        for (int d = 0; d < 2; d++) {
            for (int i = 0; i < 2; i++) {
                if (!t.equals(SHIELDS[d][i])) continue;
                active[d] = i;
                updated[d] = true;
                clientClicked = false;
                return c.blazeDaggerHideTitle;
            }
        }
        return false;
    }

    private static void renderDagger(GuiGraphicsExtractor g, DeltaTracker d) {
        String top = daggerTop, bottom = daggerBottom;
        if (top == null || daggerHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;
        draw(g, mc, daggerHud.x(), daggerHud.y(), daggerHud.scale(), top, bottom);
    }

    private static void tickFirePits() {
        resetOnWorldChange();
        long now = System.currentTimeMillis();
        if (now - pitWarnAt < 2000 && ++pitTicks % 10 == 0) {
            LoudSound.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
        }
        ArmorStand tag = SlayerHUD.bossTag();
        int tier = SlayerHUD.bossTier();
        if (tag == null || !SlayerHUD.ownBoss() || (tier != 3 && tier != 4) || SlayerHUD.bossName() == null
                || !SlayerHUD.bossName().startsWith("Inferno")) {
            if (tag == null) pitBoss = null;
            return;
        }
        double hp = SlayerHUD.currentHP();
        if (tag != pitBoss) {
            pitBoss = tag;
            pitMaxHp = hp;
            pitLastHp = hp;
            return;
        }
        if (hp > pitMaxHp) pitMaxHp = hp;
        double limit = pitMaxHp * 0.33;
        if (hp > 0 && hp < limit && pitLastHp >= limit) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal("§cFire Pits!"));
            pitWarnAt = now;
        }
        pitLastHp = hp;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String line1, String line2) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, line1, 0, 0, 0xFFFFFFFF, true);
        if (line2 != null) g.text(mc.font, line2, 0, 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
