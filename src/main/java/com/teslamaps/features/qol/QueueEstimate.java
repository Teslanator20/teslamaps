package com.teslamaps.features.qol;

// SkyOcean QueueEstimate: linear regression over "You are #n in the queue!" subtitles
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.GuiAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueueEstimate {

    private static final Pattern QUEUE = Pattern.compile("You are #(\\d+) in the queue!");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<String> SAMPLE = List.of("§dQueue Estimate", "§7Position: §d#42",
            "§7Estimate: §d18:42:10", "§7Time Left: §d3m 20s");

    private record Point(long ms, int pos) {}

    private static final List<Point> history = new ArrayList<>();
    private static String lastTitle;
    private static long estimate = -1;
    private static List<String> lines = List.of();
    private static int timer, cachedWidth;
    private static HudEditElement hud;

    private QueueEstimate() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("queueEstimate", "Queue Estimate")
                .enabled(() -> cfg().queueEstimate)
                .position(QueueEstimate::x, QueueEstimate::y)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("QueueEstimate").when(() -> cfg().queueEstimate)
                .tick(QueueEstimate::tick)
                .hud("queueestimate", QueueEstimate::render)
                .config("QoL & Misc", b -> b.toggle("Queue Estimate", () -> cfg().queueEstimate, v -> cfg().queueEstimate = v)
                        .desc("Reads \"You are #n in the queue!\" and estimates when you get in. Position in /tmap gui."));
    }

    private static int x() {
        int v = cfg().queueEstimateX;
        return v >= 0 ? v : (Minecraft.getInstance().getWindow().getGuiScaledWidth() - width(SAMPLE)) / 2;
    }

    private static int y() {
        int v = cfg().queueEstimateY;
        return v >= 0 ? v : Minecraft.getInstance().getWindow().getGuiScaledHeight() / 3;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Component sub = ((GuiAccessor) mc.gui.hud).teslamaps$subtitle();
        if (sub == null) {
            if (lastTitle != null) {
                lastTitle = null;
                history.clear();
                estimate = -1;
                lines = List.of();
            }
            return;
        }
        String title = StringUtil.stripTrim(sub.getString());
        if (!title.equals(lastTitle)) {
            lastTitle = title;
            Matcher m = QUEUE.matcher(title);
            if (m.find()) {
                history.add(new Point(System.currentTimeMillis(), Integer.parseInt(m.group(1))));
                estimate = predict();
            } else {
                history.clear();
                estimate = -1;
            }
            timer = 0;
        }
        if (timer++ % 10 == 0) build();
    }

    private static void build() {
        if (history.isEmpty()) {
            lines = List.of();
            return;
        }
        List<String> out = new ArrayList<>(5);
        out.add("§dQueue Estimate");
        out.add("§7Position: §d#" + history.get(history.size() - 1).pos);
        if (estimate < 0) {
            out.add("§7Collecting data...");
        } else {
            out.add("§7Estimate: §d" + LocalTime.ofInstant(Instant.ofEpochMilli(estimate), ZoneId.systemDefault()).format(HM));
            long left = Math.max(0, (estimate - System.currentTimeMillis()) / 1000);
            out.add("§7Time Left: §d" + (left >= 60 ? left / 60 + "m " : "") + left % 60 + "s");
            if (history.size() < 5) out.add("§cUnder 5 data points, may be off");
        }
        lines = out;
        cachedWidth = width(out);
    }

    private static long predict() {
        int n = history.size();
        if (n < 2) return -1;
        long start = history.get(0).ms;
        double ax = 0, ay = 0;
        for (Point p : history) {
            ax += (p.ms - start) / 1000.0;
            ay += p.pos;
        }
        ax /= n;
        ay /= n;
        double above = 0, below = 0;
        for (Point p : history) {
            double dx = (p.ms - start) / 1000.0 - ax;
            above += dx * (p.pos - ay);
            below += dx * dx;
        }
        if (below == 0) return -1;
        double slope = above / below;
        if (slope >= 0) return -1;
        double toZero = -(ay - slope * ax) / slope;
        return start + (long) (toZero * 1000);
    }

    private static int width(List<String> ls) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        return w + 8;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls) {
        int w = ls == lines ? cachedWidth : width(ls);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, ls.size() * 10 + 6);
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
