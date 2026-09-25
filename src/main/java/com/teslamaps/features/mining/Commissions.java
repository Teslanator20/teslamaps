package com.teslamaps.features.mining;

// After Bax's CommissionManager/CommissionDisplay and Skyblocker's CommsWidget
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Commissions off the tab widget; shared by the HUD, the waypoints and the mob highlight. */
public final class Commissions {

    public record Commission(String name, float progress, boolean done) {}

    private static final String HEADER = "Commissions:";
    private static final Pattern ENTRY = Pattern.compile("^(.+?):\\s*(?:([\\d.]+)%|(DONE))$");
    private static final String COMPLETE = "Commission Complete!";
    private static final int BAR_W = 40, LINE = 10;

    private static List<Commission> list = List.of();
    private static int version;
    private static final Set<String> chatDone = new HashSet<>();
    private static final Set<String> alerted = new HashSet<>();
    private static Object lastLevel;
    private static int ticks;
    private static boolean seen;
    private static HudEditElement hud;
    private static int width = 120;

    private static final List<Commission> SAMPLE = List.of(
            new Commission("Mithril Miner", 0.23f, false),
            new Commission("Goblin Slayer", 0.71f, false),
            new Commission("Lava Springs Titanium", 1f, true));

    private Commissions() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static List<Commission> active() { return list; }
    public static int version() { return version; }

    public static boolean anyDone() {
        for (Commission c : list) if (c.done) return true;
        return false;
    }

    private static boolean wanted() {
        TeslaMapsConfig c = cfg();
        return c.commissionHud || c.commissionWaypoints || c.commissionMobHighlight
                || c.dwarvenOverlay && c.dwarvenOverlayCommissions;
    }

    public static void register() {
        hud = HudEditElement.of("commissionHud", "Commissions").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().commissionHud)
                .size(() -> size(Minecraft.getInstance().font, SAMPLE)[0], () -> size(Minecraft.getInstance().font, SAMPLE)[1])
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("Commissions").section("Mining").when(Commissions::wanted)
                .tick(Commissions::tick)
                .serverChat(Commissions::onChat);
        Feature.of("CommissionHud").section("Mining").when(() -> cfg().commissionHud)
                .hud("commissionhud", Commissions::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Commissions");
                    b.toggle("Commission HUD", () -> c.commissionHud, v -> c.commissionHud = v)
                            .desc("Your commissions with a progress bar, read off the Commissions tab widget. Move it in /tmap gui.")
                            .tags("commission", "dwarven", "glacite", "hollows", "king");
                    b.toggle("  Progress Bars", () -> c.commissionHudBars, v -> c.commissionHudBars = v);
                    b.toggle("  Hide Finished", () -> c.commissionHudHideDone, v -> c.commissionHudHideDone = v);
                    b.toggle("  Completion Alert", () -> c.commissionAlert, v -> c.commissionAlert = v)
                            .desc("Title and sound as soon as a commission is done");
                    b.soundDropdown("    Alert Sound", SoundOptions.keys(), () -> c.commissionAlertSound, v -> c.commissionAlertSound = v);
                    b.info("Needs the Commissions tab widget: /tab");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            chatDone.clear();
            alerted.clear();
            seen = false;
            if (!list.isEmpty()) { list = List.of(); version++; }
        }
        if (++ticks % 10 != 0) return;
        if (!Mining.onMiningIsland()) {
            if (!list.isEmpty()) { list = List.of(); version++; }
            return;
        }
        List<Commission> next = new ArrayList<>(4);
        boolean in = false;
        for (String raw : TabListUtils.getOrderedLines()) {
            String line = StringUtil.stripTrim(raw);
            if (!in) {
                if (line.equals(HEADER)) in = true;
                continue;
            }
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) break;
            String name = m.group(1).trim();
            boolean done = m.group(3) != null || chatDone.contains(name.toLowerCase(Locale.ROOT));
            float p = done ? 1f : parse(m.group(2));
            next.add(new Commission(name, p, done));
        }
        for (Commission c : next) {
            String k = c.name.toLowerCase(Locale.ROOT);
            if (!c.done) {
                alerted.remove(k);
                chatDone.remove(k);
            } else if (seen && !alerted.contains(k)) {
                alert(c.name);
            } else {
                alerted.add(k);
            }
        }
        if (!next.isEmpty()) seen = true;
        if (!next.equals(list)) {
            list = next;
            version++;
            width = size(mc.font, next)[0];
        }
    }

    private static float parse(String s) {
        try { return Math.min(1f, Float.parseFloat(s) / 100f); } catch (Exception e) { return 0f; }
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        int i = t.indexOf(COMPLETE);
        if (i <= 0 || !Mining.onMiningIsland()) return;
        String name = t.substring(0, i).trim();
        List<Commission> next = new ArrayList<>(list.size());
        String hit = null;
        for (Commission c : list) {
            if (!c.done && c.name.equalsIgnoreCase(name)) {
                hit = c.name;
                next.add(new Commission(c.name, 1f, true));
            } else next.add(c);
        }
        String key = (hit != null ? hit : name).toLowerCase(Locale.ROOT);
        chatDone.add(key);
        if (hit != null) {
            list = next;
            version++;
        }
        if (!alerted.contains(key)) alert(hit != null ? hit : StringUtil.titleCase(name));
    }

    private static void alert(String name) {
        alerted.add(name.toLowerCase(Locale.ROOT));
        if (!cfg().commissionHud || !cfg().commissionAlert) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.setTitle(Component.literal("§a§lCommission Complete!"));
        mc.gui.setSubtitle(Component.literal("§e" + name));
        mc.gui.setTimes(5, 40, 10);
        LoudSound.play(SoundOptions.resolve(cfg().commissionAlertSound), 1f, 1f);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || list.isEmpty() || !Mining.onMiningIsland()) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), list);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Commission> rows) {
        TeslaMapsConfig c = cfg();
        boolean bars = c.commissionHudBars;
        boolean hideDone = c.commissionHudHideDone;
        int n = 0;
        for (Commission r : rows) if (!hideDone || !r.done) n++;
        if (n == 0) return;
        int w = rows == list ? width : size(mc.font, rows)[0];
        int pad = HudStyle.padding();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -pad, -pad, w + pad * 2, (n + 1) * LINE - 1 + pad * 2);
        g.text(mc.font, "§3§lCommissions", 0, 0, 0xFFFFFFFF);
        int row = 1;
        int nameW = w - (bars ? BAR_W + 4 : 0);
        for (Commission r : rows) {
            if (hideDone && r.done) continue;
            int ty = row * LINE;
            int col = color(r);
            String pct = r.done ? "DONE" : Math.round(r.progress * 100) + "%";
            g.text(mc.font, "§f" + r.name, 0, ty, 0xFFFFFFFF);
            if (bars) {
                int bx = nameW + 4;
                g.fill(bx, ty + 1, bx + BAR_W, ty + 8, 0xC0101010);
                int fw = Math.round((BAR_W - 2) * Math.min(1f, r.progress));
                if (fw > 0) g.fill(bx + 1, ty + 2, bx + 1 + fw, ty + 7, col);
                int tw = mc.font.width(pct);
                g.text(mc.font, pct, bx + (BAR_W - tw) / 2, ty, 0xFFFFFFFF);
            } else {
                g.text(mc.font, pct, nameW - mc.font.width(pct), ty, col);
            }
            row++;
        }
        pose.popMatrix();
    }

    private static int color(Commission r) {
        if (r.done || r.progress >= 0.75f) return 0xFF55FF55;
        if (r.progress >= 0.4f) return 0xFFFFFF55;
        return 0xFFFF5555;
    }

    private static int[] size(Font font, List<Commission> rows) {
        int w = font.width("§3§lCommissions");
        boolean bars = cfg().commissionHudBars;
        int n = 0;
        for (Commission r : rows) {
            n++;
            int rw = font.width(r.name) + 4 + (bars ? BAR_W : font.width("100%"));
            w = Math.max(w, rw);
        }
        return new int[]{w, (n + 1) * LINE};
    }
}
