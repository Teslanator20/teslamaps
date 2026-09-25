package com.teslamaps.dungeon;

// Skyblocker DungeonPlayerWidget / DungeonBuffWidget / DungeonDownedWidget, Odin BlessingDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonInfoHud {

    private static final String SECTION = "Score & Splits";
    private static final int ROW = 10;
    private static final Pattern BLESSING = Pattern.compile("Blessing of (\\w+) ([IVXL]+|\\d+)");
    private static final List<String> SAMPLE = List.of(
            "§dHealer 42 §fSteve §8| §6Ult §aREADY §8| §5Rev §f1",
            "§bMage 45 §fAlex §8| §6Ult §e12s §8| §5Rev §f0",
            "§cDowned: §fNotch §7(8s)",
            "§cPower §aX §8| §dLife §aIV §8| §7Time §aV");

    private static volatile List<String> lines = List.of();
    private static int linesWidth;
    private static int tickCounter;
    private static HudEditElement el;
    private static volatile String footer = "";
    private static String parsedFooter = "";
    private static String blessingLine;

    private DungeonInfoHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("dungeonInfoHud", "Dungeon Info").ctx(HudEditElement.HudCtx.DUNGEON).section(SECTION)
                .enabled(() -> cfg().dungeonInfoHud)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * ROW)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("DungeonInfoHud").section(SECTION).when(() -> cfg().dungeonInfoHud)
                .tick(DungeonInfoHud::tick)
                .hud("dungeoninfohud", DungeonInfoHud::render)
                .config(SECTION, b -> {
                    b.label("Dungeon Info HUD");
                    b.toggle("Dungeon Info HUD", () -> cfg().dungeonInfoHud, v -> cfg().dungeonInfoHud = v)
                            .desc("Teammates with class, ult cooldown and revives, downed player and active blessings from tab");
                    b.toggle("  Teammates", () -> cfg().dungeonInfoPlayers, v -> cfg().dungeonInfoPlayers = v);
                    b.toggle("  Downed", () -> cfg().dungeonInfoDowned, v -> cfg().dungeonInfoDowned = v);
                    b.toggle("  Blessings", () -> cfg().dungeonInfoBlessings, v -> cfg().dungeonInfoBlessings = v);
                    b.toggle("  Hide in Boss", () -> cfg().dungeonInfoHideInBoss, v -> cfg().dungeonInfoHideInBoss = v);
                    b.info("Puzzles: Puzzle HUD in Puzzles & Terminals");
                });
    }

    /** Main thread, from the tab list packet. */
    public static void onTabFooter(String text) {
        footer = text;
    }

    private static void tick() {
        if (++tickCounter % 5 != 0) return;
        TeslaMapsConfig c = cfg();
        if (!DungeonManager.isInDungeon() || (c.dungeonInfoHideInBoss && DungeonManager.isInBossArea())) {
            lines = List.of();
            return;
        }
        List<String> out = new ArrayList<>();
        if (c.dungeonInfoPlayers) {
            for (int p = 0; p < 5; p++) {
                int start = 1 + p * 4;
                Matcher m = PlayerTracker.getPlayerFromTab(p + 1);
                if (m == null) continue;
                String cls = m.group("class");
                String lvl = m.group("level");
                String col = classColor(cls);
                StringBuilder sb = new StringBuilder(col).append(cls);
                if (lvl != null) sb.append(' ').append(lvl);
                sb.append(" §f").append(m.group("name"));
                String ult = after(PlayerTracker.strAt(start + 1));
                if (ult != null) sb.append(" §8| §6Ult ").append(ult.equalsIgnoreCase("READY") ? "§a" : "§e").append(ult);
                String rev = after(PlayerTracker.strAt(start + 2));
                if (rev != null) sb.append(" §8| §5Rev §f").append(rev);
                out.add(sb.toString());
            }
        }
        if (c.dungeonInfoDowned) {
            String down = after(PlayerTracker.strAt(21));
            if (down != null && !down.equalsIgnoreCase("NONE")) {
                String time = after(PlayerTracker.strAt(22));
                out.add("§cDowned: §f" + down + (time != null ? " §7(" + time + ")" : ""));
            }
        }
        if (c.dungeonInfoBlessings) {
            String f = footer;
            if (!f.equals(parsedFooter)) {
                parsedFooter = f;
                blessingLine = blessings(f);
            }
            if (blessingLine != null) out.add(blessingLine);
        }
        linesWidth = width(out);
        lines = out;
    }

    private static String blessings(String f) {
        Matcher m = BLESSING.matcher(StringUtil.strip(f));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) sb.append(" §8| ");
            sb.append(blessingColor(m.group(1))).append(m.group(1)).append(" §a").append(m.group(2));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String blessingColor(String b) {
        return switch (b) {
            case "Power" -> "§c";
            case "Life" -> "§d";
            case "Stone" -> "§a";
            case "Wisdom" -> "§b";
            case "Time" -> "§7";
            default -> "§f";
        };
    }

    static String classColor(String cls) {
        if (cls == null) return "§f";
        return switch (cls) {
            case "Healer" -> "§d";
            case "Mage" -> "§b";
            case "Berserk" -> "§c";
            case "Archer" -> "§6";
            case "Tank" -> "§a";
            default -> "§7";
        };
    }

    private static String after(String s) {
        if (s == null) return null;
        int i = s.indexOf(": ");
        return i < 0 ? null : s.substring(i + 2).trim();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l, linesWidth);
    }

    private static int width(List<String> l) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : l) w = Math.max(w, font.width(s));
        return w;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * ROW + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * ROW, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
