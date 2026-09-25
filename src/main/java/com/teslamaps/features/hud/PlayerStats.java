package com.teslamaps.features.hud;

// Text HUDs ported from Odin's PlayerDisplay.kt + ActionBarListener.kt, bars after Bax's StatusBarHud
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerStats {

    private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)[❤]");
    private static final Pattern MANA = Pattern.compile("([\\d,]+)/([\\d,]+)[✎]");
    private static final Pattern OVERFLOW = Pattern.compile("([\\d,]+)[ʬ]");
    private static final Pattern DEFENSE = Pattern.compile("([\\d,]+)[❈]");
    private static final Pattern VITALITY = Pattern.compile("([\\d.,]+)/([\\d.,]+)");
    private static final long FRESH_MS = 1500;

    private static int hp, maxHp, mana, maxMana, overflow, defense, speed;
    private static double vitality, maxVitality;
    private static long hpAt, manaAt, vitalityAt;
    private static boolean inSkyblock;
    private static int timer;

    private static String tHealth = "", tMana = "", tOverflow = "", tDefense = "", tVitality = "", tEhp = "", tSpeed = "";
    private static float hpFill, hpOver, manaFill, overFill;
    private static float shownHp = -1, shownMana = -1;
    private static String tBarHp = "", tBarMana = "";
    public static HudEditElement healthBar, manaBar;

    private PlayerStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() { return cfg().playerStatHuds; }

    public static void register() {
        text("stHealth", "Health", () -> cfg().statHealth, () -> tHealth, "§c3,000/4,000❤");
        text("stMana", "Mana", () -> cfg().statMana, () -> tMana, "§b2,000/2,500✎");
        text("stOverflow", "Overflow Mana", () -> cfg().statOverflow, () -> tOverflow, "§3333ʬ");
        text("stDefense", "Defense", () -> cfg().statDefense, () -> tDefense, "§a1,000❈");
        text("stVitality", "Vitality", () -> cfg().statVitality, () -> tVitality, "§4100/100♨");
        text("stEhp", "Effective Health", () -> cfg().statEhp, () -> tEhp, "§240,000 EHP");
        text("stSpeed", "Speed", () -> cfg().statSpeed, () -> tSpeed, "§f400✦");
        healthBar = bar("stHealthBar", "Health Bar", () -> cfg().statHealthBar, true);
        manaBar = bar("stManaBar", "Mana Bar", () -> cfg().statManaBar, false);
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("tmap")
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("bars").executes(x -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.schedule(() -> mc.setScreen(new com.teslamaps.screen.StatusBarsScreen(null)));
                            return 1;
                        }))));

        Feature.of("PlayerStats").when(PlayerStats::on)
                .tick(PlayerStats::tick)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Player Stats");
                    b.toggle("Player Stat HUDs", () -> c.playerStatHuds, v -> c.playerStatHuds = v)
                            .desc("HP, mana, defense ... from the action bar as their own HUD elements (/tmap gui). "
                                    + "Pair with the Action Bar Filter to remove them from the action bar.");
                    b.toggle("  Icons", () -> c.statHudIcons, v -> c.statHudIcons = v);
                    b.toggle("  Health", () -> c.statHealth, v -> c.statHealth = v);
                    b.toggle("  Mana", () -> c.statMana, v -> c.statMana = v);
                    b.toggle("  Overflow Mana", () -> c.statOverflow, v -> c.statOverflow = v);
                    b.toggle("  Defense", () -> c.statDefense, v -> c.statDefense = v);
                    b.toggle("  Vitality (Rift)", () -> c.statVitality, v -> c.statVitality = v);
                    b.toggle("  Effective Health", () -> c.statEhp, v -> c.statEhp = v)
                            .desc("HP x (1 + defense / 100)");
                    b.toggle("  Speed", () -> c.statSpeed, v -> c.statSpeed = v);
                    b.toggle("  Health Bar", () -> c.statHealthBar, v -> c.statHealthBar = v)
                            .desc("Red bar, yellow part = health above max (absorption)");
                    b.toggle("    Hide Vanilla Hearts", () -> c.statHideHearts, v -> c.statHideHearts = v);
                    b.toggle("  Mana Bar", () -> c.statManaBar, v -> c.statManaBar = v)
                            .desc("Blue bar, cyan part = overflow mana");
                    b.slider("  Bar Width", 40f, 200f, () -> (float) c.statBarWidth, v -> c.statBarWidth = Math.round(v), 0);
                    b.button("  Status Bars Editor (/tmap bars)",
                            () -> Minecraft.getInstance().setScreen(new com.teslamaps.screen.StatusBarsScreen(Minecraft.getInstance().screen)))
                            .desc("Drag bars to move, drag the right edge for width, right-click for color, text, direction, max/overflow and on/off.");
                });
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, original ->
                (ctx, delta) -> { if (!hideHearts()) original.extractRenderState(ctx, delta); });
    }

    private static boolean hideHearts() {
        TeslaMapsConfig c = cfg();
        return c.playerStatHuds && c.statHealthBar && c.statHideHearts && inSkyblock;
    }

    private static void text(String prefix, String label, BooleanSupplier enabled, Supplier<String> value, String sample) {
        HudEditElement e = HudEditElement.of(prefix, label)
                .enabled(() -> on() && enabled.getAsBoolean())
                .size(() -> Math.max(20, Minecraft.getInstance().font.width(value.get().isEmpty() ? sample : value.get())), () -> 9)
                .preview((g, mc, x, y, s) -> drawText(g, mc, x, y, s, value.get().isEmpty() ? sample : value.get()));
        Feature.of("PlayerStats." + label).when(() -> on() && enabled.getAsBoolean())
                .hudHiddenInInventory(prefix.toLowerCase(Locale.ROOT), (g, d) -> {
                    Minecraft mc = Minecraft.getInstance();
                    String s = value.get();
                    if (mc.options.hideGui || s.isEmpty() || !inSkyblock) return;
                    drawText(g, mc, e.x(), e.y(), e.scale(), s);
                });
    }

    private static HudEditElement bar(String prefix, String label, BooleanSupplier enabled, boolean health) {
        HudEditElement e = HudEditElement.of(prefix, label)
                .enabled(() -> on() && enabled.getAsBoolean())
                .size(() -> barWidth(health), () -> 5)
                .preview((g, mc, x, y, s) -> drawBar(g, x, y, s, health ? 0.75f : 0.6f, health ? 0.15f : 0.2f, health));
        Feature.of("PlayerStats." + label).when(() -> on() && enabled.getAsBoolean())
                .hudHiddenInInventory(prefix.toLowerCase(Locale.ROOT), (g, d) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.options.hideGui || !inSkyblock) return;
                    if (health) {
                        if (maxHp <= 0) return;
                        shownHp = shownHp < 0 ? hpFill : shownHp + (hpFill - shownHp) * 0.25f;
                        drawBar(g, e.x(), e.y(), e.scale(), shownHp, hpOver, true);
                    } else {
                        if (maxMana <= 0) return;
                        shownMana = shownMana < 0 ? manaFill : shownMana + (manaFill - shownMana) * 0.25f;
                        drawBar(g, e.x(), e.y(), e.scale(), shownMana, overFill, false);
                    }
                });
        return e;
    }

    public static int barWidth(boolean health) {
        TeslaMapsConfig c = cfg();
        int w = health ? c.stHealthBarW : c.stManaBarW;
        return w > 0 ? w : c.statBarWidth;
    }

    /** Editor preview, same look as the live bar. */
    public static void drawPreview(GuiGraphicsExtractor g, boolean health, int x, int y, float scale) {
        drawBar(g, x, y, scale, health ? 0.75f : 0.6f, health ? 0.15f : 0.2f, health);
    }

    private static void drawText(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(mc.font, s, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private static void drawBar(GuiGraphicsExtractor g, int x, int y, float scale, float fill, float over, boolean health) {
        TeslaMapsConfig c = cfg();
        int w = barWidth(health), h = 5;
        boolean reverse = health ? c.stHealthBarReverse : c.stManaBarReverse;
        boolean showOver = health ? c.stHealthBarOver : c.stManaBarOver;
        int color = TeslaMapsConfig.parseColor(health ? c.stHealthBarColor : c.stManaBarColor) | 0xFF000000;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -1, -1, w + 2, h + 2);
        int fw = Math.round(Math.max(0, Math.min(1, fill)) * w);
        if (reverse) g.fill(w - fw, 0, w, h, color);
        else g.fill(0, 0, fw, h, color);
        if (over > 0 && showOver) {
            int ow = Math.round(Math.min(1, over) * w);
            int oc = health ? 0xFFFFD54F : 0xFF4DD0E1;
            if (reverse) g.fill(w - ow, 0, w, 2, oc);
            else g.fill(0, 0, ow, 2, oc);
        }
        String mode = health ? c.stHealthBarText : c.stManaBarText;
        if (mode != null && !"Off".equals(mode)) {
            String t = health ? tBarHp : tBarMana;
            if (t.isEmpty()) t = health ? "§c3,000/4,000" : "§b2,000/2,500";
            var font = Minecraft.getInstance().font;
            int tw = font.width(t);
            int tx = switch (mode) {
                case "Left" -> -tw - 4;
                case "Right" -> w + 4;
                default -> (w - tw) / 2;
            };
            int ty = "Above".equals(mode) ? -11 : (h - 8) / 2;
            g.text(font, t, tx, ty, 0xFFFFFFFF, true);
        }
        pose.popMatrix();
    }

    /** Raw action bar text, from InGameHudMixin. */
    public static void onActionBar(String raw) {
        com.teslamaps.utils.Placeholders.actionBar = raw;
        if (!cfg().playerStatHuds) return;
        String s = StringUtil.strip(raw);
        long now = System.currentTimeMillis();
        Matcher m = HEALTH.matcher(s);
        if (m.find()) {
            hp = parse(m.group(1), hp);
            maxHp = parse(m.group(2), maxHp);
            hpAt = now;
        }
        m = MANA.matcher(s);
        if (m.find()) {
            mana = parse(m.group(1), mana);
            maxMana = parse(m.group(2), maxMana);
            manaAt = now;
        }
        m = OVERFLOW.matcher(s);
        overflow = m.find() ? parse(m.group(1), overflow) : (now - manaAt < 100 ? 0 : overflow);
        m = DEFENSE.matcher(s);
        if (m.find()) defense = parse(m.group(1), defense);
        m = VITALITY.matcher(s);
        if (m.find()) {
            vitality = parseD(m.group(1), vitality);
            maxVitality = parseD(m.group(2), maxVitality);
            vitalityAt = now;
        }
    }

    private static void tick() {
        if (++timer % 2 != 0) return;
        Player p = Minecraft.getInstance().player;
        inSkyblock = SkyblockUtils.isInSkyblock();
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        int curHp = hp;
        // the action bar lags behind, the vanilla hearts scale with the real HP
        if (maxHp > 0 && now - hpAt > FRESH_MS && p.getMaxHealth() > 0) curHp = (int) (maxHp * p.getHealth() / p.getMaxHealth());
        var attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        speed = attr == null ? 0 : (int) Math.floor(attr.getBaseValue() * 1000);
        boolean icons = c.statHudIcons;

        tHealth = maxHp > 0 ? (curHp > maxHp ? "§e" : "§c") + num(curHp) + "/" + num(maxHp) + (icons ? "❤" : "") : "";
        tMana = maxMana > 0 ? "§b" + num(mana) + "/" + num(maxMana) + (icons ? "✎" : "") : "";
        tOverflow = overflow > 0 ? "§3" + num(overflow) + (icons ? "ʬ" : "") : "";
        tDefense = defense > 0 ? "§a" + num(defense) + (icons ? "❈" : "") : "";
        tVitality = maxVitality > 0 && now - vitalityAt < 10_000
                ? "§4" + num((int) vitality) + "/" + num((int) maxVitality) + (icons ? "♨" : "") : "";
        tEhp = maxHp > 0 ? "§2" + num((int) (curHp * (1 + defense / 100.0))) + (icons ? " EHP" : "") : "";
        tSpeed = speed > 0 ? "§f" + num(speed) + (icons ? "✦" : "") : "";

        hpFill = maxHp > 0 ? Math.min(1f, curHp / (float) maxHp) : 0;
        hpOver = maxHp > 0 && curHp > maxHp ? (curHp - maxHp) / (float) maxHp : 0;
        manaFill = maxMana > 0 ? Math.min(1f, mana / (float) maxMana) : 0;
        tBarHp = maxHp > 0 && !"Off".equals(c.stHealthBarText)
                ? (curHp > maxHp ? "§e" : "§c") + num(curHp) + (c.stHealthBarMax ? "/" + num(maxHp) : "") : "";
        tBarMana = maxMana > 0 && !"Off".equals(c.stManaBarText)
                ? "§b" + num(mana) + (c.stManaBarMax ? "/" + num(maxMana) : "") : "";
        overFill = maxMana > 0 ? overflow / (float) maxMana : 0;
    }

    private static String num(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseD(String s, double fallback) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
