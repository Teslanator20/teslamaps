package com.teslamaps.screen;

// After NEU's GuiPriceGraph.kt, data from Coflnet's price history API
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class PriceGraphScreen extends Screen {

    private static final String[] SPANS = {"day", "week", "month", "full"};
    private static final String[] SPAN_LABELS = {"1 Day", "1 Week", "1 Month", "All"};
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final int W = 340, H = 210, PAD = 30;
    private static final int COLOR_A = 0xFF55FF55, COLOR_B = 0xFFFFAA00, COLOR_AVG = 0x90AAAAFF;

    private record Point(long time, double a, double b, double volume) {}

    private final Screen parent;
    private final String id;
    private final String name;
    private final boolean bazaar;
    private int span = 1;
    private volatile List<Point> points;
    private volatile String error;
    private int requestId;

    private int px, py, gx, gy, gw, gh;
    private double min, max;
    private int[] yA, yB, yAvg;
    private List<Point> laidOut;
    private int hoverCol = -1;
    private List<Component> hoverTip = List.of();

    public PriceGraphScreen(Screen parent, String id, String name) {
        super(Component.literal("Price History"));
        this.parent = parent;
        this.id = id;
        this.name = name;
        this.bazaar = PriceManager.hasBazaar(id);
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        gx = px + PAD + 14;
        gy = py + 34;
        gw = W - PAD - 24;
        gh = H - 70;
        int bx = px + 10;
        for (int i = 0; i < SPANS.length; i++) {
            int idx = i;
            addRenderableWidget(new TeslaButton(bx, py + H - 24, 52, 16, SPAN_LABELS[i], () -> select(idx)));
            bx += 56;
        }
        addRenderableWidget(new TeslaButton(px + W - 94, py + H - 24, 84, 16, "Open Website", this::openWebsite));
        laidOut = null;
        if (points == null && error == null) fetch();
        ScreenFit.clamp(this);
    }

    private void select(int idx) {
        if (idx == span) return;
        span = idx;
        points = null;
        error = null;
        laidOut = null;
        fetch();
    }

    public static String websiteUrl(String id) {
        if (PriceManager.hasBazaar(id)) return "https://www.skyblock.bz/product/" + id;
        return "https://sky.coflnet.com/item/" + id;
    }

    private void openWebsite() {
        try {
            net.minecraft.util.Util.getPlatform().openUri(websiteUrl(id));
        } catch (Exception ignored) {
        }
    }

    private void fetch() {
        int req = ++requestId;
        String url = bazaar
                ? "https://sky.coflnet.com/api/bazaar/" + id + "/history/" + SPANS[span]
                : "https://sky.coflnet.com/api/item/price/" + id + "/history/" + SPANS[span];
        CompletableFuture.runAsync(() -> {
            List<Point> out = new ArrayList<>();
            String err = null;
            try {
                HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "TeslaMaps").GET().build();
                HttpResponse<String> resp = CLIENT.send(r, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode());
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    String ts = bazaar ? str(o, "timestamp") : str(o, "time");
                    if (ts == null) continue;
                    long t = parseTime(ts);
                    if (bazaar) {
                        out.add(new Point(t, num(o, "buy"), num(o, "sell"), num(o, "buyVolume") + num(o, "sellVolume")));
                    } else {
                        out.add(new Point(t, num(o, "avg"), num(o, "min"), num(o, "volume")));
                    }
                }
                out.sort((x, y) -> Long.compare(x.time(), y.time()));
                if (out.isEmpty()) err = "No price history for this item";
            } catch (Exception e) {
                err = "Could not load the price history (" + e.getClass().getSimpleName() + ")";
            }
            String fe = err;
            Minecraft.getInstance().execute(() -> {
                if (req != requestId) return;
                error = fe;
                points = fe == null ? out : null;
                laidOut = null;
            });
        });
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static double num(JsonObject o, String k) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseTime(String s) {
        try {
            if (s.endsWith("Z")) return Instant.parse(s).getEpochSecond();
            String t = s.length() > 19 ? s.substring(0, 19) : s;
            return LocalDateTime.parse(t).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            return 0;
        }
    }

    private void layout(List<Point> p) {
        laidOut = p;
        hoverCol = -1;
        min = Double.MAX_VALUE;
        max = -Double.MAX_VALUE;
        for (Point q : p) {
            if (q.a() > 0) { min = Math.min(min, q.a()); max = Math.max(max, q.a()); }
            if (q.b() > 0) { min = Math.min(min, q.b()); max = Math.max(max, q.b()); }
        }
        if (min == Double.MAX_VALUE) { min = 0; max = 1; }
        if (max - min < 1e-6) { max = min + 1; }
        double padV = (max - min) * 0.05;
        min = Math.max(0, min - padV);
        max += padV;
        yA = new int[gw];
        yB = new int[gw];
        yAvg = new int[gw];
        int window = Math.max(3, p.size() / 12);
        double[] avg = new double[p.size()];
        double sum = 0;
        for (int i = 0; i < p.size(); i++) {
            sum += p.get(i).a();
            if (i >= window) sum -= p.get(i - window).a();
            avg[i] = sum / Math.min(i + 1, window);
        }
        long t0 = p.getFirst().time(), t1 = Math.max(t0 + 1, p.getLast().time());
        int j = 0;
        for (int x = 0; x < gw; x++) {
            long t = t0 + (long) ((t1 - t0) * (x / (double) Math.max(1, gw - 1)));
            while (j < p.size() - 2 && p.get(j + 1).time() < t) j++;
            Point a = p.get(j), b = p.get(Math.min(p.size() - 1, j + 1));
            double f = b.time() == a.time() ? 0 : Math.max(0, Math.min(1, (t - a.time()) / (double) (b.time() - a.time())));
            yA[x] = y(lerp(a.a(), b.a(), f));
            yB[x] = y(lerp(a.b(), b.b(), f));
            yAvg[x] = y(lerp(avg[j], avg[Math.min(p.size() - 1, j + 1)], f));
        }
    }

    private static double lerp(double a, double b, double f) {
        if (a <= 0) return b;
        if (b <= 0) return a;
        return a + (b - a) * f;
    }

    private int y(double v) {
        if (v <= 0) return -1;
        return gy + gh - (int) Math.round((v - min) / (max - min) * gh);
    }

    private void line(GuiGraphicsExtractor g, int[] ys, int color) {
        int prev = -1;
        for (int x = 0; x < ys.length; x++) {
            int y = ys[x];
            if (y < 0) { prev = -1; continue; }
            int top = prev < 0 ? y : Math.min(prev, y), bot = prev < 0 ? y : Math.max(prev, y);
            g.fill(gx + x, top, gx + x + 1, bot + 1, color);
            prev = y;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        HudStyle.screenPanel(g, px, py, W, H, 8);
        g.text(font, "§6Price History §7- " + name, px + 10, py + 8, GuiStyle.text());
        String legend = bazaar ? "§aInstant Buy  §6Instant Sell  §9Average" : "§aAverage  §6Lowest  §9Moving Avg";
        g.text(font, legend, px + 10, py + 19, GuiStyle.text());
        List<Point> p = points;
        if (p == null) {
            String msg = error != null ? "§c" + error : "§7Loading...";
            g.centeredText(font, msg, px + W / 2, gy + gh / 2, GuiStyle.text());
        } else {
            if (laidOut != p) layout(p);
            g.fill(gx - 1, gy, gx, gy + gh + 1, 0xFF888888);
            g.fill(gx - 1, gy + gh, gx + gw, gy + gh + 1, 0xFF888888);
            g.text(font, "§7" + EstimatedValue.fmt(max), px + 6, gy - 4, GuiStyle.text());
            g.text(font, "§7" + EstimatedValue.fmt(min), px + 6, gy + gh - 4, GuiStyle.text());
            line(g, yAvg, COLOR_AVG);
            line(g, yB, COLOR_B);
            line(g, yA, COLOR_A);
            int col = mouseX - gx;
            if (col >= 0 && col < gw && mouseY >= gy && mouseY <= gy + gh) {
                g.fill(gx + col, gy, gx + col + 1, gy + gh, 0x60FFFFFF);
                if (col != hoverCol) {
                    hoverCol = col;
                    hoverTip = tip(p, col);
                }
                g.setComponentTooltipForNextFrame(font, hoverTip, mouseX, mouseY);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private List<Component> tip(List<Point> p, int col) {
        long t0 = p.getFirst().time(), t1 = Math.max(t0 + 1, p.getLast().time());
        long t = t0 + (long) ((t1 - t0) * (col / (double) Math.max(1, gw - 1)));
        Point best = p.getFirst();
        for (Point q : p) if (Math.abs(q.time() - t) < Math.abs(best.time() - t)) best = q;
        List<Component> out = new ArrayList<>(4);
        out.add(Component.literal("§7" + HOUR.format(Instant.ofEpochSecond(best.time()).atZone(ZoneId.systemDefault()))));
        out.add(Component.literal((bazaar ? "§aBuy: §6" : "§aAverage: §6") + EstimatedValue.exact(best.a())));
        out.add(Component.literal((bazaar ? "§6Sell: §6" : "§6Lowest: §6") + EstimatedValue.exact(best.b())));
        out.add(Component.literal("§7Volume: §f" + String.format(Locale.ROOT, "%,d", (long) best.volume())));
        return out;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
