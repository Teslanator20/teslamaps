package com.teslamaps.adm.misc;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's misc/HealthMana.kt (+ OdinHuds' hide wrapper on our own Health/Mana text HUDs)
public final class HealthMana {
    private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)❤");
    private static final Pattern MANA = Pattern.compile("([\\d,]+)/([\\d,]+)✎");

    private static int health, maxHealth, mana, maxMana;
    private static HudEditElement healthHud, manaHud;

    private HealthMana() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    static void register() {
        healthHud = HudEditElement.of("admHealthBarHud", "ADM Health Bar").section(Adm.SECTION)
                .enabled(() -> cfg().admHealthMana && cfg().admHealthBar)
                .size(() -> cfg().admHealthBarWidth, () -> cfg().admHealthBarHeight)
                .scaleRange(0.5f, 5f)
                .preview((g, mc, x, y, s) -> draw(g, x, y, s, 3000, 4000, true));
        manaHud = HudEditElement.of("admManaBarHud", "ADM Mana Bar").section(Adm.SECTION)
                .enabled(() -> cfg().admHealthMana && cfg().admManaBar)
                .size(() -> cfg().admManaBarWidth, () -> cfg().admManaBarHeight)
                .scaleRange(0.5f, 5f)
                .preview((g, mc, x, y, s) -> draw(g, x, y, s, 2000, 20000, false));
        Feature.of("AdmHealthMana").section(Adm.SECTION).when(() -> cfg().admHealthMana)
                .hudHiddenInInventory("admhealthbar", (g, d) -> {
                    if (cfg().admHealthBar) bar(g, true);
                })
                .hudHiddenInInventory("admmanabar", (g, d) -> {
                    if (cfg().admManaBar) bar(g, false);
                })
                .config(Adm.SECTION, b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Health & Mana");
                    b.toggle("Health & Mana", () -> c.admHealthMana, v -> c.admHealthMana = v)
                            .desc("Health and mana bars, and hiding our Health/Mana HUDs until the stat runs low.");
                    b.toggle("  Hide Health/Mana Above %", () -> c.admHideHealthManaAbove, v -> c.admHideHealthManaAbove = v)
                            .desc("Hides the Player Stats Health and Mana HUDs, and the Health/Mana Bar HUDs below, unless the stat drops below the threshold below.");
                    b.slider("    Threshold (%)", 1f, 100f, () -> (float) c.admHealthManaThreshold, v -> c.admHealthManaThreshold = Math.round(v), 0)
                            .desc("Only show the Health/Mana HUDs once the stat drops below this percent of max.");
                    b.toggle("  Health Bar HUD", () -> c.admHealthBar, v -> c.admHealthBar = v)
                            .desc("Your health as a filled bar. Move it in /tmap gui.");
                    b.color("    Health Bar Color", () -> c.admHealthBarColor, v -> c.admHealthBarColor = v);
                    b.slider("    Health Bar Width", 20f, 200f, () -> (float) c.admHealthBarWidth, v -> c.admHealthBarWidth = Math.round(v / 5f) * 5, 0);
                    b.slider("    Health Bar Height", 2f, 30f, () -> (float) c.admHealthBarHeight, v -> c.admHealthBarHeight = Math.round(v), 0);
                    b.toggle("  Mana Bar HUD", () -> c.admManaBar, v -> c.admManaBar = v)
                            .desc("Your mana as a filled bar. Move it in /tmap gui.");
                    b.color("    Mana Bar Color", () -> c.admManaBarColor, v -> c.admManaBarColor = v);
                    b.slider("    Mana Bar Width", 20f, 200f, () -> (float) c.admManaBarWidth, v -> c.admManaBarWidth = Math.round(v / 5f) * 5, 0);
                    b.slider("    Mana Bar Height", 2f, 30f, () -> (float) c.admManaBarHeight, v -> c.admManaBarHeight = Math.round(v), 0);
                });
    }

    /** Raw action bar, from the setOverlayMessage hook. */
    public static void onActionBar(String raw) {
        String s = StringUtil.strip(raw);
        Matcher m = HEALTH.matcher(s);
        if (m.find()) { health = parse(m.group(1)); maxHealth = parse(m.group(2)); }
        m = MANA.matcher(s);
        if (m.find()) { mana = parse(m.group(1)); maxMana = parse(m.group(2)); }
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static boolean aboveThreshold(int current, int max) {
        return cfg().admHideHealthManaAbove && max > 0 && current / (float) max >= cfg().admHealthManaThreshold / 100f;
    }

    /** Our Player Stats text HUD {@code prefix} skipped this frame (OdinHuds' Hide Health/Mana Above %). */
    public static boolean hidesText(String prefix) {
        if (!cfg().admHealthMana || !cfg().admHideHealthManaAbove || !Adm.on()) return false;
        if ("stHealth".equals(prefix)) return aboveThreshold(health, maxHealth);
        if ("stMana".equals(prefix)) return aboveThreshold(mana, maxMana);
        return false;
    }

    private static void bar(GuiGraphicsExtractor g, boolean isHealth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !SkyblockUtils.inSkyblockCached()) return;
        int cur = isHealth ? health : mana, max = isHealth ? maxHealth : maxMana;
        if (max == 0 || aboveThreshold(cur, max)) return;
        HudEditElement e = isHealth ? healthHud : manaHud;
        draw(g, e.x(), e.y(), e.scale(), cur, max, isHealth);
    }

    private static void draw(GuiGraphicsExtractor g, int x, int y, float scale, int cur, int max, boolean isHealth) {
        TeslaMapsConfig c = cfg();
        int w = isHealth ? c.admHealthBarWidth : c.admManaBarWidth;
        int h = isHealth ? c.admHealthBarHeight : c.admManaBarHeight;
        int col = TeslaMapsConfig.parseColor(isHealth ? c.admHealthBarColor : c.admManaBarColor);
        float pct = max <= 0 ? 0f : Math.max(0f, Math.min(1f, cur / (float) max));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.fill(0, 0, w, h, 0x99000000);
        int filled = (int) (w * pct);
        if (pct > 0f) filled = Math.max(1, filled);
        g.fill(0, 0, filled, h, col);
        g.pose().popMatrix();
    }
}
