package com.teslamaps.slayer;

// After Skyblocker's SlayerBossBar.java (HP / max table) and Devonian's SlayerDisplay.kt (timer + spawned by)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerBossHud {

    private static final Pattern HP = Pattern.compile("(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?[kKmMbB]?)❤");
    private static final Pattern TIME = Pattern.compile("^\\d{1,2}:\\d{2}$");

    private static final int[] REV = {500, 20_000, 400_000, 1_500_000, 10_000_000};
    private static final int[] TARA = {750, 30_000, 900_000, 2_400_000, 10_000_000};
    private static final int[] SVEN = {2_000, 40_000, 750_000, 2_000_000};
    private static final int[] VOID = {300_000, 12_000_000, 50_000_000, 210_000_000};
    private static final int[] BLAZE = {2_500_000, 10_000_000, 45_000_000, 150_000_000};
    private static final int[] VAMP = {625, 1_100, 1_800, 2_400, 3_000};

    private static final Component S_NAME = Component.literal("§c☠ §bVoidgloom Seraph IV §e64.2M§c❤");
    private static final String S_TIME = "§c02:59";
    private static final String S_OWNER = "§eSpawned by: §bPlayer";

    private static Component name;
    private static String time;
    private static String owner;
    private static float progress;
    private static boolean hasHp;
    private static int width;
    private static ArmorStand lastTag;
    private static double maxHp;
    private static int ticks;
    private static HudEditElement hud;

    private SlayerBossHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("slayerBossHud", "Slayer Boss HUD").section("Combat")
                .enabled(() -> cfg().slayerBossHud)
                .size(() -> sampleWidth(), () -> height(true, true))
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, S_NAME, 0.64f, S_TIME, S_OWNER, sampleWidth()));

        Feature.of("SlayerBossHud").section("Combat").when(() -> cfg().slayerBossHud)
                .tick(SlayerBossHud::tick)
                .hud("slayerbosshud", SlayerBossHud::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Slayer Boss HUD", () -> c.slayerBossHud, v -> c.slayerBossHud = v)
                            .desc("Movable HUD with the tracked slayer boss: name + HP, quest timer and \"Spawned by\". Move it in /tmap gui.")
                            .tags("slayer", "boss", "hp", "bossbar", "timer");
                    b.toggle("  HP Bar", () -> c.slayerBossHudBar, v -> c.slayerBossHudBar = v)
                            .desc("Bar = current HP / max HP of that tier; red while no HP is readable (hit phase)");
                });
    }

    private static int sampleWidth() {
        return Minecraft.getInstance().font.width(S_NAME) + 8;
    }

    private static int height(boolean t, boolean o) {
        int lines = 1 + (t ? 1 : 0) + (o ? 1 : 0);
        return lines * 10 + (cfg().slayerBossHudBar ? 6 : 0) + 4;
    }

    private static void tick() {
        if (++ticks % 2 != 0) return;
        ArmorStand tag = SlayerHUD.bossTag();
        if (tag == null) {
            name = null;
            lastTag = null;
            return;
        }
        Component n = tag.getName();
        String raw = StringUtil.strip(n.getString());
        if (tag != lastTag) {
            lastTag = tag;
            maxHp = 0;
        }
        Matcher m = HP.matcher(raw);
        hasHp = m.find();
        if (hasHp) {
            double hp = parse(m.group(1));
            if (maxHp <= 0) maxHp = Math.max(tableMax(raw, SlayerHUD.tagTier(raw)), hp);
            if (hp > maxHp) maxHp = hp;
            progress = maxHp <= 0 ? 1f : (float) Math.max(0, Math.min(1, hp / maxHp));
        } else progress = 1f;

        String t = null, o = null;
        for (ArmorStand s : NametagMobs.namedStands(tag, 1.5)) {
            if (s == tag) continue;
            String sn = s.getName().getString();
            String clean = StringUtil.stripTrim(sn);
            if (t == null && TIME.matcher(clean).matches()) t = sn;
            else if (o == null && clean.startsWith("Spawned by")) o = sn;
        }
        time = t;
        owner = o;
        name = n;
        var font = Minecraft.getInstance().font;
        int w = font.width(n);
        if (t != null) w = Math.max(w, font.width(t));
        if (o != null) w = Math.max(w, font.width(o));
        width = w + 8;
    }

    private static double tableMax(String name, int tier) {
        if (name.contains("Conjoined Brood")) return 20_000_000;
        int[] table = name.contains("Revenant") || name.contains("Atoned") ? REV
                : name.contains("Tarantula") ? TARA
                : name.contains("Sven") ? SVEN
                : name.contains("Voidgloom") ? VOID
                : name.contains("Demonlord") ? BLAZE
                : name.contains("Bloodfiend") ? VAMP : null;
        if (table == null || tier < 1) return 0;
        return table[Math.min(tier, table.length) - 1];
    }

    private static double parse(String s) {
        String v = s.replace(",", "");
        char last = Character.toLowerCase(v.charAt(v.length() - 1));
        double mul = last == 'k' ? 1e3 : last == 'm' ? 1e6 : last == 'b' ? 1e9 : 1;
        if (mul > 1) v = v.substring(0, v.length() - 1);
        try {
            return Double.parseDouble(v) * mul;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Component n = name;
        if (n == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), n, hasHp ? progress : -1f, time, owner, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, Component n,
                             float prog, String t, String o, int w) {
        boolean bar = cfg().slayerBossHudBar;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, height(t != null, o != null));
        int cy = 3;
        g.text(mc.font, n, 4, cy, 0xFFFFFFFF, true);
        cy += 10;
        if (bar) {
            int bw = w - 8;
            g.fill(4, cy, 4 + bw, cy + 4, 0xFF303030);
            if (prog < 0) g.fill(4, cy, 4 + bw, cy + 4, 0xFFFF3030);
            else g.fill(4, cy, 4 + Math.round(bw * prog), cy + 4, 0xFFFF55FF);
            cy += 6;
        }
        if (t != null) {
            g.text(mc.font, t, 4, cy, 0xFFFFFFFF, true);
            cy += 10;
        }
        if (o != null) g.text(mc.font, o, 4, cy, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
