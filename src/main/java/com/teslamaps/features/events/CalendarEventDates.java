package com.teslamaps.features.events;

// After Skyblocker's DateCalculatorTooltip.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Calendar menus: the real local start time under every countdown, the real date on every day. */
public final class CalendarEventDates {

    private static final Pattern TIMER = Pattern.compile("(?:(\\d+)d\\b)? ?(?:(\\d+)h\\b)? ?(?:(\\d+)m\\b)? ?(?:(\\d+)s\\b)?");
    private static final Pattern MONTH_TITLE = Pattern.compile("(.+), Year (\\d+)");
    private static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
            "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};
    private static final long SB_EPOCH = 1560275700000L, YEAR_MS = 446_400_000L, MONTH_MS = 37_200_000L, DAY_MS = 1_200_000L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault()).withLocale(Locale.ENGLISH);

    private static ItemStack lastStack;
    private static String lastTitle;
    private static List<int[]> lastAt = List.of();
    private static List<String> lastText = List.of();

    private CalendarEventDates() {}

    public static void register() {
        FeatureRegistry.category("Events", "Skyblock", true);
        Feature feature = Feature.of("CalendarEventDates").section("Events").when(() -> TeslaMapsConfig.get().calendarEventDates)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Calendar");
                    b.toggle("Calendar Real Dates", () -> c.calendarEventDates, v -> c.calendarEventDates = v)
                            .desc("Calendar and Events: the local start time under every \"Starts in\" line. "
                                    + "Month views: the real date of every day.")
                            .tags("calendar", "event", "date", "time", "starts in");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (TeslaMapsConfig.get().calendarEventDates && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (stack.isEmpty() || mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        boolean events = title.equals("Calendar and Events");
        Matcher mt = events ? null : MONTH_TITLE.matcher(title);
        if (!events && !mt.matches()) return;
        if (inPlayerInv(mc.player.getInventory(), stack)) return;
        if (stack != lastStack || !title.equals(lastTitle)) {
            lastStack = stack;
            lastTitle = title;
            compute(stack, lines, events, mt);
        }
        for (int i = lastAt.size() - 1; i >= 0; i--) {
            int at = lastAt.get(i)[0];
            if (at > lines.size()) continue;
            lines.add(at, Component.literal(lastText.get(i)).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        }
    }

    private static boolean inPlayerInv(Inventory inv, ItemStack stack) {
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i) == stack) return true;
        return false;
    }

    private static void compute(ItemStack stack, List<Component> lines, boolean events, Matcher mt) {
        List<int[]> at = new ArrayList<>();
        List<String> text = new ArrayList<>();
        if (events) {
            for (int i = 1; i < lines.size(); i++) {
                String l = StringUtil.strip(lines.get(i).getString());
                if (!l.contains("Starts in:") && !l.contains(" (")) continue;
                Instant when = parse(l);
                if (when == null) continue;
                at.add(new int[]{i + 1});
                text.add(FMT.format(when));
            }
        } else {
            int month = -1;
            for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(mt.group(1).trim())) month = i;
            int day = stack.getCount();
            if (month >= 0 && day <= 31) {
                long year;
                try { year = Long.parseLong(mt.group(2)); } catch (NumberFormatException e) { year = 0; }
                if (year > 0) {
                    long ms = SB_EPOCH + (year - 1) * YEAR_MS + month * MONTH_MS + (day - 1) * DAY_MS;
                    at.add(new int[]{Math.min(1, lines.size())});
                    text.add(FMT.format(Instant.ofEpochMilli(ms)));
                }
            }
        }
        lastAt = at;
        lastText = text;
    }

    private static Instant parse(String line) {
        Matcher m = TIMER.matcher(line);
        while (m.find()) {
            if (m.group(1) == null && m.group(2) == null && m.group(3) == null && m.group(4) == null) continue;
            long secs = num(m.group(1)) * 86400 + num(m.group(2)) * 3600 + num(m.group(3)) * 60 + num(m.group(4));
            return Instant.now().plusSeconds(secs + 30).truncatedTo(ChronoUnit.MINUTES);
        }
        return null;
    }

    private static long num(String s) {
        return s == null ? 0 : Long.parseLong(s);
    }
}
