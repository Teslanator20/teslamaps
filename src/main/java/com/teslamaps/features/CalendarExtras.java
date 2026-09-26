package com.teslamaps.features;

// Ported from SkyHanni's MinisterInCalendar.kt / TravelingZooPetInCalendar.kt and Skysoft's MinisterCalendarTooltip.kt
import com.teslamaps.api.ElectionApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CalendarExtras {

    private static final String MAIN = "Calendar and Events";
    private static final Pattern SEASON = Pattern.compile("^((?:Early |Late )?(?:Spring|Summer|Autumn|Winter)), Year (\\d+)$");
    private static final Pattern STARTS_IN = Pattern.compile("Starts in: ((?:\\d+[dhms] ?)+)");
    private static final Pattern TIME_PART = Pattern.compile("(\\d+)([dhms])");
    private static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
            "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};
    private static final String[] ZOO_PETS = {"Lion", "Monkey", "Elephant", "Giraffe", "Blue Whale", "Tiger"};
    private static final long SB_EPOCH = 1560275700000L, YEAR_MS = 446_400_000L, MONTH_MS = YEAR_MS / 12;

    private static Feature feature;
    private static Screen lastScreen;
    private static boolean main;
    private static int pageYear = -1, pageMonth = -1;
    private static String ministerKey = "";
    private static List<Component> ministerLines = List.of();

    private CalendarExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CalendarExtras").section("Inventory").when(() -> cfg().calendarExtras)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Calendar");
                    b.toggle("Calendar Extras", () -> c.calendarExtras, v -> c.calendarExtras = v)
                            .desc("Extra info in the Calendar and Events menu.");
                    b.toggle("  Minister In Calendar", () -> c.calendarMinister, v -> c.calendarMinister = v)
                            .desc("Hovering the mayor also shows the current minister and their perk.");
                    b.toggle("  Traveling Zoo Pet", () -> c.calendarZooPet, v -> c.calendarZooPet = v)
                            .desc("Traveling Zoo entries show which legendary pet Oringo brings.");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !feature.active()) return;
            try { tooltip(stack, lines); } catch (RuntimeException ignored) {}
        });
    }

    public static boolean wantsElection() {
        TeslaMapsConfig c = cfg();
        return c.calendarExtras && c.calendarMinister;
    }

    private static void updateScreen() {
        Screen s = Minecraft.getInstance().screen;
        if (s == lastScreen) return;
        lastScreen = s;
        main = false;
        pageYear = pageMonth = -1;
        if (!(s instanceof AbstractContainerScreen<?> cs)) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (title.equals(MAIN)) { main = true; return; }
        Matcher m = SEASON.matcher(title);
        if (!m.matches()) return;
        pageMonth = monthIndex(m.group(1));
        try { pageYear = Integer.parseInt(m.group(2)); } catch (NumberFormatException e) { pageYear = -1; }
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        if (stack.isEmpty() || lines.isEmpty()) return;
        updateScreen();
        if (!main && pageYear < 0) return;
        TeslaMapsConfig c = cfg();
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        if (main && c.calendarMinister && name.startsWith("Mayor ")) {
            List<Component> ml = minister();
            if (!ml.isEmpty()) {
                lines.add(Component.empty());
                lines.addAll(ml);
            }
        }
        if (!c.calendarZooPet) return;
        if (main) {
            if (!name.equals("Traveling Zoo")) return;
            long at = System.currentTimeMillis();
            for (Component l : lines) {
                Matcher m = STARTS_IN.matcher(StringUtil.stripTrim(l.getString()));
                if (m.find()) { at += duration(m.group(1)); break; }
            }
            long el = at - SB_EPOCH;
            int year = (int) (el / YEAR_MS) + 1;
            int month = (int) ((el % YEAR_MS) / MONTH_MS);
            addZoo(lines, year, month);
        } else if (name.startsWith("Day ")) {
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).getString().contains("Traveling Zoo")) {
                    addZoo(lines, pageYear, pageMonth);
                    return;
                }
            }
        }
    }

    private static void addZoo(List<Component> lines, int year, int month) {
        if (month < 0) return;
        lines.add(Component.empty());
        lines.add(Component.literal("§7Pet available: §6" + zooPet(year, month)));
    }

    /** Legendary pet of the Traveling Zoo in that year (1-based) and month (0-based). */
    public static String zooPet(int year, int month) {
        return ZOO_PETS[Math.floorMod(year * 2 + (month < 6 ? 0 : 1), ZOO_PETS.length)];
    }

    private static List<Component> minister() {
        ElectionApi.Person p = ElectionApi.ministerFull();
        if (p == null || p.name().isEmpty()) return List.of();
        String key = p.name() + "|" + p.perks().size();
        if (key.equals(ministerKey)) return ministerLines;
        List<Component> out = new ArrayList<>();
        out.add(Component.literal("§dMinister " + p.name()));
        Minecraft mc = Minecraft.getInstance();
        for (ElectionApi.Perk k : p.perks()) {
            out.add(Component.literal("§e" + k.name()));
            if (k.description().isEmpty()) continue;
            for (FormattedCharSequence seq : mc.font.split(Component.literal("§7" + k.description()), 170)) {
                StringBuilder sb = new StringBuilder("§7");
                seq.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
                out.add(Component.literal(sb.toString()));
            }
        }
        ministerKey = key;
        ministerLines = List.copyOf(out);
        return ministerLines;
    }

    private static int monthIndex(String s) {
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(s)) return i;
        return -1;
    }

    private static long duration(String s) {
        long ms = 0;
        Matcher m = TIME_PART.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return ms;
    }
}
