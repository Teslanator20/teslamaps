package com.teslamaps.features.hud;

// Skyblocker's utils/render/title/TitleContainer: every title stacks with its own expiry instead of overwriting
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class TitleStack {

    private static final int WIDTH = 260, MAX = 6;

    private static final class Entry {
        Component title, subtitle;
        String key;
        long start;
        int in, stay, out;

        long endMs() { return start + (in + stay + out) * 50L; }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static HudEditElement hud;
    private static Entry last;
    private static long lastAt;
    private static Component pendingSub;
    private static long pendingAt, blankAt;
    private static Object lastLevel;
    private static Component sampleTitle, sampleSub;

    private TitleStack() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean enabled() {
        return cfg().titleStack;
    }

    public static void register() {
        hud = HudEditElement.of("titleStack", "Title Stack")
                .enabled(TitleStack::enabled)
                .size(() -> WIDTH, () -> entryHeight(true))
                .preview(TitleStack::preview);
        Feature.of("TitleStack").when(TitleStack::enabled)
                .tickAlways(TitleStack::tick)
                .hudForeground("titlestack", TitleStack::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Title Stack");
                    b.toggle("Title Stack", () -> c.titleStack, v -> { c.titleStack = v; ENTRIES.clear(); })
                            .desc("Titles (server and TeslaMaps alerts) stack with their own timer instead of replacing each other, also over open inventories. Position in /tmap gui.");
                    b.toggle("  Stack Upwards", () -> c.titleStackUp, v -> c.titleStackUp = v);
                    b.toggle("  Clear On World Change", () -> c.titleStackClearOnWorld, v -> c.titleStackClearOnWorld = v);
                });
    }

    /** Gui.setTitle; true = handled here, vanilla must not show it. */
    public static boolean onTitle(Component title, int in, int stay, int out) {
        if (!enabled()) return false;
        long now = System.currentTimeMillis();
        Component sub = pendingSub != null && now - pendingAt < 100 ? pendingSub : null;
        pendingSub = null;
        boolean blank = title == null || title.getString().isBlank();
        if (blank && sub == null) {
            last = null;
            blankAt = now;
            return true;
        }
        add(blank ? null : title, sub, in, stay, out, now);
        return true;
    }

    private static void add(Component title, Component sub, int in, int stay, int out, long now) {
        String key = (title == null ? "" : title.getString()) + "|" + (sub == null ? "" : sub.getString());
        Entry e = null;
        for (Entry x : ENTRIES) if (x.key.equals(key) && now < x.endMs()) { e = x; break; }
        if (e == null) {
            e = new Entry();
            e.title = title;
            e.key = key;
            ENTRIES.add(e);
            while (ENTRIES.size() > MAX) ENTRIES.remove(0);
        }
        e.subtitle = sub;
        e.start = now;
        e.in = in;
        e.stay = stay;
        e.out = out;
        last = e;
        lastAt = now;
    }

    /** Gui.setSubtitle; true = handled here. */
    public static boolean onSubtitle(Component sub) {
        if (!enabled()) return false;
        long now = System.currentTimeMillis();
        Component s = sub == null || sub.getString().isBlank() ? null : sub;
        if (last != null && now - lastAt < 100) {
            if (last.title != null) last.subtitle = s;
        } else if (s != null && now - blankAt < 100) {
            add(null, s, 10, 70, 20, now);
        } else {
            pendingSub = s;
            pendingAt = now;
        }
        return true;
    }

    /** After Gui.setTimes: a title set in the same moment takes the new times. */
    public static void onTimes(int in, int stay, int out) {
        if (!enabled() || last == null || System.currentTimeMillis() - lastAt >= 100) return;
        last.in = in;
        last.stay = stay;
        last.out = out;
    }

    public static void clear() {
        ENTRIES.clear();
        last = null;
        pendingSub = null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (cfg().titleStackClearOnWorld) clear();
        }
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(e -> now >= e.endMs());
    }

    private static int entryHeight(boolean sub) {
        return 36 + (sub ? 22 : 0);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (ENTRIES.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        boolean up = cfg().titleStackUp;
        float s = hud.scale();
        g.pose().pushMatrix();
        g.pose().translate(hud.x(), hud.y());
        g.pose().scale(s, s);
        int y = up ? entryHeight(true) : 0;
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            long age = now - e.start, end = e.endMs();
            if (now >= end) continue;
            float a = 1f;
            if (e.in > 0 && age < e.in * 50L) a = age / (e.in * 50f);
            long left = end - now;
            if (e.out > 0 && left < e.out * 50L) a = Math.min(a, left / (e.out * 50f));
            int alpha = Math.max(8, Math.min(255, (int) (a * 255)));
            int h = (e.title == null ? 22 : entryHeight(e.subtitle != null)) + 4;
            int top = up ? y - h + 4 : y;
            draw(g, mc.font, top, e.title, e.subtitle, alpha << 24 | 0xFFFFFF);
            y += up ? -h : h;
        }
        g.pose().popMatrix();
    }

    private static void draw(GuiGraphicsExtractor g, Font font, int y, Component title, Component sub, int color) {
        g.pose().pushMatrix();
        g.pose().translate(WIDTH / 2f, y);
        if (title != null) {
            g.pose().pushMatrix();
            g.pose().scale(4f, 4f);
            g.text(font, title, -font.width(title) / 2, 0, color, true);
            g.pose().popMatrix();
        }
        if (sub != null) {
            if (title != null) g.pose().translate(0, 40);
            g.pose().scale(2f, 2f);
            g.text(font, sub, -font.width(sub) / 2, 0, color, true);
        }
        g.pose().popMatrix();
    }

    private static void preview(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s) {
        if (sampleTitle == null) {
            sampleTitle = Component.literal("§c§lBLOOD DONE");
            sampleSub = Component.literal("§7Title Stack");
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        draw(g, mc.font, 0, sampleTitle, sampleSub, 0xFFFFFFFF);
        g.pose().popMatrix();
    }
}
