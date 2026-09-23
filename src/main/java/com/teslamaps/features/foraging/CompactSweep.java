package com.teslamaps.features.foraging;

// Ported from SkyHanni's CompactSweepDetails.kt
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.TickScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Sweep Details block squashed into one line, the full breakdown in its hover, click runs /treegifts. */
final class CompactSweep {

    private static final Pattern HEADER = Pattern.compile(
            "^\\s*(?:§.)*Sweep Details: (?<sweep>(?:§.)+(?<amt>[\\d,.]+)).? Sweep");
    private static final Pattern TOUGHNESS = Pattern.compile(
            "^\\s*(?:§.)*\\s*(?<tree>[^§]+?) Tree Toughness: (?:§.)*(?<tough>[\\d,.]+) (?:§.)*(?<green>§.)(?<logs>[\\d,.]+) Logs");
    private static final Pattern PENALTY = Pattern.compile(
            "^\\s*(?:§.)*\\s*(?<reason>[^§:]+): (?<pd>(?:§.)+-(?<pct>[\\d,.]+)%) Sweep (?:§.)*(?<green>§.)(?<logs>[\\d,.]+) Logs(?: (?<tip>(?:§.)+.+))?");

    private static boolean inside, addedInitial;
    private static String logs = "", treeType = "", proTip = "";
    private static final List<String> breakdown = new ArrayList<>();
    private static final List<String> hover = new ArrayList<>();

    private CompactSweep() {}

    /** @return true when the line belongs to a sweep block and is replaced. */
    static boolean handle(Component message, String clean) {
        if (!clean.contains("Sweep") && !clean.contains("Toughness")) return false;
        String f = ChatText.formatted(message).replace("§r", "");
        Matcher m = HEADER.matcher(f);
        if (m.find()) {
            if (hover.size() > 2) send();
            reset();
            inside = true;
            hover.add("§eClick to open the Tree Gifts guide!");
            hover.add("§6Initial §2Sweep§7: §2" + m.group("amt"));
            breakdown.add("§2Sweep: " + m.group("sweep"));
            return true;
        }
        if (!inside) return false;
        m = TOUGHNESS.matcher(f);
        if (m.find()) {
            String tough = m.group("tough");
            if (tough.endsWith(".0")) tough = tough.substring(0, tough.length() - 2);
            treeType = m.group("tree").trim();
            logs = m.group("logs");
            hover.add("§6Initial Logs: " + logs + " " + treeType + " Logs §7(§6" + tough + " toughness§7)");
            if (m.group("green").equals("§a")) send();
            return true;
        }
        m = PENALTY.matcher(f);
        if (m.find()) {
            if (!addedInitial) {
                breakdown.add("§7, §e" + logs + " logs");
                addedInitial = true;
            }
            logs = m.group("logs");
            hover.add("§e" + m.group("reason").trim() + "§7: §c-" + m.group("pct") + "% §7(" + logs + " logs)");
            breakdown.add("§7(" + m.group("pd") + "§7)");
            proTip = m.group("tip") == null ? "" : m.group("tip");
            if (m.group("green").equals("§a")) send();
            return true;
        }
        return false;
    }

    static void reset() {
        inside = false;
        addedInitial = false;
        logs = treeType = proTip = "";
        breakdown.clear();
        hover.clear();
    }

    private static void send() {
        hover.add("§6Final Logs: §a" + logs + " §6" + treeType + " Logs");
        StringBuilder h = new StringBuilder(String.join("\n", hover));
        if (!proTip.isEmpty()) h.append("\n§6Pro tip: ").append(proTip);

        StringBuilder line = new StringBuilder();
        for (String part : breakdown) {
            line.append(part);
            if (!part.startsWith("§2Sweep: ") || !addedInitial) line.append(' ');
        }
        line.append("§7-> §a").append(logs).append(" logs");
        if (!proTip.isEmpty()) line.append("\n  §6Pro tip: ").append(proTip);

        Component hoverText = Component.literal(h.toString());
        Component out = Component.literal(line.toString()).withStyle(s -> s
                .withHoverEvent(new HoverEvent.ShowText(hoverText))
                .withClickEvent(new ClickEvent.RunCommand("/treegifts")));
        reset();
        TickScheduler.schedule(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(out);
        });
    }
}
