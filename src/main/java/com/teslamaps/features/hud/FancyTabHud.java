package com.teslamaps.features.hud;

// After Skyblocker's tabhud (TabHud / WidgetManager / TabHudWidget): the SkyBlock tab list drawn as widget panels
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FancyTabHud {

    private static final Comparator<PlayerInfo> TAB_ORDER =
            Comparator.comparingInt((PlayerInfo p) -> -p.getTabListOrder())
                    .thenComparingInt(p -> p.getGameMode() == GameType.SPECTATOR ? 1 : 0)
                    .thenComparing(p -> p.getTeam() == null ? "" : p.getTeam().getName())
                    .thenComparing(p -> p.getProfile().name(), String::compareToIgnoreCase);
    private static final int ROW = 10, PAD = 4, GAP = 4, COLUMN = 20;

    private static final class Widget {
        FormattedCharSequence title;
        int titleW;
        final List<FormattedCharSequence> rows = new ArrayList<>();
        final List<Float> bars = new ArrayList<>();
        int w, h, x, y;
    }

    private static List<Widget> widgets = List.of();
    private static int totalW, totalH;
    private static boolean inSkyblock;
    private static int timer;

    private FancyTabHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FancyTabHud").when(() -> cfg().fancyTabHud)
                .tick(FancyTabHud::tick)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Fancy Tab List");
                    b.toggle("Fancy Tab HUD", () -> c.fancyTabHud, v -> c.fancyTabHud = v)
                            .desc("On SkyBlock the tab list becomes panels, one per tab section (Area, Skills, Pet, ...).");
                    b.slider("  Scale", 0.5f, 2f, () -> c.fancyTabScale, v -> c.fancyTabScale = v, 2);
                    b.toggle("  Player List", () -> c.fancyTabPlayers, v -> c.fancyTabPlayers = v);
                    b.toggle("  Progress Bars", () -> c.fancyTabBars, v -> c.fancyTabBars = v)
                            .desc("Thin bar under lines ending in a percentage");
                    b.keybind("  Hold For Vanilla Tab", () -> c.fancyTabVanillaKey, v -> c.fancyTabVanillaKey = v);
                });
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, original ->
                (ctx, delta) -> { if (!replacing()) original.extractRenderState(ctx, delta); else render(ctx); });
    }

    private static boolean replacing() {
        TeslaMapsConfig c = cfg();
        if (!c.fancyTabHud || !inSkyblock || widgets.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyPlayerList.isDown()) return false;
        return c.fancyTabVanillaKey == -1 || !KeyNames.isDown(c.fancyTabVanillaKey);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean held = mc.options.keyPlayerList.isDown();
        if (++timer % (held ? 5 : 40) != 0) return;
        inSkyblock = SkyblockUtils.isInSkyblock();
        if (!inSkyblock || mc.getConnection() == null) {
            widgets = List.of();
            return;
        }
        List<PlayerInfo> entries = new ArrayList<>(mc.getConnection().getListedOnlinePlayers());
        entries.sort(TAB_ORDER);
        TeslaMapsConfig c = cfg();
        Font font = mc.font;
        List<Widget> out = new ArrayList<>();
        for (int col = 0; col < entries.size(); col += COLUMN) {
            List<Component> lines = new ArrayList<>(COLUMN);
            for (int i = col; i < Math.min(entries.size(), col + COLUMN); i++) {
                Component name = entries.get(i).getTabListDisplayName();
                lines.add(name == null ? Component.empty() : name);
            }
            parseColumn(lines, out, c, font);
        }
        layout(out, mc);
        widgets = out;
    }

    private static void parseColumn(List<Component> lines, List<Widget> out, TeslaMapsConfig c, Font font) {
        int i = 0;
        while (i < lines.size()) {
            String plain = StringUtil.strip(lines.get(i).getString());
            if (plain.isBlank()) { i++; continue; }
            int end = i;
            while (end < lines.size() && !StringUtil.strip(lines.get(end).getString()).isBlank()) end++;
            boolean players = plain.startsWith("Players (") || plain.startsWith("Party (");
            if (plain.trim().equals("Info") && end - i == 1) { i = end; continue; }
            if (players && !c.fancyTabPlayers) { i = end; continue; }
            Widget w = new Widget();
            int from = i;
            boolean titled = players || (end - i > 1 && !plain.startsWith(" ")
                    && StringUtil.strip(lines.get(i + 1).getString()).startsWith(" "));
            if (titled) {
                w.title = lines.get(i).getVisualOrderText();
                w.titleW = font.width(w.title);
                from++;
            }
            int width = w.titleW;
            for (int j = from; j < end; j++) {
                Component line = lines.get(j);
                String p = StringUtil.strip(line.getString());
                if (p.trim().equals("Info")) continue;
                FormattedCharSequence seq = line.getVisualOrderText();
                w.rows.add(seq);
                w.bars.add(c.fancyTabBars ? percent(p) : -1f);
                width = Math.max(width, font.width(seq));
            }
            if (w.rows.isEmpty() && w.title == null) { i = end; continue; }
            w.w = width + PAD * 2;
            int n = w.rows.size() + (w.title != null ? 1 : 0);
            w.h = n * ROW + PAD * 2 - 2;
            out.add(w);
            i = end;
        }
    }

    private static float percent(String p) {
        String t = p.trim();
        if (!t.endsWith("%")) return -1f;
        int s = t.length() - 1;
        while (s > 0 && (Character.isDigit(t.charAt(s - 1)) || t.charAt(s - 1) == '.')) s--;
        if (s == t.length() - 1) return -1f;
        try {
            return Math.max(0f, Math.min(1f, Float.parseFloat(t.substring(s, t.length() - 1)) / 100f));
        } catch (NumberFormatException e) {
            return -1f;
        }
    }

    private static void layout(List<Widget> ws, Minecraft mc) {
        float scale = Math.max(0.25f, cfg().fancyTabScale);
        int maxH = (int) ((mc.getWindow().getGuiScaledHeight() - 20) / scale);
        int x = 0, y = 0, colW = 0;
        totalW = 0;
        totalH = 0;
        for (Widget w : ws) {
            if (y > 0 && y + w.h > maxH) {
                x += colW + GAP;
                y = 0;
                colW = 0;
            }
            w.x = x;
            w.y = y;
            y += w.h + GAP;
            colW = Math.max(colW, w.w);
            totalH = Math.max(totalH, y - GAP);
        }
        totalW = x + colW;
    }

    /** For MovableVanillaHud: the fancy panels replace the tab list, so its box follows them. */
    static boolean shown() {
        return cfg().fancyTabHud && inSkyblock && !widgets.isEmpty();
    }

    static int left() {
        return (int) ((Minecraft.getInstance().getWindow().getGuiScaledWidth() - totalW * Math.max(0.25f, cfg().fancyTabScale)) / 2);
    }

    static int width() {
        return (int) Math.ceil(totalW * Math.max(0.25f, cfg().fancyTabScale));
    }

    static int height() {
        return (int) Math.ceil(totalH * Math.max(0.25f, cfg().fancyTabScale)) + 1;
    }

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        List<Widget> ws = widgets;
        float scale = Math.max(0.25f, cfg().fancyTabScale);
        int ox = left();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ox, 10);
        pose.scale(scale, scale);
        for (Widget w : ws) {
            HudStyle.panel(g, w.x, w.y, w.w, w.h);
            int ty = w.y + PAD;
            if (w.title != null) {
                g.text(mc.font, w.title, w.x + (w.w - w.titleW) / 2, ty, 0xFFFFFFFF, true);
                ty += ROW;
            }
            for (int i = 0; i < w.rows.size(); i++) {
                g.text(mc.font, w.rows.get(i), w.x + PAD, ty, 0xFFFFFFFF, true);
                float bar = w.bars.get(i);
                if (bar >= 0) {
                    int bw = w.w - PAD * 2;
                    g.fill(w.x + PAD, ty + 8, w.x + PAD + bw, ty + 9, 0x60000000);
                    g.fill(w.x + PAD, ty + 8, w.x + PAD + Math.round(bw * bar), ty + 9, 0xFF55FF55);
                }
                ty += ROW;
            }
        }
        pose.popMatrix();
    }
}
