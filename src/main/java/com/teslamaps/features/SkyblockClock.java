package com.teslamaps.features;

// Lobby code / date / time elements from SkyHanni's ScoreboardElementLobbyCode/Date/Time
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SkyblockClock {

    private static final long SB_EPOCH = 1560275700L;
    private static final List<String> SAMPLE = List.of("§709/25/26 §8m77CK", "§fLate Summer 11th", "§710:40pm §b☽");

    private static List<String> lines = List.of();
    private static int width, timer;
    private static HudEditElement hud;

    private SkyblockClock() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("sbClock", "SkyBlock Clock")
                .enabled(() -> cfg().sbClock)
                .size(() -> panelWidth(SAMPLE), () -> panelHeight(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("SkyblockClock").when(() -> cfg().sbClock)
                .tick(SkyblockClock::tick)
                .hudHiddenInInventory("sbclock", SkyblockClock::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("SkyBlock Clock");
                    b.toggle("SkyBlock Clock", () -> c.sbClock, v -> c.sbClock = v)
                            .desc("Small HUD with the lobby code, SkyBlock date and SkyBlock time. Position in /tmap gui.");
                    b.toggle("  Lobby Code", () -> c.sbClockLobby, v -> c.sbClockLobby = v);
                    b.toggle("    With Real Date", () -> c.sbClockLobbyDate, v -> c.sbClockLobbyDate = v);
                    b.toggle("  SkyBlock Date", () -> c.sbClockDate, v -> c.sbClockDate = v);
                    b.toggle("    With Year", () -> c.sbClockYear, v -> c.sbClockYear = v);
                    b.toggle("  SkyBlock Time", () -> c.sbClockTime, v -> c.sbClockTime = v);
                    b.toggle("    24h Format", () -> c.sbClock24h, v -> c.sbClock24h = v);
                    b.toggle("    Exact Minutes", () -> c.sbClockExactMinutes, v -> c.sbClockExactMinutes = v);
                    b.toggle("    Day/Night Symbol", () -> c.sbClockSymbol, v -> c.sbClockSymbol = v);
                });
    }

    private static void tick() {
        if (++timer % 5 != 0) return;
        TeslaMapsConfig c = cfg();
        if (!SkyblockUtils.isInSkyblock()) {
            lines = List.of();
            return;
        }
        String date = null, codes = null, symbol = null;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String l = ScoreboardUtils.cleanLine(raw);
            if (codes == null && l.length() > 9 && l.charAt(2) == '/' && l.charAt(5) == '/' && l.charAt(8) == ' '
                    && Character.isDigit(l.charAt(0))) {
                date = l.substring(0, 8);
                Set<String> set = new LinkedHashSet<>();
                for (String p : l.substring(9).trim().split("\\s+")) if (!p.isEmpty()) set.add(p);
                codes = String.join(" ", set);
            } else if (symbol == null && (l.endsWith("☽") || l.endsWith("☀") || l.endsWith("⚡") || l.endsWith("☔"))
                    && (l.contains("am") || l.contains("pm"))) {
                symbol = l.substring(l.length() - 1);
            }
        }

        long sec = System.currentTimeMillis() / 1000L - SB_EPOCH;
        long dayIndex = sec / 1200;
        int month = (int) (dayIndex / 31 % 12) + 1;
        int day = (int) (dayIndex % 31) + 1;
        long daySec = sec % 1200;
        int hour = (int) (daySec / 50);
        int minute = (int) (daySec % 50 * 60 / 50);

        List<String> out = new ArrayList<>(3);
        if (c.sbClockLobby && codes != null && !codes.isEmpty()) {
            out.add(c.sbClockLobbyDate && date != null ? "§7" + date + " §8" + codes : "§8" + codes);
        }
        if (c.sbClockDate) out.add("§f" + monthName(month) + " " + day + suffix(day)
                + (c.sbClockYear ? "§7, Year " + (sec / 446400 + 1) : ""));
        if (c.sbClockTime) {
            if (!c.sbClockExactMinutes) minute = minute / 10 * 10;
            String mm = (minute < 10 ? "0" : "") + minute;
            String time;
            if (c.sbClock24h) {
                time = (hour < 10 ? "0" : "") + hour + ":" + mm;
            } else {
                int h12 = hour % 12 == 0 ? 12 : hour % 12;
                time = h12 + ":" + mm + (hour < 12 ? "am" : "pm");
            }
            if (c.sbClockSymbol) {
                String sym = symbol != null ? symbol : (hour >= 6 && hour < 19 ? "☀" : "☽");
                String col = switch (sym) {
                    case "☀" -> "§e";
                    case "☽" -> "§b";
                    default -> "§9";
                };
                time += " " + col + sym;
            }
            out.add("§7" + time);
        }
        lines = out;
        width = panelWidth(out);
    }

    private static String monthName(int month) {
        String prefix = switch ((month - 1) % 3) {
            case 0 -> "Early ";
            case 2 -> "Late ";
            default -> "";
        };
        String name = switch ((month - 1) / 3) {
            case 0 -> "Spring";
            case 1 -> "Summer";
            case 2 -> "Autumn";
            default -> "Winter";
        };
        return prefix + name;
    }

    private static String suffix(int n) {
        if (n >= 11 && n <= 13) return "th";
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private static int panelWidth(List<String> ls) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        return w + 8;
    }

    private static int panelHeight(int n) {
        return n * 10 + 6;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls) {
        int w = ls == SAMPLE ? panelWidth(SAMPLE) : width;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, panelHeight(ls.size()));
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
