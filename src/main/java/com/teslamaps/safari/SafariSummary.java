package com.teslamaps.safari;

// Ported from HypixelSimpleChat's SafariSummary.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** SAFARI REWARD SUMMARY block -> one line at the closing bar. */
public final class SafariSummary {

    private static final Pattern SEPARATOR = Pattern.compile("[▬-]{8,}");
    private static final Pattern GAIN = Pattern.compile("\\+([\\d,]+) (Shards?|Safari Essence|Hunting Exp)");

    private static boolean active;
    private static int lines;
    private static final Map<String, String> gains = new LinkedHashMap<>();

    private SafariSummary() {}

    /** @return true when the line must not reach chat. */
    public static boolean hide(String raw) {
        if (!TeslaMapsConfig.get().safariSummaryLine) {
            active = false;
            return false;
        }
        String clean = StringUtil.strip(raw).trim();
        if (clean.equals("SAFARI REWARD SUMMARY")) {
            active = true;
            lines = 0;
            gains.clear();
            return true;
        }
        if (!active) return false;
        if (++lines > 12) {
            active = false;
            return false;
        }
        if (clean.isEmpty()) return true;
        Matcher g = GAIN.matcher(clean);
        if (g.matches()) {
            gains.put(g.group(2), g.group(1));
            return true;
        }
        if (SEPARATOR.matcher(clean).matches()) {
            active = false;
            if (!gains.isEmpty()) send(line());
            gains.clear();
            return true;
        }
        active = false;
        return false;
    }

    private static String line() {
        StringBuilder sb = new StringBuilder("§2§lSAFARI §r");
        boolean first = true;
        for (Map.Entry<String, String> e : gains.entrySet()) {
            if (!first) sb.append(" §8· ");
            first = false;
            String amount = e.getValue();
            switch (e.getKey()) {
                case "Safari Essence" -> sb.append("§f+").append(amount).append(" Essence");
                case "Hunting Exp" -> sb.append("§b+").append(shortNum(amount)).append(" Hunting Exp");
                default -> sb.append("§9+").append(amount).append(" Shards");
            }
        }
        return sb.toString();
    }

    static String shortNum(String s) {
        long n;
        try {
            n = Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return s;
        }
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return trim(n / 1000.0) + "k";
        return trim(n / 1_000_000.0) + "M";
    }

    private static String trim(double v) {
        String out = String.format(java.util.Locale.ROOT, "%.1f", v);
        return out.endsWith(".0") ? out.substring(0, out.length() - 2) : out;
    }

    private static void send(String text) {
        TickScheduler.schedule(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
        });
    }
}
