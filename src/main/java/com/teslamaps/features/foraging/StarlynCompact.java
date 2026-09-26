package com.teslamaps.features.foraging;

// After SkyHanni's CompactStarlynSisters.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Squashes the Starlyn Sisters contest result block and the collection PB spam into single lines. */
public final class StarlynCompact {

    private static final Pattern RESULT_START = Pattern.compile("\\[NPC] (?<sister>[\\S ]+): You reached the (?<bracket>\\w+) Bracket in my contest!");
    private static final Pattern POINTS = Pattern.compile("\\[NPC] [\\S ]+: You earned a total of (?<points>[\\d,]+) points!(?<pb> That's a new PERSONAL BEST!)?");
    private static final Pattern PREVIOUS = Pattern.compile("\\[NPC] [\\S ]+: Your previous Personal Best was (?<prev>[\\d,]+)\\.");
    private static final Pattern SEE_ME = Pattern.compile("\\[NPC] [\\S ]+: Come see me at .+ to claim your rewards!");
    private static final Pattern COLL_PB = Pattern.compile("PERSONAL BEST: You increased your (?<wood>\\w+) Collection by (?<during>[\\d,]+) during the contest! That's (?<more>[\\d,]+) more than your previous best!");
    private static final Pattern SWEEP = Pattern.compile("Your total . Sweep is now increased by (?<sweep>[\\d.]+%)!");
    private static final Pattern SURPASSED = Pattern.compile("\\[NPC] (?<sister>[\\w ]+): PERSONAL BEST! You've surpassed your previous record of (?<record>[\\d,]+) (?<wood>[\\S ]+) logs collected in my Contest!");
    private static final Pattern KEEP_UP = Pattern.compile("\\[NPC] [\\w ]+: Keep it up!");
    private static final String CMD = "/starlynsisterlevels";

    private static boolean inResults, inPb;
    private static String sister = "", bracket = "", points = "", prev = "";
    private static boolean newPb;
    private static String wood = "", during = "", more = "";
    private static long startedAt;

    private StarlynCompact() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("StarlynCompact").config("Foraging", b -> {
            TeslaMapsConfig c = b.config();
            b.label("Starlyn Sisters");
            b.toggle("Compact Starlyn Contest", () -> c.starlynCompact, v -> c.starlynCompact = v)
                    .desc("One line per contest result and per collection PB, with hover and click to /starlynsisterlevels.")
                    .tags("starlyn", "agatha", "contest", "personal best", "sweep");
            b.toggle("  Contest Results", () -> c.starlynCompactResults, v -> c.starlynCompactResults = v);
            b.toggle("  Collection Personal Bests", () -> c.starlynCompactPb, v -> c.starlynCompactPb = v);
        });
    }

    /** From ForagingChat, server lines; true hides the line. */
    public static boolean handle(String clean) {
        TeslaMapsConfig c = cfg();
        if (!c.starlynCompact || !Foraging.onForagingIsland()) return false;
        String t = clean.trim();
        if (System.currentTimeMillis() - startedAt > 10_000) inResults = inPb = false;
        if (c.starlynCompactPb && pb(t)) return true;
        return c.starlynCompactResults && results(t);
    }

    private static boolean pb(String t) {
        Matcher m = SURPASSED.matcher(t);
        if (m.matches()) {
            send(Component.literal("§b" + m.group("sister") + "'s §eContest: You broke a §dpersonal best §eof §b"
                    + m.group("record") + " §6" + m.group("wood") + " logs§e! Keep it up!"));
            return true;
        }
        if (KEEP_UP.matcher(t).matches()) return true;
        if (!inPb) {
            m = COLL_PB.matcher(t);
            if (!m.matches()) return false;
            inPb = true;
            startedAt = System.currentTimeMillis();
            wood = m.group("wood");
            during = m.group("during");
            more = m.group("more");
            return true;
        }
        m = SWEEP.matcher(t);
        if (!m.matches()) return false;
        inPb = false;
        String sweep = m.group("sweep");
        Component hover = Component.literal("§eClick to check your personal bests!\n§2Sweep Increase§7: §2" + sweep
                + "\n§6Collected§7: §b" + during + " " + wood + " §eLogs\n§6PB Increase§7: §a" + more + " " + wood + " §eLogs");
        send(Component.literal("§6" + wood + " PB§e: §2Sweep §a+" + sweep + " §efrom §b" + during + " §eLogs §7(§a" + more + " §7more)")
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover)).withClickEvent(new ClickEvent.RunCommand(CMD))));
        return true;
    }

    private static boolean results(String t) {
        Matcher m;
        if (!inResults) {
            m = RESULT_START.matcher(t);
            if (!m.matches()) return false;
            inResults = true;
            startedAt = System.currentTimeMillis();
            sister = m.group("sister");
            bracket = m.group("bracket");
            points = prev = "";
            newPb = false;
            return true;
        }
        m = POINTS.matcher(t);
        if (m.matches()) {
            points = m.group("points");
            newPb = m.group("pb") != null;
            return true;
        }
        m = PREVIOUS.matcher(t);
        if (m.matches()) {
            prev = m.group("prev");
            return true;
        }
        if (!SEE_ME.matcher(t).matches()) return false;
        inResults = false;
        StringBuilder sb = new StringBuilder("§b").append(sister).append("'s §eContest: §b").append(points)
                .append(" §epoints, ").append(bracketColor(bracket)).append("§l").append(bracket).append(" §ebracket");
        if (newPb) sb.append(" §d§lNEW PB").append(prev.isEmpty() ? "" : " §7(was §b" + prev + "§7)");
        sb.append("§e.");
        send(Component.literal(sb.toString()).withStyle(s -> s
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to claim your rewards!")))
                .withClickEvent(new ClickEvent.RunCommand(CMD))));
        return true;
    }

    private static String bracketColor(String b) {
        return switch (b) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "SPECIAL", "VERY" -> "§c";
            default -> "§f";
        };
    }

    private static void send(Component line) {
        TickScheduler.schedule(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(line);
        });
    }
}
