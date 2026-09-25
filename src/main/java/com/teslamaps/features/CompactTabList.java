package com.teslamaps.features;

// Ported from SkyHanni's features/misc/compacttablist (TabListReader, TabListRenderer, AdvancedPlayerList)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.PlayerTabOverlayAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompactTabList {

    private static final int MAX_LINES = 22;
    private static final int LINE_HEIGHT = 9;
    private static final int PADDING = 3;
    private static final int SPACING = 6;
    private static final int TITLE = 0, TEXT = 1, PLAYER = 2;
    private static final Pattern PLAYER_LINE = Pattern.compile("^\\[(\\d+)] (?:\\[\\w+] )?(\\w+)");

    private record Line(String text, int type, PlayerInfo info, int width) {}
    private record Column(List<Line> lines, int width) {}
    private record Layout(List<Column> columns, List<FormattedCharSequence> header, List<FormattedCharSequence> footer,
                          int[] headerW, int[] footerW) {}

    private static volatile Layout layout;
    private static boolean toggled, wasDown;
    private static int timer;

    private CompactTabList() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CompactTabList").when(() -> cfg().compactTabList)
                .tick(CompactTabList::tick)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Tab List");
                    b.toggle("Compact Tab List", () -> c.compactTabList, v -> c.compactTabList = v)
                            .desc("SkyBlock only: sections in columns, empty lines removed, one width per column. Fancy Tab HUD wins while it is on.")
                            .tags("tab", "tablist", "player list", "sba");
                    b.toggle("  Hide Adverts", () -> c.compactTabHideAds, v -> c.compactTabHideAds = v)
                            .desc("Drops the hypixel.net / store lines from header and footer.");
                    b.toggle("  Hide Fire Sales", () -> c.compactTabHideFireSales, v -> c.compactTabHideFireSales = v);
                    b.toggle("  Background", () -> c.compactTabBackground, v -> c.compactTabBackground = v);
                    b.toggle("  Tab Toggles", () -> c.compactTabToggle, v -> c.compactTabToggle = v)
                            .desc("Press Tab once to show, again to hide, instead of holding it.");
                    b.dropdown("  Player Sort", new String[]{"Rank", "Level", "Name"},
                            () -> c.compactTabSort, v -> c.compactTabSort = v);
                    b.toggle("    Reverse", () -> c.compactTabSortReverse, v -> c.compactTabSortReverse = v);
                    b.toggle("  Hide Player Heads", () -> c.compactTabHideHeads, v -> c.compactTabHideHeads = v);
                    b.toggle("  Hide SkyBlock Level", () -> c.compactTabHideLevel, v -> c.compactTabHideLevel = v);
                    b.toggle("  Hide Emblems", () -> c.compactTabHideEmblems, v -> c.compactTabHideEmblems = v)
                            .desc("Symbols behind the name. The ironman ♲ stays.");
                    b.toggle("  Hide Rank Color", () -> c.compactTabHideRankColor, v -> c.compactTabHideRankColor = v);
                    b.toggle("  Mark Party Members", () -> c.compactTabPartyIcon, v -> c.compactTabPartyIcon = v)
                            .desc("Puts a blue P behind players in your party.");
                });
    }

    private static boolean active() {
        return cfg().compactTabList && SkyblockUtils.isInSkyblock();
    }

    /** From TabToggleMixin: whether vanilla should draw the tab list this frame. */
    public static boolean tabShown(boolean keyDown) {
        if (!cfg().compactTabToggle || !active()) return keyDown;
        return toggled && Minecraft.getInstance().screen == null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.options.keyPlayerList.isDown();
        if (down && !wasDown && mc.screen == null) toggled = !toggled;
        wasDown = down;
        if (!SkyblockUtils.isInSkyblock()) { layout = null; return; }
        boolean visible = cfg().compactTabToggle ? toggled : down;
        if (!visible) { timer = 0; return; }
        if (timer++ % 5 != 0) return;
        layout = build(mc);
    }

    /** From CompactTabListMixin. True = drawn here, vanilla skipped. */
    public static boolean render(GuiGraphicsExtractor g, int screenWidth) {
        Layout l = layout;
        if (l == null || !active()) return false;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        TeslaMapsConfig c = cfg();

        int maxLines = 0, total = -SPACING;
        for (Column col : l.columns) {
            maxLines = Math.max(maxLines, col.lines.size());
            total += col.width + SPACING;
        }
        for (int w : l.headerW) total = Math.max(total, w);
        for (int w : l.footerW) total = Math.max(total, w);
        int headerH = l.header.size() * LINE_HEIGHT;
        int footerH = l.footer.size() * LINE_HEIGHT;
        int height = headerH + (headerH > 0 ? PADDING : 0) + maxLines * LINE_HEIGHT + (footerH > 0 ? PADDING + footerH : 0);

        int cx = screenWidth / 2;
        int x = cx - total / 2;
        int y = 10;
        if (c.compactTabBackground) g.fill(x - SPACING, y - PADDING, x + total + SPACING, y + height + PADDING, 0x80000000);

        int yy = y;
        for (int i = 0; i < l.header.size(); i++) {
            g.text(font, l.header.get(i), cx - l.headerW[i] / 2, yy, -1);
            yy += LINE_HEIGHT;
        }
        if (headerH > 0) yy += PADDING;

        int colX = cx - columnsWidth(l) / 2;
        for (Column col : l.columns) {
            int ly = yy;
            g.fill(colX - PADDING + 1, ly - PADDING + 1, colX + col.width + PADDING - 2,
                    ly + col.lines.size() * LINE_HEIGHT + PADDING - 2, c.compactTabBackground ? 0x20AAAAAA : 0x8F262626);
            for (Line line : col.lines) {
                int tx = colX;
                if (line.type == PLAYER && !c.compactTabHideHeads && line.info != null) {
                    PlayerFaceExtractor.extractRenderState(g, line.info.getSkin().body().texturePath(), tx, ly, 8,
                            line.info.showHat(), false, -1);
                    tx += 10;
                }
                if (line.type == TITLE) tx = colX + (col.width - line.width) / 2;
                g.text(font, line.text, tx, ly, -1);
                ly += LINE_HEIGHT;
            }
            colX += col.width + SPACING;
        }

        yy += maxLines * LINE_HEIGHT + PADDING;
        for (int i = 0; i < l.footer.size(); i++) {
            g.text(font, l.footer.get(i), cx - l.footerW[i] / 2, yy, -1);
            yy += LINE_HEIGHT;
        }
        return true;
    }

    private static int columnsWidth(Layout l) {
        int w = -SPACING;
        for (Column col : l.columns) w += col.width + SPACING;
        return w;
    }

    private static Layout build(Minecraft mc) {
        if (mc.getConnection() == null) return null;
        PlayerTabOverlay overlay = mc.gui.getTabList();
        List<PlayerInfo> infos = new ArrayList<>(mc.getConnection().getListedOnlinePlayers());
        infos.sort(PlayerTabOverlayAccessor.getOrdering());
        List<PlayerInfo> entries = new ArrayList<>();
        for (PlayerInfo p : infos) if (p.getProfile().name().startsWith("!")) entries.add(p);
        if (entries.isEmpty() || entries.size() % 20 != 0) return null;

        TeslaMapsConfig c = cfg();
        Font font = mc.font;

        List<String> titles = new ArrayList<>();
        List<List<Line>> columns = new ArrayList<>();
        for (int start = 0; start < entries.size(); start += 20) {
            String title = legacy(overlay.getNameForDisplay(entries.get(start))).trim();
            int idx = -1;
            for (int i = 0; i < titles.size(); i++) if (StringUtil.strip(titles.get(i)).equals(StringUtil.strip(title))) idx = i;
            if (idx < 0) {
                titles.add(title);
                columns.add(new ArrayList<>());
                idx = titles.size() - 1;
            }
            List<Line> col = columns.get(idx);
            for (int i = start + 1; i < start + 20; i++) {
                PlayerInfo info = entries.get(i);
                String text = legacy(overlay.getNameForDisplay(info));
                String plain = StringUtil.strip(text).trim();
                if (plain.isEmpty()) { col.add(null); continue; }
                boolean player = PLAYER_LINE.matcher(plain).find();
                col.add(new Line(text, player ? PLAYER : TEXT, info, 0));
            }
        }

        List<Column> render = new ArrayList<>();
        List<Line> cur = new ArrayList<>();
        for (int ci = 0; ci < columns.size(); ci++) {
            List<Line> col = columns.get(ci);
            if (StringUtil.strip(titles.get(ci)).startsWith("Players")) sortPlayers(col, c);
            List<List<Line>> sections = new ArrayList<>();
            List<Line> sec = null;
            for (Line line : col) {
                if (line == null) { sec = null; continue; }
                if (sec == null) sections.add(sec = new ArrayList<>());
                sec.add(line);
            }
            boolean needTitle = true;
            for (List<Line> s : sections) {
                if (c.compactTabHideFireSales && StringUtil.strip(s.get(0).text).trim().startsWith("Fire Sale")) continue;
                int size = s.size() + (needTitle ? 1 : 0);
                if (cur.size() >= MAX_LINES || size >= MAX_LINES / 2 && cur.size() + size > MAX_LINES) {
                    if (!cur.isEmpty()) render.add(column(cur, font));
                    cur = new ArrayList<>();
                } else if (!cur.isEmpty()) {
                    cur.add(new Line("", TEXT, null, 0));
                }
                if (needTitle) {
                    cur.add(new Line(titles.get(ci), TITLE, null, 0));
                    needTitle = false;
                }
                for (Line line : s) {
                    if (cur.size() >= MAX_LINES) {
                        render.add(column(cur, font));
                        cur = new ArrayList<>();
                    }
                    cur.add(line.type == PLAYER ? player(line, c) : line);
                }
            }
        }
        if (!cur.isEmpty()) render.add(column(cur, font));
        if (render.isEmpty()) return null;

        PlayerTabOverlayAccessor acc = (PlayerTabOverlayAccessor) overlay;
        List<FormattedCharSequence> header = new ArrayList<>(), footer = new ArrayList<>();
        int[] hw = split(acc.teslamaps$getHeader(), font, c.compactTabHideAds, header);
        int[] fw = split(acc.teslamaps$getFooter(), font, c.compactTabHideAds, footer);
        return new Layout(render, header, footer, hw, fw);
    }

    private static Column column(List<Line> lines, Font font) {
        List<Line> out = new ArrayList<>(lines.size());
        int w = 0;
        for (Line l : lines) {
            int lw = font.width(l.text) + (l.type == PLAYER && !cfg().compactTabHideHeads && l.info != null ? 10 : 0);
            out.add(new Line(l.text, l.type, l.info, lw));
            w = Math.max(w, lw);
        }
        return new Column(out, w);
    }

    private static int[] split(Component text, Font font, boolean hideAds, List<FormattedCharSequence> out) {
        List<Integer> widths = new ArrayList<>();
        if (text != null) {
            boolean lastBlank = true;
            for (FormattedText ft : font.getSplitter().splitLines(text, 400, Style.EMPTY)) {
                String s = ft.getString().trim();
                if (hideAds) {
                    String u = s.toUpperCase();
                    if (u.contains("HYPIXEL.NET") || u.contains("RANKS, BOOSTERS")) continue;
                }
                boolean blank = s.isEmpty();
                if (blank && lastBlank) continue;
                lastBlank = blank;
                FormattedCharSequence seq = Language.getInstance().getVisualOrder(ft);
                out.add(seq);
                widths.add(font.width(seq));
            }
            while (!out.isEmpty() && widths.get(widths.size() - 1) == 0) {
                out.remove(out.size() - 1);
                widths.remove(widths.size() - 1);
            }
        }
        int[] w = new int[widths.size()];
        for (int i = 0; i < w.length; i++) w[i] = widths.get(i);
        return w;
    }

    private static void sortPlayers(List<Line> col, TeslaMapsConfig c) {
        List<Integer> slots = new ArrayList<>();
        List<Line> players = new ArrayList<>();
        for (int i = 0; i < col.size(); i++) {
            Line l = col.get(i);
            if (l != null && l.type == PLAYER) { slots.add(i); players.add(l); }
        }
        if (players.size() < 2) return;
        Comparator<Line> cmp = switch (c.compactTabSort) {
            case "Level" -> Comparator.comparingInt(l -> -level(l));
            case "Name" -> Comparator.comparing(l -> name(l).toLowerCase().replace("_", ""));
            default -> null;
        };
        if (cmp != null) players.sort(cmp);
        if (c.compactTabSortReverse) java.util.Collections.reverse(players);
        for (int i = 0; i < slots.size(); i++) col.set(slots.get(i), players.get(i));
    }

    private static int level(Line l) {
        Matcher m = PLAYER_LINE.matcher(StringUtil.strip(l.text).trim());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String name(Line l) {
        Matcher m = PLAYER_LINE.matcher(StringUtil.strip(l.text).trim());
        return m.find() ? m.group(2) : "";
    }

    private static Line player(Line l, TeslaMapsConfig c) {
        if (!c.compactTabHideLevel && !c.compactTabHideEmblems && !c.compactTabHideRankColor && !c.compactTabPartyIcon) return l;
        String s = l.text;
        int close = s.indexOf(']');
        if (close < 0) return l;
        String levelPart = s.substring(0, Math.min(close + 1, s.length()));
        while (levelPart.startsWith(" ")) levelPart = levelPart.substring(1);
        String rest = s.substring(Math.min(close + 1, s.length())).trim();
        String name = name(l);
        int at = rest.indexOf(name);
        if (at < 0) return l;
        String namePart = rest.substring(0, at + name.length());
        String suffix = rest.substring(at + name.length());

        StringBuilder sb = new StringBuilder();
        if (!c.compactTabHideLevel) sb.append(levelPart).append("§r ");
        if (c.compactTabHideRankColor) {
            String plain = StringUtil.strip(namePart);
            sb.append("§b").append(plain.substring(plain.lastIndexOf(' ') + 1));
        } else {
            sb.append(namePart);
        }
        if (c.compactTabHideEmblems) {
            if (suffix.contains("♲")) sb.append(" §7♲");
        } else {
            sb.append(suffix);
        }
        if (c.compactTabPartyIcon && PartyTracker.inParty() && PartyTracker.isMember(name)) sb.append(" §9§lP");
        return new Line(sb.toString(), PLAYER, l.info, 0);
    }

    // Hypixel mixes literal § codes and styled siblings; flatten both into one legacy string
    private static String legacy(Component c) {
        StringBuilder sb = new StringBuilder();
        c.visit((style, part) -> {
            TextColor color = style.getColor();
            if (color != null) {
                ChatFormatting f = ChatFormatting.getByName(color.serialize());
                if (f != null) sb.append('§').append(f.getChar());
            }
            if (style.isBold()) sb.append("§l");
            sb.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }
}
