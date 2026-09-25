package com.teslamaps.features;

// Ported from FrankyAddons' PartyFinder
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DianaPartyFinder {

    private static final Pattern SBO_INVITE = Pattern.compile(
            "(?i)\\[sbo].*?\\binvited\\s+([A-Za-z0-9_]+)\\s+to (?:the |your )?party");
    private static final Pattern JOINED = Pattern.compile("^(?:\\[[^]]*?] ?)?(\\w{1,16}) joined the party\\.$");
    private static final Pattern BURROWS = Pattern.compile(
            "(?i)(\\d+(?:[.,]\\d+)?)\\s*([km])?\\s*(/h|b/h|burrows?|hr)?");
    private static final int PENDING_TTL = 1800;
    private static final int REPLY_TTL = 600;

    private static String pending;
    private static boolean joined;
    private static int expiry;
    private static int ticks;

    private DianaPartyFinder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DianaPartyFinder").section("Party").legit().when(() -> cfg().dianaPartyFinder)
                .tickAlways(DianaPartyFinder::tick)
                .chat(DianaPartyFinder::onChat)
                .config("Party", b -> {
                    b.label("Diana Party Finder");
                    b.toggle("Diana Burrows Check", () -> cfg().dianaPartyFinder, v -> cfg().dianaPartyFinder = v).cheat()
                            .desc("After an SBO invite joins, asks /pc !burrows and kicks them below the minimum burrows/h");
                    b.slider("  Minimum Burrows/h (0 = only show)", 0f, 5000f,
                            () -> cfg().dianaPartyFinderMinRate, v -> cfg().dianaPartyFinderMinRate = Math.round(v / 50f) * 50f, 0);
                });
    }

    private static void tick() {
        ticks++;
        if (pending != null && ticks >= expiry) clear();
        if (Minecraft.getInstance().level == null) clear();
    }

    private static void onChat(String raw) {
        if (LegitMode.blocksCheats()) return;
        String s = StringUtil.strip(raw).trim();
        if (pending == null) {
            Matcher m = SBO_INVITE.matcher(s);
            if (m.find()) {
                pending = m.group(1);
                joined = false;
                expiry = ticks + PENDING_TTL;
            }
            return;
        }
        String name = pending;
        if (!joined) {
            Matcher m = JOINED.matcher(s);
            if (m.matches() && m.group(1).equalsIgnoreCase(name)) {
                joined = true;
                expiry = ticks + REPLY_TTL;
                TickScheduler.schedule(30, () -> Announce.party("!burrows"));
            }
            return;
        }
        if (!s.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)) || !isReport(s)) return;
        Double rate = parse(s);
        if (rate == null) return;
        double min = cfg().dianaPartyFinderMinRate;
        clear();
        if (min > 0 && rate < min) {
            msg("§c" + name + " §7has §c" + fmt(rate) + " burrows/h §7(min " + fmt(min) + ") - kicking");
            if (PartyTracker.mayLead())
                TickScheduler.schedule(30 + ThreadLocalRandom.current().nextInt(20),
                        () -> Announce.command("party kick " + name));
        } else {
            msg("§a" + name + " §7has §a" + fmt(rate) + " burrows/h");
        }
    }

    private static boolean isReport(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return l.contains("burrows") || l.contains("b/h") || l.contains("/h");
    }

    static Double parse(String text) {
        int colon = text.lastIndexOf(':');
        String tail = (colon < 0 ? text : text.substring(colon + 1)).trim();
        Matcher m = BURROWS.matcher(tail);
        String num = null, unit = null, lastNum = null, lastUnit = null;
        while (m.find()) {
            lastNum = m.group(1);
            lastUnit = m.group(2);
            if (m.group(3) != null && !m.group(3).isEmpty()) {
                num = m.group(1);
                unit = m.group(2);
            }
        }
        if (num == null) { num = lastNum; unit = lastUnit; }
        if (num == null) return null;
        try {
            double v = Double.parseDouble(num.replace(',', '.'));
            if (unit != null) {
                String u = unit.toLowerCase(Locale.ROOT);
                if (u.equals("k")) v *= 1000;
                else if (u.equals("m")) v *= 1_000_000;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    private static void clear() {
        pending = null;
        joined = false;
        expiry = 0;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s)); });
    }
}
