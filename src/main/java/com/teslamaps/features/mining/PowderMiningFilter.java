package com.teslamaps.features.mining;

// After SkyHanni's PowderMiningChatFilter.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.ChatFilter;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Thins out the Crystal Hollows treasure chest reward spam. */
public final class PowderMiningFilter {

    public static final String[] EGGS = {"Show All", "Green Up", "Yellow Up", "Red Up", "Blue Only", "Hide All"};
    public static final String[] GEMS = {"Show All", "Flawed Up", "Fine Up", "Flawless Only", "Hide All"};
    private static final Pattern WRAPPER = Pattern.compile("^▬{64}$");
    private static final Pattern AMOUNT = Pattern.compile("^(.+?)(?: x([\\d,]+))?$");
    private static final Pattern RAW_AMOUNT = Pattern.compile("\\s*(?:§r)?§8x[\\d,]+\\s*$");
    private static final Pattern LEAD = Pattern.compile("^(?:\\s|§r)+");
    private static final Pattern EGG = Pattern.compile("^(?:(Green|Yellow|Red|Blue) )?Goblin Egg$");
    private static final Pattern GEM = Pattern.compile("^(?:\\S+ )?(Rough|Flawed|Fine|Flawless) (?:Ruby|Amethyst|Jade|Amber|Sapphire|Topaz|Jasper) Gemstone$");
    private static final Set<String> COMMON = Set.of("Ascension Rope", "Wishing Compass", "Oil Barrel", "Sludge Juice", "Yoggie",
            "Treasurite", "FTX 3070", "Synthetic Heart", "Control Switch", "Robotron Reflector", "Electron Transmitter", "Superlite Motor");
    private static final Set<String> HIDDEN_HEADERS = Set.of("CHEST LOCKPICKED", "LOOT CHEST COLLECTED", "REWARDS");
    private static final Set<String> NOTICES = Set.of("You uncovered a treasure chest!",
            "You have successfully picked the lock on this chest!", "This chest has already been looted.");

    private static boolean inRewards;

    private PowderMiningFilter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PowderMiningFilter").section("Mining")
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Powder Mining Chat Filter", () -> c.powderChatFilter, v -> c.powderChatFilter = v)
                            .desc("Crystal Hollows treasure chests: hides the chest notices and cheap rewards, what is left becomes one short \"+ amount reward\" line.")
                            .tags("powder", "treasure chest", "chat filter", "gemstone", "goblin egg", "essence", "crystal hollows");
                    b.slider("  Hide Powder Below", 0, 60000, () -> (float) c.powderChatFilterPowder,
                            v -> c.powderChatFilterPowder = Math.round(v / 500f) * 500, 0).desc("60000 hides all powder");
                    b.slider("  Hide Essence Below", 0, 20, () -> (float) c.powderChatFilterEssence,
                            v -> c.powderChatFilterEssence = Math.round(v), 0).desc("20 hides all essence");
                    b.toggle("  Hide Common Items", () -> c.powderChatFilterCommon, v -> c.powderChatFilterCommon = v)
                            .desc("Ascension Rope, Wishing Compass, Oil Barrel, Sludge Juice, Yoggie, Treasurite, Robot Parts");
                    b.dropdown("  Goblin Eggs", EGGS, () -> c.powderChatFilterEggs, v -> c.powderChatFilterEggs = v);
                    b.dropdown("  Gemstones", GEMS, () -> c.powderChatFilterGems, v -> c.powderChatFilterGems = v);
                    b.toggle("  Hide \"Stronger Tool\" Messages", () -> c.powderChatFilterTool, v -> c.powderChatFilterTool = v);
                    b.toggle("  Compact Kept Rewards", () -> c.powderChatFilterCompact, v -> c.powderChatFilterCompact = v);
                });
    }

    /** TRUE = hide, FALSE = rewritten, null = not ours. */
    static Boolean handle(Component message, String text, String stripped) {
        TeslaMapsConfig c = cfg();
        if (!c.powderChatFilter || !SkyblockUtils.isInArea("Crystal Hollows")) { inRewards = false; return null; }
        String t = stripped.trim();
        if (NOTICES.contains(t)) return true;
        if (c.powderChatFilterTool && t.startsWith("You need a tool with a Breaking Power of")) return true;
        if (WRAPPER.matcher(t).matches() && (text.contains("§e§l▬") || text.contains("§d§l▬"))) {
            inRewards = !inRewards;
            return true;
        }
        if (!inRewards) return null;
        if (t.isEmpty() || HIDDEN_HEADERS.contains(t)) return true;
        if (!stripped.startsWith("    ")) return null;

        Matcher m = AMOUNT.matcher(t);
        if (!m.matches()) return null;
        String name = m.group(1);
        String amountStr = m.group(2);
        long amount = 1;
        if (amountStr != null) {
            try { amount = Long.parseLong(amountStr.replace(",", "")); } catch (NumberFormatException ignored) {}
        }
        if (name.equals("Gemstone Powder") || name.equals("Mithril Powder")) {
            if (c.powderChatFilterPowder >= 60000 || amount < c.powderChatFilterPowder) return true;
        } else if (name.equals("Gold Essence") || name.equals("Diamond Essence")) {
            if (c.powderChatFilterEssence >= 20 || amount < c.powderChatFilterEssence) return true;
        } else if (COMMON.contains(name)) {
            if (c.powderChatFilterCommon) return true;
        } else {
            Matcher egg = EGG.matcher(name);
            if (egg.matches()) {
                int mode = index(EGGS, c.powderChatFilterEggs);
                String col = egg.group(1);
                int rank = col == null ? 0 : switch (col) { case "Green" -> 1; case "Yellow" -> 2; case "Red" -> 3; default -> 4; };
                if (mode == EGGS.length - 1 || (mode > 0 && rank < mode)) return true;
            } else {
                Matcher gem = GEM.matcher(name);
                if (gem.matches()) {
                    int mode = index(GEMS, c.powderChatFilterGems);
                    int rank = switch (gem.group(1)) { case "Rough" -> 0; case "Flawed" -> 1; case "Fine" -> 2; default -> 3; };
                    if (mode == GEMS.length - 1 || (mode > 0 && rank < mode)) return true;
                }
            }
        }
        if (!c.powderChatFilterCompact) return null;
        String reward = RAW_AMOUNT.matcher(LEAD.matcher(text).replaceFirst("")).replaceAll("");
        String shortForm = "§8+ " + (amountStr != null ? "§7" + amountStr + " " : "") + reward;
        ChatFilter.queueRewrite(text, ChatFilter.compact(message, shortForm));
        return false;
    }

    private static int index(String[] options, String v) {
        for (int i = 0; i < options.length; i++) if (options[i].equals(v)) return i;
        return 0;
    }
}
