package com.teslamaps.features;

// Rules ported from HypixelSimpleChat's rules/common (Abilities, Combat, Drops, Economy, Pets, Npc, Events, Misc)
// and rules/islands/Mining.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.PlayerChat;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shortens frequent lines instead of hiding them; the full line stays in the hover. */
public final class ChatCompactRules {

    private ChatCompactRules() {}

    /** Result: dim the original line instead of rewriting it. */
    static final String GREY = "\u0000grey";

    interface Fmt { String apply(Matcher m, Ctx c); }

    static final class Ctx {
        final Component msg;
        private String legacy;
        Ctx(Component msg) { this.msg = msg; }
        String legacy() {
            if (legacy == null) legacy = ChatText.formatted(msg);
            return legacy;
        }
        /** {@code text} as it sits in the coloured line, codes kept. */
        String span(String text, String fallback) { return ChatCompactRules.span(legacy(), text, fallback); }
    }

    private record Rule(Predicate<TeslaMapsConfig> on, Pattern p, Fmt f) {}

    private static final List<Rule> RULES = new ArrayList<>();

    private static void r(Predicate<TeslaMapsConfig> on, String regex, Fmt f) {
        RULES.add(new Rule(on, Pattern.compile(regex), f));
    }

    private static final Predicate<TeslaMapsConfig> COMBAT = c -> c.chatCompactCombat;
    private static final Predicate<TeslaMapsConfig> DAMAGE = c -> c.chatCompactCombat && c.chatCompactDamage;
    private static final Predicate<TeslaMapsConfig> DEATHS = c -> c.chatCompactCombat && c.chatCompactDeaths;
    private static final Predicate<TeslaMapsConfig> DROPS = c -> c.chatCompactDrops;
    private static final Predicate<TeslaMapsConfig> NPC = c -> c.chatCompactDrops && c.chatCompactNpcGrey;
    private static final Predicate<TeslaMapsConfig> MINING = c -> c.chatCompactMining;

    static {
        // ---- CHAT-040: abilities / warnings ----
        r(COMBAT, "(?:This (?:item's ability|item|ability)|Your Ultimate) is (?:currently )?on cooldown for (?:about )?(\\d+) more seconds?",
                (m, c) -> "§7Cooldown §8· §f" + m.group(1) + "s");
        r(COMBAT, "^No more charges, next one in (\\d+)s",
                (m, c) -> "§7No charges §8· §f" + m.group(1) + "s");
        r(COMBAT, "(?:This (?:item's ability|item|ability)|Your Ultimate) is (?:temporarily disabled!|on cooldown)",
                (m, c) -> "§7Cooldown");
        r(COMBAT, "You (?:do not have enough|need at least .+) mana to (?:do this|activate this)!",
                (m, c) -> "§bNot enough mana");
        r(COMBAT, "^(.+) is (?:ready to use! Press DROP to activate it!|now (?:available|ready)!)",
                (m, c) -> "§a✔ " + c.span(m.group(1), "§f") + " §7ready");
        r(COMBAT, "(?:Whow! Slow down there!|Whoa! Slow down there!|Woah slow down, you're doing that too fast!|Please wait a (?:few seconds between refreshing!|bit before doing this!))",
                (m, c) -> "§7Too fast");
        r(COMBAT, "^You are sending commands too fast! Please slow down\\.",
                (m, c) -> "§7Commands too fast");
        r(COMBAT, "^You can(?:'t|not) (?:use this|use the wardrobe|open the SkyBlock menu) (?:while )?in combat!",
                (m, c) -> "§7In combat");
        r(COMBAT, "^Command Failed: This command is on cooldown!",
                (m, c) -> "§7Command cooldown");

        // ---- CHAT-040: damage / heal ----
        r(DAMAGE, "^(.+?) used (.+?) on you hitting you for ([0-9,.]+) damage",
                (m, c) -> "§c" + m.group(2) + " §7· §f-" + shortNum(m.group(3)));
        r(DAMAGE, "^(.+?) (?:hit|burnt) you for ([\\d,.]+) (?:true )?damage",
                (m, c) -> "§c-" + shortNum(m.group(2)) + " §7(" + src(m.group(1)) + ")");
        r(DAMAGE, "^A (.+) exploded, hitting you for ([\\d,.]+) damage",
                (m, c) -> "§c-" + shortNum(m.group(2)) + " §7(" + m.group(1) + ")");
        r(DAMAGE, "^You were healed for ([\\d,.]+) health",
                (m, c) -> "§a+" + shortNum(m.group(1)) + "❤");
        r(DAMAGE, "^(.+?) healed you for ([\\d,.]+) health",
                (m, c) -> "§a+" + shortNum(m.group(2)) + "❤ §7(" + src(m.group(1)) + ")");
        r(DAMAGE, "^You gained ([\\d,.]+) HP worth of absorption",
                (m, c) -> "§e+" + shortNum(m.group(1)) + " absorption");
        r(DAMAGE, "^(.+) granted you ([\\d,.]+) strength for 20 seconds!",
                (m, c) -> "§4+" + m.group(2) + "❁ §7(20s)");

        // ---- CHAT-040: deaths of others, yours stays ----
        r(DEATHS, "^☠ (?!You )(\\S+) (.+?)\\.?$", (m, c) -> GREY);

        // ---- CHAT-041: drops / economy ----
        r(DROPS, "^\\+([\\d,.]+) SkyBlock XP(?: \\((.+?)\\))?(?: \\([\\d,]+/[\\d,]+\\))?$",
                (m, c) -> "§b+" + m.group(1) + " SB XP" + (m.group(2) == null ? "" : " §7(" + m.group(2) + ")"));
        r(DROPS, "^LOOT SHARE!? You received (?:an? )?(.+?) from (.+?) catching an? .+?!*$",
                (m, c) -> "§6Loot share §7· " + c.span(m.group(1), "§f") + " §8· " + c.span(m.group(2), "§f"));
        r(DROPS, "^LOOT SHARE!? You received (.+?) for assisting (.+?)!*$",
                (m, c) -> m.group(1).equals("loot") ? "§6Loot share §7· " + c.span(m.group(2), "§f")
                        : "§6Loot share §7· " + c.span(m.group(1), "§f") + " §8· " + c.span(m.group(2), "§f"));
        r(DROPS, "^RARE REWARD! (.+?) found an? (.+?) in their (.+?) Chest!",
                (m, c) -> "§6§lRARE REWARD §r§f" + m.group(1) + " §7· " + c.span(m.group(2), "§f") + " §fin " + m.group(3) + " Chest");
        r(DROPS, "^You earned ([\\d,]+) Event EXP from playing ",
                (m, c) -> "§e+" + m.group(1) + " Event EXP");
        r(DROPS, "^You (summoned|despawned) your (.+?)!?$",
                (m, c) -> (m.group(1).equals("summoned") ? "§a+ " : "§c- ") + c.span(m.group(2), "§f"));
        r(DROPS, "^Autopet equipped your \\[Lvl (\\d+)] (.+?)!",
                (m, c) -> "§cAutopet §7[" + m.group(1) + "] " + c.span(m.group(2), "§f"));
        r(DROPS, "^✆ RING", (m, c) -> "§a✆ §7Ring…");
        r(DROPS, "^Fire Sales for (.+) are starting soon!", (m, c) -> "§6♨ Fire Sale soon §8· §f" + m.group(1));
        r(DROPS, "FIRE SALE", (m, c) -> "§6♨ Fire Sale");
        r(DROPS, "^(?:Inventory full\\? Don't forget to check out your Storage inside the SkyBlock Menu!|You don't have any inventory space!)",
                (m, c) -> "§cInventory full");
        r(NPC, "^\\[NPC] ", (m, c) -> GREY);

        // ---- CHAT-044: mining ----
        r(MINING, "^You need a tool with a Breaking Power of (\\d+) to mine (.+?)!",
                (m, c) -> "§c⛏ Breaking Power " + m.group(1) + " §8· " + c.span(m.group(2), "§7"));
        r(MINING, "^You uncovered a treasure chest!", (m, c) -> "");
        r(MINING, "^You have successfully picked the lock on this chest!", (m, c) -> "");
    }

    static boolean any(TeslaMapsConfig c) {
        return c.chatCompactCombat || c.chatCompactDrops || c.chatCompactMining;
    }

    /** Short form for the line, {@link #GREY}, "" = drop, null = untouched. {@code t} is stripped. */
    static String apply(Component message, String t) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatFilterEnabled || !any(c)) return null;
        if (PlayerChat.current() != null && PlayerChat.current().channel() != PlayerChat.Channel.NPC) return null;
        String s = t.trim();
        if (s.isEmpty()) return null;

        Ctx ctx = null;
        for (int i = 0, size = RULES.size(); i < size; i++) {
            Rule r = RULES.get(i);
            if (!r.on.test(c)) continue;
            Matcher m = r.p.matcher(s);
            if (!m.find()) continue;
            if (ctx == null) ctx = new Ctx(message);
            return r.f.apply(m, ctx);
        }
        return null;
    }

    static String shortNum(String raw) {
        String digits = raw.replace(",", "");
        int dot = digits.indexOf('.');
        if (dot >= 0) digits = digits.substring(0, dot);
        long n;
        try { n = Long.parseLong(digits); } catch (NumberFormatException e) { return raw; }
        if (n >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", n / 1_000_000_000.0);
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
        return Long.toString(n);
    }

    private static String src(String s) {
        if (s.startsWith("The ")) s = s.substring(4);
        if (s.startsWith("Your ")) s = s.substring(5);
        return s.trim();
    }

    /** The part of {@code legacy} that reads {@code text}, with its leading colour and inner codes. */
    static String span(String legacy, String text, String fallback) {
        StringBuilder plain = new StringBuilder(legacy.length());
        int[] idx = new int[legacy.length() + 1];
        for (int i = 0; i < legacy.length(); i++) {
            char ch = legacy.charAt(i);
            if (ch == '§' && i + 1 < legacy.length()) { i++; continue; }
            idx[plain.length()] = i;
            plain.append(ch);
        }
        int p = text.isEmpty() ? -1 : plain.indexOf(text);
        if (p < 0) return fallback + text;
        int start = idx[p], end = idx[p + text.length() - 1] + 1;
        String lead = fallback;
        for (int i = start - 2; i >= 0; i--) {
            if (legacy.charAt(i) == '§' && isColor(legacy.charAt(i + 1))) {
                lead = "§" + legacy.charAt(i + 1);
                break;
            }
        }
        return lead + legacy.substring(start, end);
    }

    private static boolean isColor(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }
}
