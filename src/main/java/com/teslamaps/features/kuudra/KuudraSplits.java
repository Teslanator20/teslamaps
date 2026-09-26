package com.teslamaps.features.kuudra;

// Segments: Odin's SplitsManager kuudraSplitsGroup / kuudraT5SplitsGroup, pace benchmarks: IQ's customSplits,
// lag line: Shizo's LagTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KuudraSplits {

    private record Seg(String name, Kuudra.Phase start, double bench) {}

    private static final Seg[] T5 = {
            new Seg("§2Supplies", Kuudra.Phase.SUPPLIES, 22.5), new Seg("§bBuild", Kuudra.Phase.BUILD, 12.0),
            new Seg("§dEaten", Kuudra.Phase.EATEN, 4.125), new Seg("§cStun", Kuudra.Phase.STUN, 0.0),
            new Seg("§4DPS", Kuudra.Phase.DPS, 3.5), new Seg("§4Cleared", Kuudra.Phase.SKIP, 6.9)};
    private static final Seg[] LOWER = {
            new Seg("§2Supplies", Kuudra.Phase.SUPPLIES, 0), new Seg("§bBuild", Kuudra.Phase.BUILD, 0),
            new Seg("§cDPS", Kuudra.Phase.DPS, 0), new Seg("§4Cleared", Kuudra.Phase.SKIP, 0)};
    private static final String[] SAMPLE = {"§6§lKuudra Splits §7(T5)", "§2Supplies§f: 22.45s", "§bBuild§f: 14.32s",
            "§dEaten§f: 5.21s", "§cStun§f: 0.53s", "§4DPS§f: 3.89s", "§4Cleared§f: 7.12s", "§eTotal§f: 53.52s",
            "§7Pace: §f54.10s", "§7Lag: §c0.85s"};

    private static final boolean[] announced = new boolean[T5.length];
    private static String[] lines;
    private static HudEditElement hud;

    private KuudraSplits() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kuudraSplits", "Kuudra Splits").section("Kuudra")
                .enabled(() -> cfg().kuudraSplits)
                .size(KuudraSplits::sampleWidth, () -> SAMPLE.length * 10)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Kuudra.LISTENERS.add(KuudraSplits::onPhase);
        Feature.of("KuudraSplits").section("Kuudra").when(() -> cfg().kuudraSplits)
                .tick(KuudraSplits::tick)
                .hud("kuudrasplits", KuudraSplits::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Splits");
                    b.toggle("Kuudra Splits", () -> c.kuudraSplits, v -> c.kuudraSplits = v)
                            .desc("Supplies > Build > (T5: Eaten > Stun) > DPS > Cleared > Total, PBs per tier. Move it in /tmap gui");
                    b.toggle("  Chat Message per Split", () -> c.kuudraSplitsChat, v -> c.kuudraSplitsChat = v)
                            .desc("\"Build took 12.34s\" with the difference to your PB");
                    b.toggle("  Pace (T5)", () -> c.kuudraSplitsPace, v -> c.kuudraSplitsPace = v)
                            .desc("Projected end time from benchmark seconds per phase");
                    b.toggle("  Lag Loss", () -> c.kuudraSplitsLag, v -> c.kuudraSplitsLag = v)
                            .desc("Real time minus server ticks: time lost to server lag");
                    b.toggle("  New PB Title", () -> c.kuudraSplitsPbTitle, v -> c.kuudraSplitsPbTitle = v);
                    KuudraSupplySplits.configRows(b);
                    b.button("Reset Kuudra PBs", () -> {
                        c.kuudraSplitPbs.clear();
                        TeslaMapsConfig.save();
                    });
                });
    }

    private static int sampleWidth() {
        int w = 0;
        for (String s : SAMPLE) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w;
    }

    private static Seg[] segs() {
        return Kuudra.tier() == 5 ? T5 : LOWER;
    }

    /** Segment end in ms, 0 = still running or not reached. */
    private static long end(Seg[] segs, int i) {
        for (int j = i + 1; j < segs.length; j++) {
            long s = Kuudra.startMs(segs[j].start);
            if (s != 0) return s;
        }
        return Kuudra.startMs(Kuudra.Phase.COMPLETED);
    }

    private static String pbKey(String name) {
        return "T" + Kuudra.tier() + ":" + com.teslamaps.utils.StringUtil.strip(name);
    }

    private static void onPhase(Kuudra.Phase prev, Kuudra.Phase now) {
        if (now == Kuudra.Phase.SUPPLIES || now == Kuudra.Phase.NONE) java.util.Arrays.fill(announced, false);
        TeslaMapsConfig c = cfg();
        if (!c.kuudraSplits || !c.section("Kuudra") || now == Kuudra.Phase.NONE || Kuudra.tier() == 0) return;
        Seg[] segs = segs();
        boolean done = now == Kuudra.Phase.COMPLETED && !Kuudra.failed();
        for (int i = 0; i < segs.length; i++) {
            long st = Kuudra.startMs(segs[i].start), en = end(segs, i);
            if (announced[i] || st == 0 || en == 0) continue;
            announced[i] = true;
            if (Kuudra.failed()) continue;
            long dur = en - st;
            String delta = pb(pbKey(segs[i].name), dur);
            if (c.kuudraSplitsChat) chat(segs[i].name + " §7took §f" + secs(dur) + delta);
        }
        if (!done) return;
        long total = Kuudra.startMs(Kuudra.Phase.COMPLETED) - Kuudra.runStartMs();
        if (Kuudra.runStartMs() == 0) return;
        String key = pbKey("Total");
        Long old = c.kuudraSplitPbs.get(key);
        String delta = pb(key, total);
        if (c.kuudraSplitsChat) chat("§eTotal §7took §f" + secs(total) + delta);
        if (c.kuudraSplitsLag) {
            long lag = lagMs();
            if (lag > 0) chat("§c" + secs(lag) + " §7lost to lag");
        }
        if (c.kuudraSplitsPbTitle && old != null && total < old) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setTimes(0, 60, 10);
            mc.gui.setTitle(Component.literal("§6§lNew Personal Best!"));
            mc.gui.setSubtitle(Component.literal("§7" + secs(old) + " §f> §a" + secs(total)));
        }
    }

    /** Stores a new PB, returns the " (+1.23s)" delta vs the old one. */
    private static String pb(String key, long dur) {
        TeslaMapsConfig c = cfg();
        Long old = c.kuudraSplitPbs.get(key);
        if (old == null || dur < old) {
            c.kuudraSplitPbs.put(key, dur);
            TeslaMapsConfig.save();
        }
        if (old == null) return "";
        long d = dur - old;
        return (d <= 0 ? " §a" : " §c") + String.format(Locale.ROOT, "(%+.2fs)", d / 1000.0);
    }

    private static long lagMs() {
        long start = Kuudra.runStartMs();
        if (start == 0 || ServerTickTimer.lastPingMs() == 0) return 0;
        long endMs = Kuudra.startMs(Kuudra.Phase.COMPLETED);
        if (endMs == 0) endMs = System.currentTimeMillis();
        long ticks = ServerTickTimer.pings() - Kuudra.startPings();
        if (ticks <= 0) return 0;
        return Math.max(0, endMs - start - ticks * 50);
    }

    private static void chat(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] " + text));
    }

    private static String secs(long ms) {
        return String.format(Locale.ROOT, "%.2fs", ms / 1000.0);
    }

    private static void tick() {
        Kuudra.Phase ph = Kuudra.phase();
        if (!Kuudra.inKuudra() || ph == Kuudra.Phase.NONE || Kuudra.runStartMs() == 0) {
            lines = null;
            return;
        }
        TeslaMapsConfig c = cfg();
        Seg[] segs = segs();
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        out.add("§6§lKuudra Splits" + (Kuudra.tier() > 0 ? " §7(T" + Kuudra.tier() + ")" : ""));
        double pace = 0;
        boolean running = ph.running();
        for (int i = 0; i < segs.length; i++) {
            long st = Kuudra.startMs(segs[i].start), en = end(segs, i);
            Long best = c.kuudraSplitPbs.get(pbKey(segs[i].name));
            String val;
            double sec;
            if (st == 0) {
                val = "§7-";
                sec = 0;
            } else if (en == 0) {
                sec = running ? (now - st) / 1000.0 : 0;
                val = running ? "§e" + String.format(Locale.ROOT, "%.2fs", sec) : "§7-";
            } else {
                sec = (en - st) / 1000.0;
                val = (best != null && en - st <= best ? "§a" : "§f") + String.format(Locale.ROOT, "%.2fs", sec);
            }
            pace += Math.max(sec, segs[i].bench);
            out.add(segs[i].name + "§f: " + val);
        }
        long endMs = Kuudra.startMs(Kuudra.Phase.COMPLETED);
        long total = (endMs != 0 ? endMs : running ? now : Kuudra.runStartMs()) - Kuudra.runStartMs();
        out.add("§eTotal§f: " + (Kuudra.failed() ? "§c" : "§f") + secs(total));
        if (c.kuudraSplitsPace && Kuudra.tier() == 5 && running) out.add("§7Pace: §f" + String.format(Locale.ROOT, "%.2fs", pace));
        if (c.kuudraSplitsLag) {
            long lag = lagMs();
            if (lag >= 50) out.add("§7Lag: §c" + secs(lag));
        }
        lines = out.toArray(new String[0]);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String[] l = lines;
        Minecraft mc = Minecraft.getInstance();
        if (l == null || hud == null || mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String[] l) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        for (int i = 0; i < l.length; i++) g.text(mc.font, l[i], 0, i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
