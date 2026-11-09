package com.teslamaps.features.events;

// After SkyHanni's MythologicalCreatureTracker.kt / DianaProfitTracker.kt, Devonian's DianaDropTracker.kt /
// DianaMobTracker.kt and Skysoft's MythologicalRitualTracking.kt / MythologicalRitualPartyCommands.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Diana: burrows, mythological creatures, drops, coins, "since" counters and party !commands. */
public final class MythologicalTracker {

    private static final Pattern MOB = Pattern.compile(
            "^(?:Oh|Uh oh|Yikes|Oi|Good Grief|Danger|Woah)! You dug out (?:an? )?(?<mob>[\\w ]+)!$");
    private static final Pattern DROP = Pattern.compile(
            "^(?:[A-Z]+ )?RARE DROP! (?:You dug out an? )?(?<drop>.+?)(?: \\(\\+(?<mf>[\\d,]+)% ✯ Magic Find\\))?!?$");
    private static final Pattern COINS = Pattern.compile("^Wow! You dug out (?<coins>[\\d,]+) coins!$");

    static final String[] MOBS = {"Minos Hunter", "Siamese Lynxes", "Gaia Construct", "Stranded Nymph", "Cretan Bull",
            "Harpy", "Minotaur", "Minos Champion", "Minos Inquisitor", "Sphinx", "Manticore", "King Minos"};
    private static final Map<String, String> DROPS = new LinkedHashMap<>();
    static {
        DROPS.put("Enchanted Book (Chimera I)", "ENCHANTMENT_ULTIMATE_CHIMERA_1");
        DROPS.put("Chimera I", "ENCHANTMENT_ULTIMATE_CHIMERA_1");
        DROPS.put("Griffin Feather", "GRIFFIN_FEATHER");
        DROPS.put("Braided Griffin Feather", "BRAIDED_GRIFFIN_FEATHER");
        DROPS.put("Daedalus Stick", "DAEDALUS_STICK");
        DROPS.put("Crown of Greed", "CROWN_OF_GREED");
        DROPS.put("Washed-up Souvenir", "WASHED_UP_SOUVENIR");
        DROPS.put("Minos Relic", "MINOS_RELIC");
        DROPS.put("Dwarf Turtle Shelmet", "DWARF_TURTLE_SHELMET");
        DROPS.put("Crochet Tiger Plushie", "CROCHET_TIGER_PLUSHIE");
        DROPS.put("Antique Remedies", "ANTIQUE_REMEDIES");
        DROPS.put("Hilt of Revelations", "HILT_OF_REVELATIONS");
        DROPS.put("Mythos Fragment", "MYTHOS_FRAGMENT");
        DROPS.put("Cretan Urn", "CRETAN_URN");
        DROPS.put("Brain Food", "BRAIN_FOOD");
        DROPS.put("Shimmering Wool", "SHIMMERING_WOOL");
        DROPS.put("Fateful Stinger", "FATEFUL_STINGER");
        DROPS.put("Manti-core", "MANTI_CORE");
        DROPS.put("Mythological Dye", "DYE_MYTHOLOGICAL");
        DROPS.put("Myth the Fish", "MYTH_THE_FISH");
    }

    // since counters: key -> {label, counted mob or "" for any mob, reset by mob, reset by drop id}
    private static final String[][] SINCE = {
            {"inq", "Mobs since Inquisitor", "", "Minos Inquisitor", ""},
            {"king", "Mobs since King Minos", "", "King Minos", ""},
            {"manti", "Mobs since Manticore", "", "Manticore", ""},
            {"sphinx", "Mobs since Sphinx", "", "Sphinx", ""},
            {"chim", "Inquisitors since Chimera", "Minos Inquisitor", "", "ENCHANTMENT_ULTIMATE_CHIMERA_1"},
            {"stick", "Minotaurs since Stick", "Minotaur", "", "DAEDALUS_STICK"},
            {"relic", "Champions since Relic", "Minos Champion", "", "MINOS_RELIC"},
            {"core", "Manticores since Core", "Manticore", "", "MANTI_CORE"},
            {"wool", "Kings since Wool", "King Minos", "", "SHIMMERING_WOOL"},
            {"food", "Sphinxes since Food", "Sphinx", "", "BRAIN_FOOD"},
    };

    private static final String BURROWS = "MYTHO_BURROWS";
    private static final String LOOTSHARE = "MYTHO_LOOTSHARE";

    private static Tracker tracker;
    private static long lastActivity, lastSpade, lastCommand;
    private static boolean show;
    private static String lastMf = null;

    private MythologicalTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    static String mobKey(String mob) {
        return "MYTHO_MOB_" + mob.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public static void register() {
        tracker = Tracker.create("mythological", "§6§lMythological Tracker")
                .priceSource(() -> cfg().mythoTrackerPriceSource).maxRows(40)
                .fixedValue(BURROWS, 0).name(BURROWS, "§7Burrows Dug")
                .fixedValue(LOOTSHARE, 0).name(LOOTSHARE, "§7Loot Shares");
        for (String m : MOBS) tracker.fixedValue(mobKey(m), 0).name(mobKey(m), "§e" + m);
        tracker.extra(MythologicalTracker::extra);
        HudEditElement.of("mythoTracker", "Mythological Tracker").section("Events")
                .enabled(() -> cfg().mythoTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("MythologicalTracker").section("Events").when(() -> cfg().mythoTracker)
                .tick(MythologicalTracker::tick)
                .serverChat(MythologicalTracker::onChat)
                .chat(MythologicalTracker::onAnyChat)
                .hud("mythotracker", MythologicalTracker::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mythological Tracker", () -> c.mythoTracker, v -> c.mythoTracker = v)
                            .desc("Burrows dug, every mythological creature, drops, coins and profit per hour. "
                                    + "Shown with a spade in hand or while digging in the Hub. Move it in /tmap gui.")
                            .tags("diana", "mythological", "burrow", "inquisitor", "chimera", "griffin", "tracker");
                    b.toggle("  Always Show", () -> c.mythoTrackerAlways, v -> c.mythoTrackerAlways = v);
                    b.toggle("  Since Counters", () -> c.mythoTrackerSince, v -> c.mythoTrackerSince = v)
                            .desc("Mobs since the last Inquisitor, Inquisitors since the last Chimera, ...");
                    b.toggle("  Hide Dig Messages", () -> c.mythoHideChat, v -> c.mythoHideChat = v)
                            .desc("Hides the burrow, coin and creature dug-out lines once they are counted");
                    b.toggle("  Party Commands", () -> c.mythoPartyCommands, v -> c.mythoPartyCommands = v)
                            .desc("Answers !inq !chim !king !manti !stick !relic !wool !food !burrows !mobs !coins !feathers "
                                    + "!since <x> !profit !playtime !mf in party chat (2 s cooldown)");
                    b.priceSource("  Price Source", () -> c.mythoTrackerPriceSource, v -> c.mythoTrackerPriceSource = v);
                    b.trackerButtons(tracker);
                    b.button("  Reset Since Counters", () -> {
                        c.mythoSince.clear();
                        TeslaMapsConfig.save();
                    });
                });
    }

    private static List<String> extra(Tracker.View v) {
        List<String> out = new ArrayList<>();
        long mobs = totalMobs(v);
        if (mobs > 0) {
            long inq = tracker.count(mobKey("Minos Inquisitor"), v);
            out.add("§7Mobs: §e" + mobs + (inq > 0 ? String.format(Locale.ROOT, " §7(Inquisitor %.2f%%)", inq * 100.0 / mobs) : ""));
        }
        if (cfg().mythoTrackerSince) {
            Map<String, Long> since = cfg().mythoSince;
            for (String[] s : SINCE) {
                Long n = since.get(s[0]);
                if (n != null) out.add("§7" + s[1] + ": §e" + n);
            }
        }
        if (lastMf != null) out.add("§7Last Magic Find: §b" + lastMf + "% ✯");
        return out;
    }

    private static long totalMobs(Tracker.View v) {
        long n = 0;
        for (String m : MOBS) n += tracker.count(mobKey(m), v);
        return n;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ItemUtil.skyblockId(mc.player.getMainHandItem()).contains("ANCESTRAL_SPADE")) lastSpade = now;
        show = SkyblockUtils.isInSkyblock() && (cfg().mythoTrackerAlways || now - lastSpade < 10_000
                || (now - lastActivity < 120_000 && SkyblockUtils.isInArea("Hub")));
        tracker.tick();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("You dug out a Griffin Burrow!") || t.startsWith("You finished the Griffin burrow chain!")) {
            tracker.add(BURROWS, 1);
            active();
            return;
        }
        Matcher m = COINS.matcher(t);
        if (m.matches()) {
            tracker.addCoins(Long.parseLong(m.group("coins").replace(",", "")));
            active();
            return;
        }
        m = MOB.matcher(t);
        if (m.matches()) {
            String mob = canonical(m.group("mob").trim());
            if (mob == null) return;
            tracker.add(mobKey(mob), 1);
            bumpSince(mob);
            active();
            return;
        }
        if (t.startsWith("LOOT SHARE You received loot for assisting") && SkyblockUtils.isInArea("Hub")
                && System.currentTimeMillis() - lastActivity < 300_000) {
            tracker.add(LOOTSHARE, 1);
            return;
        }
        if (!t.contains("RARE DROP!")) return;
        m = DROP.matcher(t);
        if (!m.matches()) return;
        String name = m.group("drop").trim();
        String id = DROPS.get(name);
        if (id == null) return;
        if (!SkyblockUtils.isInArea("Hub") && !name.equals("Griffin Feather")) return;
        tracker.name(id, SkyblockItems.coloredName(id) != null ? SkyblockItems.coloredName(id) : "§6" + name);
        tracker.add(id, 1);
        if (m.group("mf") != null) lastMf = m.group("mf");
        resetSince(id);
        active();
    }

    private static String canonical(String mob) {
        if (mob.equals("Siamese Lynx") || mob.equals("Bagheera") || mob.equals("Azrael")) return "Siamese Lynxes";
        for (String m : MOBS) if (m.equals(mob)) return m;
        return null;
    }

    private static void active() {
        lastActivity = System.currentTimeMillis();
        tracker.markActivity();
    }

    private static void bumpSince(String mob) {
        Map<String, Long> since = cfg().mythoSince;
        for (String[] s : SINCE) {
            if (!s[3].isEmpty()) {
                since.put(s[0], s[3].equals(mob) ? 0L : since.getOrDefault(s[0], 0L) + 1);
            } else if (s[2].equals(mob)) {
                since.merge(s[0], 1L, Long::sum);
            }
        }
        TeslaMapsConfig.save();
    }

    private static void resetSince(String dropId) {
        Map<String, Long> since = cfg().mythoSince;
        boolean changed = false;
        for (String[] s : SINCE) {
            if (s[4].equals(dropId)) {
                since.put(s[0], 0L);
                changed = true;
            }
        }
        if (changed) TeslaMapsConfig.save();
    }

    /** Dig lines this tracker counted, for the chat filter. */
    public static boolean hideLine(String t) {
        TeslaMapsConfig c = cfg();
        if (!c.mythoTracker || !c.mythoHideChat || !c.section("Events")) return false;
        return t.startsWith("You dug out a Griffin Burrow!") || t.startsWith("You finished the Griffin burrow chain!")
                || t.startsWith("Wow! You dug out ") && t.endsWith(" coins!")
                || t.equals("Follow the arrows to find the treasure!")
                || MOB.matcher(t).matches();
    }

    // --- party commands ---

    private static void onAnyChat(String raw) {
        if (!cfg().mythoPartyCommands) return;
        PlayerChat.Msg m = PlayerChat.current();
        if (m == null || m.channel() != PlayerChat.Channel.PARTY || !m.message().startsWith("!")) return;
        long now = System.currentTimeMillis();
        if (now - lastCommand < 2000) return;
        String reply = reply(m.message().trim());
        if (reply == null) return;
        lastCommand = now;
        Announce.party(reply);
    }

    private static String reply(String msg) {
        String[] parts = msg.toLowerCase(Locale.ROOT).split("\\s+");
        Tracker.View v = Tracker.View.TOTAL;
        long mobs = totalMobs(v);
        return switch (parts[0]) {
            case "!inq", "!inqs", "!inquisitor", "!inquis" -> rate("Inquisitor", tracker.count(mobKey("Minos Inquisitor"), v), mobs);
            case "!king", "!kings", "!kingminos" -> rate("King Minos", tracker.count(mobKey("King Minos"), v), mobs);
            case "!manti", "!manticore" -> rate("Manticore", tracker.count(mobKey("Manticore"), v), mobs);
            case "!sphinx" -> rate("Sphinx", tracker.count(mobKey("Sphinx"), v), mobs);
            case "!chim", "!chimera", "!chims", "!book", "!books" ->
                    rate("Chimera", tracker.count("ENCHANTMENT_ULTIMATE_CHIMERA_1", v), tracker.count(mobKey("Minos Inquisitor"), v));
            case "!stick", "!sticks" -> rate("Sticks", tracker.count("DAEDALUS_STICK", v), tracker.count(mobKey("Minotaur"), v));
            case "!relic", "!relics" -> rate("Relics", tracker.count("MINOS_RELIC", v), tracker.count(mobKey("Minos Champion"), v));
            case "!core", "!cores" -> rate("Cores", tracker.count("MANTI_CORE", v), tracker.count(mobKey("Manticore"), v));
            case "!stinger" -> rate("Stingers", tracker.count("FATEFUL_STINGER", v), tracker.count(mobKey("Manticore"), v));
            case "!wool", "!wools" -> rate("Wool", tracker.count("SHIMMERING_WOOL", v), tracker.count(mobKey("King Minos"), v));
            case "!food", "!brainfood" -> rate("Brain Food", tracker.count("BRAIN_FOOD", v), tracker.count(mobKey("Sphinx"), v));
            case "!feathers", "!feather" -> "Feathers: " + tracker.count("GRIFFIN_FEATHER", v);
            case "!coins", "!coin" -> "Coins: " + String.format(Locale.ROOT, "%,d", tracker.count(Tracker.COINS, v));
            case "!burrows", "!burrow" -> "Burrows: " + tracker.count(BURROWS, v);
            case "!mobs", "!mob" -> "Mobs: " + mobs;
            case "!since" -> since(parts.length > 1 ? parts[1] : "inq");
            case "!profit", "!profits" -> "Profit: " + ItemPrice.format(tracker.profit(Tracker.View.SESSION))
                    + " (" + ItemPrice.format(tracker.perHour()) + "/h)";
            case "!playtime" -> "Playtime: " + (tracker.activeMillis() / 60_000) + "m";
            case "!mf", "!magicfind" -> lastMf == null ? null : "Last Magic Find: " + lastMf + "%";
            default -> null;
        };
    }

    private static String rate(String label, long n, long of) {
        return label + ": " + n + (of > 0 ? String.format(Locale.ROOT, " (%.2f%%)", n * 100.0 / of) : "");
    }

    private static String since(String arg) {
        String key = switch (arg) {
            case "chim", "chimera", "chims", "book", "books" -> "chim";
            case "stick", "sticks" -> "stick";
            case "relic", "relics" -> "relic";
            case "king", "kings", "kingminos" -> "king";
            case "manti", "manticore" -> "manti";
            case "core", "cores" -> "core";
            case "wool", "wools" -> "wool";
            case "sphinx" -> "sphinx";
            case "food", "brainfood" -> "food";
            default -> "inq";
        };
        for (String[] s : SINCE) if (s[0].equals(key)) return s[1] + ": " + cfg().mythoSince.getOrDefault(key, 0L);
        return null;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || com.teslamaps.utils.HudVisibility.suppressed()) return;
        TeslaMapsConfig c = cfg();
        tracker.draw(g, mc, c.mythoTrackerX, c.mythoTrackerY, c.mythoTrackerScale);
    }
}
