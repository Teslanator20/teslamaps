package com.teslamaps.features;

// NoammAddons Scoreboard.kt / FloydAddons FloydCustomScoreboard: the sidebar redrawn in our panel style
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CustomScoreboard {

    private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final int ROW = 10;

    private static FormattedCharSequence title;
    private static List<FormattedCharSequence> rows = List.of();
    private static int width;
    private static List<FormattedCharSequence> sampleRows;
    private static FormattedCharSequence sampleTitle;
    private static int timer;
    private static HudEditElement hud;

    private CustomScoreboard() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        return cfg().customScoreboard;
    }

    public static void register() {
        hud = HudEditElement.of("customScoreboard", "Custom Scoreboard")
                .enabled(CustomScoreboard::on)
                .size(() -> rows.isEmpty() ? sampleWidth() : width, () -> height(rows.isEmpty() ? sample().size() : rows.size()))
                .preview((g, mc, x, y, s) -> {
                    if (rows.isEmpty()) draw(g, mc, x, y, s, sampleTitle(), sample(), sampleWidth());
                    else draw(g, mc, x, y, s, title, rows, width);
                });
        Feature.of("CustomScoreboard").when(CustomScoreboard::on)
                .tick(CustomScoreboard::tick)
                .hud("customscoreboard", CustomScoreboard::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Custom Scoreboard");
                    b.toggle("Custom Scoreboard", () -> c.customScoreboard, v -> { c.customScoreboard = v; timer = 0; })
                            .desc("Replaces the vanilla sidebar with a movable panel in your HUD style. Position in /tmap gui.");
                    b.toggle("  Show Title", () -> c.customScoreboardTitle, v -> c.customScoreboardTitle = v);
                    b.toggle("  Hide Server/Date Line", () -> c.customScoreboardHideServer, v -> c.customScoreboardHideServer = v);
                    b.toggle("  Hide www.hypixel.net", () -> c.customScoreboardHideFooter, v -> c.customScoreboardHideFooter = v);
                    b.toggle("  Hide Blank Lines", () -> c.customScoreboardHideBlank, v -> c.customScoreboardHideBlank = v);
                    b.toggle("  Panel Background", () -> c.customScoreboardBackground, v -> c.customScoreboardBackground = v);
                    b.toggle("Show Purse Instead Of Piggy", () -> c.scoreboardPiggyToPurse, v -> { c.scoreboardPiggyToPurse = v; ScoreboardText.clear(); })
                            .desc("Sidebar line \"Piggy:\" reads \"Purse:\" again. Vanilla and custom scoreboard.")
                            .tags("piggy bank", "purse", "sidebar");
                    b.toggle("Color Month Names", () -> c.scoreboardColorMonths, v -> { c.scoreboardColorMonths = v; ScoreboardText.clear(); })
                            .desc("SkyBlock date line colored by season: spring pink, summer gold, autumn yellow, winter blue.")
                            .tags("season", "date", "sidebar");
                });
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original ->
                (ctx, delta) -> { if (!on()) original.extractRenderState(ctx, delta); });
    }

    private static void tick() {
        if (++timer % 5 != 1) return;
        Minecraft mc = Minecraft.getInstance();
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            rows = List.of();
            title = null;
            return;
        }
        TeslaMapsConfig c = cfg();
        Font font = mc.font;
        List<PlayerScoreEntry> entries = new ArrayList<>();
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) if (!e.isHidden()) entries.add(e);
        entries.sort(ORDER);
        if (entries.size() > 15) entries = entries.subList(0, 15);

        List<FormattedCharSequence> out = new ArrayList<>(entries.size());
        int lastText = -1;
        int w = 0;
        boolean lastBlank = true;
        for (PlayerScoreEntry e : entries) {
            Component line = ScoreboardText.apply(PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName()));
            String plain = StringUtil.strip(line.getString()).trim();
            boolean blank = plain.isEmpty();
            if (blank && (c.customScoreboardHideBlank || lastBlank)) continue;
            if (c.customScoreboardHideServer && isServerLine(plain)) continue;
            if (c.customScoreboardHideFooter && plain.endsWith("hypixel.net")) continue;
            lastBlank = blank;
            FormattedCharSequence seq = line.getVisualOrderText();
            out.add(seq);
            if (!blank) lastText = out.size() - 1;
            w = Math.max(w, font.width(seq));
        }
        if (lastText + 1 < out.size()) out = out.subList(0, lastText + 1);

        if (c.customScoreboardTitle) {
            title = obj.getDisplayName().getVisualOrderText();
            w = Math.max(w, font.width(title));
        } else {
            title = null;
        }
        rows = out;
        width = w + 8;
    }

    // "09/25/26 m77CK" - date plus server id
    private static boolean isServerLine(String s) {
        return s.length() >= 8 && Character.isDigit(s.charAt(0)) && s.charAt(2) == '/' && s.charAt(5) == '/';
    }

    private static int height(int n) {
        return (n + (cfg().customScoreboardTitle ? 1 : 0)) * ROW + 6;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<FormattedCharSequence> r = rows;
        if (hud == null || (r.isEmpty() && title == null)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), title, r, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale,
                             FormattedCharSequence head, List<FormattedCharSequence> r, int w) {
        int h = (r.size() + (head != null ? 1 : 0)) * ROW + 6;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        if (cfg().customScoreboardBackground) HudStyle.panel(g, 0, 0, w, h);
        int ty = 4;
        if (head != null) {
            g.text(mc.font, head, (w - mc.font.width(head)) / 2, ty, 0xFFFFFFFF, true);
            ty += ROW;
        }
        for (FormattedCharSequence line : r) {
            g.text(mc.font, line, 4, ty, 0xFFFFFFFF, true);
            ty += ROW;
        }
        g.pose().popMatrix();
    }

    private static FormattedCharSequence sampleTitle() {
        if (!cfg().customScoreboardTitle) return null;
        if (sampleTitle == null) sampleTitle = Component.literal("§e§lSKYBLOCK").getVisualOrderText();
        return sampleTitle;
    }

    private static List<FormattedCharSequence> sample() {
        if (sampleRows == null) {
            List<FormattedCharSequence> l = new ArrayList<>();
            for (String s : new String[]{"§7 Late Summer 11th", "§7 10:40pm §b☽", "§7 ⏣ §bVillage", " ",
                    "§fPurse: §61,234,567", "§fBits: §b4,200"}) {
                l.add(Component.literal(s).getVisualOrderText());
            }
            sampleRows = l;
        }
        return sampleRows;
    }

    private static int sampleWidth() {
        Font font = Minecraft.getInstance().font;
        int w = 0;
        for (FormattedCharSequence s : sample()) w = Math.max(w, font.width(s));
        FormattedCharSequence t = sampleTitle();
        if (t != null) w = Math.max(w, font.width(t));
        return w + 8;
    }
}
