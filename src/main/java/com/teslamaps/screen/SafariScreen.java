package com.teslamaps.screen;

// Ported from critterMod's CritterScreen.java
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.safari.SafariData;
import com.teslamaps.safari.SafariHistory;
import com.teslamaps.safari.SafariTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SafariScreen extends Screen {

    private enum Tab { RUN("Run"), HISTORY("History"), STATS("Stats");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final int MIN_COLUMN = 70;
    private static final int LINE = 11;
    private static final int PAD = 10;
    private static final int HISTORY_ROWS = 12;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM  HH:mm");

    private static final int YOU = 0xFF55FF55;
    private static final int PARTY = 0xFF55FFFF;
    private static final int UNCAUGHT = 0xFF777777;
    private static final int HEADING = 0xFFFFAA00;
    private static final int LABEL = 0xFFBBBBBB;
    private static final int DIM = 0xFF888888;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TRACK = 0x50FFFFFF;

    private static Tab tab = Tab.RUN;

    private final Screen parent;
    private final SafariTracker.RunView run;
    private int column, left, top, panelW, panelH;
    private String feedback = "";

    public SafariScreen(Screen parent) {
        super(Component.literal("Critter Safari"));
        this.parent = parent;
        this.run = SafariTracker.view();
    }

    @Override
    protected void init() {
        int columns = SafariData.Biome.values().length;
        int widest = 0;
        for (SafariData.Critter c : SafariData.ALL) widest = Math.max(widest, font.width(c.name()));
        int measured = widest + 8 + font.width("000/00") + 8;
        column = Math.max(MIN_COLUMN, Math.min(measured, (width - PAD * 2 - 8) / columns));
        panelW = column * columns + PAD * 2;
        panelH = switch (tab) {
            case RUN -> 46 + 12 * LINE + ((run == null ? 0 : run.perPlayer().size()) + 2) * LINE + 40;
            case HISTORY -> 46 + (HISTORY_ROWS + 2) * LINE + 46;
            case STATS -> 46 + 15 * LINE + 46;
        };
        left = Math.max(4, (width - panelW) / 2);
        top = Math.max(28, (height - panelH) / 2);

        Tab[] tabs = Tab.values();
        int tabW = 60, gap = 4;
        int tx = left + (panelW - (tabW * tabs.length + gap * (tabs.length - 1))) / 2;
        for (Tab t : tabs) {
            TeslaButton b = new TeslaButton(tx, top - 24, tabW, 20, t.label, () -> { tab = t; rebuildWidgets(); });
            b.active = t != tab;
            addRenderableWidget(b);
            tx += tabW + gap;
        }

        int bw = 80, by = top + panelH - 30;
        int bx = left + (panelW - (bw * 3 + 12)) / 2;
        addRenderableWidget(new TeslaButton(bx, by, bw, 20, "Copy missing", this::copyMissing));
        addRenderableWidget(new TeslaButton(bx + bw + 6, by, bw, 20, "Share", this::share));
        addRenderableWidget(new TeslaButton(bx + (bw + 6) * 2, by, bw, 20, "Close", this::onClose));
        ScreenFit.clamp(this);
    }

    private void copyMissing() {
        Minecraft.getInstance().keyboardHandler.setClipboard(SafariTracker.missingLine());
        feedback = "Copied the missing list";
    }

    private void share() {
        com.teslamaps.features.Announce.party(SafariTracker.missingLine());
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(g, left, top, panelW, panelH, 8);
        int y = top + PAD;
        switch (tab) {
            case RUN -> {
                if (run == null) {
                    g.text(font, "No Critter Safari run tracked since launch.", left + PAD, y, LABEL);
                } else {
                    y = header(g, y);
                    y = columns(g, y + 4);
                    players(g, y + 6);
                }
            }
            case HISTORY -> history(g, y);
            case STATS -> stats(g, y);
        }
        if (!feedback.isEmpty()) g.centeredText(font, feedback, left + panelW / 2, top + panelH - 42, YOU);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private int header(GuiGraphicsExtractor g, int y) {
        int x = left + PAD;
        String heading = run.live() ? "Critter Safari  " + SafariHistory.time(run.elapsedMs()) : "Critter Safari  (last run)";
        g.text(font, heading, x, y, HEADING);
        String legend = "you  ·  party  ·  uncaught";
        g.text(font, legend, left + panelW - PAD - font.width(legend), y, DIM);
        y += LINE + 3;
        int total = SafariData.ALL.size();
        y = bar(g, x, y, "Party", run.dex(), total, run.dex() >= total ? YOU : WHITE);
        return bar(g, x, y, "You", run.own(), total, PARTY);
    }

    private int bar(GuiGraphicsExtractor g, int x, int y, String label, int cur, int max, int color) {
        g.text(font, label, x, y, LABEL);
        int bx = x + 40, bw = 150;
        g.fill(bx, y + 2, bx + bw, y + 7, TRACK);
        if (cur > 0) g.fill(bx, y + 2, bx + Math.max(1, bw * Math.min(cur, max) / max), y + 7, color);
        g.text(font, cur + "/" + max, bx + bw + 8, y, color);
        return y + LINE + 2;
    }

    private int columns(GuiGraphicsExtractor g, int y) {
        int bottom = y;
        SafariData.Biome[] biomes = SafariData.Biome.values();
        for (int i = 0; i < biomes.length; i++) {
            SafariData.Biome b = biomes[i];
            int x = left + PAD + i * column;
            int ry = y;
            int max = SafariData.countIn(b);
            g.text(font, b.label, x, ry, SafariData.argb(b));
            String count = run.inBiome(b) + "/" + max;
            g.text(font, count, x + column - 8 - font.width(count), ry, run.inBiome(b) >= max ? YOU : WHITE);
            ry += LINE + 2;
            for (SafariData.Critter c : SafariData.in(b)) {
                int color = !run.complete(c) ? UNCAUGHT : run.caughtSelf().contains(c.name()) ? YOU : PARTY;
                g.text(font, c.name(), x, ry, color);
                int caught = run.catches(c);
                int q = SafariData.quota(c);
                String note = q > 1 ? caught + "/" + q
                        : caught > 1 ? "x" + caught
                        : caught == 0 && run.attempts(c) > 0 ? run.attempts(c) + "t" : "";
                if (!note.isEmpty()) g.text(font, note, x + column - 8 - font.width(note), ry, DIM);
                ry += LINE;
            }
            bottom = Math.max(bottom, ry);
        }
        return bottom;
    }

    private void players(GuiGraphicsExtractor g, int y) {
        int x = left + PAD;
        g.fill(x, y, left + panelW - PAD, y + 1, 0x40FFFFFF);
        y += 5;
        Map<String, Map<SafariData.Biome, Integer>> per = run.perPlayer();
        if (per.isEmpty()) {
            g.text(font, "Nobody has caught anything yet.", x, y, DIM);
            return;
        }
        SafariData.Biome[] biomes = SafariData.Biome.values();
        int nameW = Math.min(96, panelW / 3);
        int cell = Math.max(28, (panelW - PAD * 2 - nameW) / biomes.length);
        g.text(font, "Unique per player", x, y, LABEL);
        for (int i = 0; i < biomes.length; i++) g.text(font, biomes[i].label, x + nameW + i * cell, y, SafariData.argb(biomes[i]));
        y += LINE + 2;
        for (Map.Entry<String, Map<SafariData.Biome, Integer>> e : per.entrySet()) {
            g.text(font, e.getKey(), x, y, e.getKey().equals(run.self()) ? YOU : WHITE);
            for (int i = 0; i < biomes.length; i++) {
                int v = e.getValue().getOrDefault(biomes[i], 0);
                g.text(font, String.valueOf(v), x + nameW + i * cell, y, v == 0 ? UNCAUGHT : LABEL);
            }
            y += LINE;
        }
    }

    private void history(GuiGraphicsExtractor g, int y) {
        int x = left + PAD, right = left + panelW - PAD;
        List<SafariHistory.Run> runs = SafariHistory.runs();
        g.text(font, "Saved runs", x, y, HEADING);
        String kept = runs.size() + " kept";
        g.text(font, kept, right - font.width(kept), y, DIM);
        y += LINE + 3;
        if (runs.isEmpty()) {
            g.text(font, "No runs saved yet. A run is saved when you leave the Safari.", x, y, LABEL);
            return;
        }
        int caps = 0, esc = 0, caught = 0;
        for (SafariHistory.Run r : runs) { caps += r.capsules; esc += r.escapes; caught += r.own; }
        g.text(font, runs.size() + " runs  ·  " + hours(SafariHistory.totalTime()) + " played  ·  best "
                + SafariHistory.bestDex() + "/" + SafariData.ALL.size() + "  ·  " + SafariHistory.fullDexRuns() + " full dex", x, y, LABEL);
        y += LINE + 5;

        int timeW = Math.min(120, panelW / 3);
        int cell = Math.max(40, (panelW - PAD * 2 - timeW) / 4);
        String[] head = {"Length", "Party", "You", "Capsules"};
        g.text(font, "When", x, y, LABEL);
        for (int i = 0; i < head.length; i++) g.text(font, head[i], x + timeW + cell * i, y, LABEL);
        y += LINE + 2;
        int total = SafariData.ALL.size();
        for (int i = runs.size() - 1, shown = 0; i >= 0 && shown < HISTORY_ROWS; i--, shown++) {
            SafariHistory.Run r = runs.get(i);
            g.text(font, when(r.startedAt), x, y, WHITE);
            g.text(font, SafariHistory.time(r.durationMs), x + timeW, y, DIM);
            g.text(font, r.dex + "/" + total, x + timeW + cell, y, r.dex >= total ? YOU : WHITE);
            g.text(font, r.own + "/" + total, x + timeW + cell * 2, y, PARTY);
            g.text(font, r.capsules + " (" + r.escapes + ")", x + timeW + cell * 3, y, DIM);
            y += LINE;
        }
        y += 2;
        g.fill(x, y, right, y + 1, 0x40FFFFFF);
        y += 3;
        g.text(font, "Total", x, y, LABEL);
        g.text(font, hours(SafariHistory.totalTime()), x + timeW, y, DIM);
        g.text(font, String.valueOf(caught), x + timeW + cell * 2, y, PARTY);
        g.text(font, caps + " (" + esc + ")", x + timeW + cell * 3, y, DIM);
    }

    private void stats(GuiGraphicsExtractor g, int y) {
        int x = left + PAD, right = left + panelW - PAD;
        List<SafariHistory.Run> runs = SafariHistory.runs();
        g.text(font, "Across saved runs", x, y, HEADING);
        String legend = "catches  ·  runs seen in";
        g.text(font, legend, right - font.width(legend), y, DIM);
        y += LINE + 3;
        if (runs.isEmpty()) {
            g.text(font, "Nothing saved yet.", x, y, LABEL);
            return;
        }
        int with = SafariHistory.runsWithBreakdown();
        g.text(font, runs.size() + " runs  ·  " + hours(SafariHistory.totalTime()) + " played  ·  "
                + SafariHistory.fullDexRuns() + " full dex", x, y, LABEL);
        y += LINE;
        g.text(font, with + " of " + runs.size() + " runs kept a per-critter breakdown", x, y, DIM);
        y += LINE + 5;

        SafariData.Biome[] biomes = SafariData.Biome.values();
        for (int i = 0; i < biomes.length; i++) {
            SafariData.Biome b = biomes[i];
            int cx = left + PAD + i * column;
            int ry = y;
            g.text(font, b.label, cx, ry, SafariData.argb(b));
            ry += LINE + 2;
            for (SafariData.Critter c : SafariData.in(b)) {
                int[] st = SafariHistory.statFor(c.name());
                g.text(font, c.name(), cx, ry, st[0] == 0 ? UNCAUGHT : WHITE);
                String note = st[0] == 0 ? "—" : st[0] + "/" + st[1];
                g.text(font, note, cx + column - 8 - font.width(note), ry, st[0] == 0 ? UNCAUGHT : DIM);
                ry += LINE;
            }
        }
    }

    private static String when(long ms) {
        return WHEN.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    private static String hours(long ms) {
        long m = ms / 60_000;
        return m < 60 ? m + "m" : (m / 60) + "h " + String.format("%02d", m % 60) + "m";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
