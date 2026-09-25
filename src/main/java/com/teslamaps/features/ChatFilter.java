package com.teslamaps.features;

// New categories ported from SkyHanni's ChatFilter.kt (patterns rewritten for stripped text)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFilter {

    public static final String G_DUNGEON = "Dungeon";
    public static final String G_COMBAT = "Combat / Misc";
    public static final String G_SPAM = "Spam / Info";
    public static final String G_MORE = "More Filters";

    /**
     * {@code anyLine} rules also see lines with ": " (the old first block), the rest only
     * colon-free lines. {@code systemOnly} rules never touch player chat.
     */
    public record Rule(String id, String group, boolean anyLine, boolean systemOnly,
                       Predicate<TeslaMapsConfig> on, Predicate<String> match) {}

    public static final List<Rule> RULES = new ArrayList<>();

    /** Id of the rule that matched last, for debugging. */
    public static String lastReason;

    private static void old(String id, String group, boolean anyLine, Predicate<TeslaMapsConfig> on, Predicate<String> match) {
        RULES.add(new Rule(id, group, anyLine, false, on, match));
    }

    private static void more(String id, Predicate<TeslaMapsConfig> on, Predicate<String> match) {
        RULES.add(new Rule(id, G_MORE, true, true, on, match));
    }

    private static Predicate<String> any(Pattern... ps) {
        return t -> {
            for (Pattern p : ps) if (p.matcher(t).matches()) return true;
            return false;
        };
    }

    private static Predicate<String> oneOf(String... lines) {
        Set<String> set = Set.of(lines);
        return set::contains;
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }

    private static final Pattern SKY_MALL = p("^(?:New day! Your Sky Mall buff changed!|New buff: .*|You can disable this messaging by toggling Sky Mall in your /hotm!)$");
    private static final Pattern LOTTERY = p("^(?:New day! Your Lottery buff changed!|You can disable this messaging by toggling Lottery in your /hotf!)$");

    static {
        old("chatFilterEmpty", G_DUNGEON, true, c -> c.chatFilterEmpty, t -> t.trim().isEmpty());
        old("chatFilterWatcher", G_DUNGEON, true, c -> c.chatFilterWatcher, t -> t.contains("[BOSS] The Watcher:"));
        old("chatFilterF4Boss", G_DUNGEON, true, c -> c.chatFilterF4Boss, t -> t.contains("[BOSS] Thorn:")
                || t.toLowerCase().contains("[crowd]"));
        old("chatFilterOruo", G_SPAM, true, c -> c.chatFilterOruo, t -> t.contains("[STATUE] Oruo the Omniscient:"));
        old("chatFilterWitherKingHeroes", G_SPAM, true, c -> c.chatFilterWitherKingHeroes, t -> t.contains("[BOSS] Wither King:")
                && t.contains("I hope you'll become the Heroes I could never be"));
        old("chatFilterMort", G_SPAM, true, c -> c.chatFilterMort, t -> t.contains("[NPC] Mort:"));
        old("chatFilterProfileInfo", G_SPAM, true, c -> c.chatFilterProfileInfo, t -> t.contains("You are playing on profile:")
                || t.contains("Profile ID:"));
        old("chatFilterPerkBuffs", G_SPAM, true, c -> c.chatFilterPerkBuffs, t -> {
            if (!(t.contains("buff changed!") || t.contains("New buff:") || t.contains("disable this messaging by toggling"))) return false;
            if (!TeslaMapsConfig.get().chatFilterPerkBuffsElsewhere) return true;
            String s = t.trim();
            if (SKY_MALL.matcher(s).matches()) return !com.teslamaps.features.mining.Mining.onMiningIsland();
            if (LOTTERY.matcher(s).matches()) return !com.teslamaps.features.foraging.Foraging.onForagingIsland();
            return true;
        });
        old("chatFilterMilestone", G_SPAM, true, c -> c.chatFilterMilestone, t -> t.contains("Milestone") && t.contains("so far!"));
        old("chatFilterWatchdog", G_SPAM, true, c -> c.chatFilterWatchdog, t -> t.contains("[WATCHDOG ANNOUNCEMENT]")
                || (t.contains("Watchdog has banned") && t.contains("players"))
                || t.contains("Staff have banned an additional")
                || t.contains("Blacklisted modifications are a bannable offense!"));

        more("chatFilterBossAll", c -> c.chatFilterBossAll, t -> t.startsWith("[BOSS] "));
        more("chatFilterLobby", c -> c.chatFilterLobby, any(
                p(".* (?:joined|spooked into|slid into) the lobby!.*"),
                p("\\s*You can now create your own Hypixel SMP server!\\s*"),
                p("✦ .* found (?:a|an) .* (?:Mystery Box!|in a (?:Holiday )?Mystery Box!)"),
                p("✦ You earned \\d+ Mystery Dust!"),
                p("✦ You earned \\d+ Pet Consumables items!"),
                p(".*You have reached your Hype limit!.*"),
                p(".*Welcome to the Prototype Lobby.*"),
                p(".*HYPIXEL.* (?:is hosting a .* tournament!|tournament is live!).*"),
                p("You are still radiating with Generosity!")));
        more("chatFilterWelcome", c -> c.chatFilterWelcome, oneOf("Welcome to Hypixel SkyBlock!"));
        more("chatFilterFriendJoin", c -> c.chatFilterFriendJoin, any(p("^(?:Friend|Guild) > \\w{1,16} (?:joined|left)\\.$")));
        more("chatFilterWarpMore", c -> c.chatFilterWarpMore, t -> {
            String s = t.trim();
            return s.startsWith("Request join for Hub") || s.startsWith("Request join for Dungeon Hub #")
                    || (s.startsWith("Warped to ") && s.endsWith("!"))
                    || s.equals("Warping you to your SkyBlock island...") || s.equals("Warping using transfer token...")
                    || s.equals("Finding player...") || s.equals("Sending a visit request...");
        });
        more("chatFilterBzAhMinis", c -> c.chatFilterBzAhMinis, oneOf(
                "Putting item in escrow...", "Putting coins in escrow...", "Setting up the auction...",
                "Processing purchase...", "Processing bid...", "Claiming BIN auction...",
                "[Bazaar] Submitting sell offer...", "[Bazaar] Submitting buy order...",
                "[Bazaar] Executing instant sell...", "[Bazaar] Executing instant buy...",
                "[Bazaar] Cancelling order...", "[Bazaar] Claiming order...", "[Bazaar] Putting goods in escrow...",
                "Depositing coins...", "Withdrawing coins..."));
        more("chatFilterSlayerSpam", c -> c.chatFilterSlayerSpam, t -> {
            String s = t.trim();
            return s.equals("SLAYER QUEST STARTED!") || (s.startsWith("» Slay ") && s.contains(" worth of "))
                    || s.equals("SLAYER QUEST COMPLETE!") || s.endsWith("Slayer LVL 9 - LVL MAXED OUT!")
                    || (s.startsWith("» Talk to Maddox to claim your") && s.endsWith("Slayer XP!"))
                    || s.equals("NICE! SLAYER BOSS SLAIN!")
                    || s.equals("You received kill credit for assisting on a slayer miniboss!")
                    || s.startsWith("✆ RING... ");
        });
        more("chatFilterUselessWarnings", c -> c.chatFilterUselessWarnings, oneOf(
                "You are sending commands too fast! Please slow down.", "You can't use this while in combat!",
                "You can not modify your equipped armor set!", "Please wait a few seconds between refreshing!",
                "This item is not salvageable!", "Place a Dungeon weapon or armor piece above the anvil to salvage it!",
                "Whoa! Slow down there!", "Wait a moment before confirming!",
                "You cannot open the SkyBlock menu while in combat!",
                "Your radio is weak. Find another enjoyer to boost it.", "Your radio signal is strong!",
                "Your radio lost signal. There's too many enjoyers on this channel.",
                "You can't use the wardrobe in combat!"));
        more("chatFilterBlazetekk", c -> c.chatFilterBlazetekk, oneOf(
                "Your radio is weak. Find another enjoyer to boost it.", "Your radio signal is strong!",
                "Your radio lost signal. There's too many enjoyers on this channel."));
        more("chatFilterUselessNotifications", c -> c.chatFilterUselessNotifications, any(
                p("You tipped \\d+ players? in \\d+(?: different)? games?!"),
                p("Your previous Plasmaflux Power Orb was removed!"),
                p("You used your Mining Speed Boost Pickaxe Ability!"),
                p("Your Mining Speed Boost has expired!"),
                p("Mining Speed Boost is now available!"),
                p("(?:You have just received|Since you've been away you earned) 0 coins as interest in your (?:personal|co-op) bank account!"),
                p("Your Blessing enchant got you double drops!")));
        more("chatFilterFireSale", c -> c.chatFilterFireSale, t -> {
            String s = t.trim();
            return s.startsWith("♨") || s.contains("FIRE SALE");
        });
        more("chatFilterEventLevel", c -> c.chatFilterEventLevel, t -> {
            String s = t.trim();
            return s.startsWith("You are now Event Level ")
                    || (s.startsWith("You earned ") && s.endsWith(" Event Silver!"))
                    || (s.contains(" LEVEL UP! ") && s.length() < 20);
        });
        more("chatFilterHoppity", c -> c.chatFilterHoppity, t ->
                (t.startsWith("HOPPITY'S HUNT A ") && t.endsWith(" has appeared!"))
                        || t.startsWith("Hoppity's Hunt has begun!"));
        more("chatFilterChocolateFactory", c -> c.chatFilterChocolateFactory, any(
                p(".* has been promoted to \\[.*] .*!"),
                p("Your Rabbit Barn capacity has been increased to .* Rabbits!"),
                p("You will now produce .* Chocolate per click!"),
                p("You upgraded to .*?!")));
        more("chatFilterSacrifice", c -> c.chatFilterSacrifice, any(
                p("SACRIFICE! .* turned .* into .* Dragon Essence!"),
                p("BONUS LOOT! They also received .* from their sacrifice!")));
        more("chatFilterNoPests", c -> c.chatFilterNoPests,
                oneOf("There are not any Pests on your Garden right now! Keep farming!"));
        more("chatFilterLegacyItems", c -> c.chatFilterLegacyItems,
                t -> t.startsWith("You currently have one or more Legacy Items"));
        more("chatFilterParkour", c -> c.chatFilterParkour, any(
                p("Started parkour .*!"), p("Finished parkour .* in .*!"),
                p("Reached checkpoint #.* for parkour .*!"), p("Wrong checkpoint for parkour .*!"),
                p("You haven't reached all checkpoints for parkour .*!"), p("Cancelled parkour!.*")));
        more("chatFilterTeleportPads", c -> c.chatFilterTeleportPads, any(
                p("Warped from the .* to the .*!"), p("This Teleport Pad does not have a destination set!")));
        more("chatFilterChefTed", c -> c.chatFilterChefTed,
                t -> t.startsWith("[NPC] Feast Chef Ted: Thanks for the donation!"));
        more("chatFilterRewardBundles", c -> c.chatFilterRewardBundles, any(
                p("You haven't claimed your \\w+ Rewards yet!"), p("Talk to the .+ in the .+!")));
        more("chatFilterWinterGift", c -> c.chatFilterWinterGift, t -> {
            String s = t.trim();
            int sp = s.indexOf(' ');
            return (sp > 1 && s.charAt(sp - 1) == '!' && s.contains(" gift with "))
                    || (s.startsWith("EXTRA! +") && s.contains("North Star"))
                    || s.equals("This player is playing a profile mode which doesn't allow gifting!")
                    || s.equals("Can't place gifts this close to spawn!");
        });
        Pattern anita = p(".*\\[NPC] Jacob: Your Anita's \\w+ is giving you \\+\\d{1,2}\\S* .+ Fortune during the contest!");
        more("chatFilterAnita", c -> c.chatFilterAnita,
                t -> t.contains("Anita's") && anita.matcher(t).matches() && !com.teslamaps.utils.SkyblockUtils.isInGarden());
        Pattern rareReward = p("RARE REWARD! .* found an? .* in their .* Chest!");
        more("chatFilterRareRewards", c -> c.chatFilterRareRewards, t -> rareReward.matcher(t.trim()).matches());
        Pattern soloClass = p("Your (?:Healer|Mage|Berserk|Archer|Tank) stats are doubled because you are the only player using this class!");
        Pattern soloStats = p("\\[(?:Healer|Mage|Berserk|Archer|Tank)] .*");
        more("chatFilterSoloClass", c -> c.chatFilterSoloClass, t -> com.teslamaps.dungeon.DungeonManager.isInDungeon()
                && (soloClass.matcher(t.trim()).matches() || soloStats.matcher(t.trim()).matches()));
        Pattern fairy = p("[\\w']+ the Fairy: (?:You killed me! .*|Have a great life!)");
        more("chatFilterFairy", c -> c.chatFilterFairy, t -> com.teslamaps.dungeon.DungeonManager.isInDungeon()
                && fairy.matcher(t.trim()).matches());

        old("chatFilterBlessings", G_DUNGEON, false, c -> c.chatFilterBlessings, t -> t.contains("DUNGEON BUFF!")
                || (t.contains("Blessing of") && t.contains("was picked up!"))
                || (t.contains("Blessing of") && t.contains("has obtained"))
                || t.contains("Granted you +") || t.contains("Also granted you +"));
        old("chatFilterEssence", G_DUNGEON, false, c -> c.chatFilterEssence, t -> t.contains("ESSENCE!")
                || (t.contains("Wither Essence!") && t.contains("extra essence")));
        old("chatFilterKeys", G_DUNGEON, false, c -> c.chatFilterKeys, t -> t.contains("Key was picked up!")
                || (t.contains("has obtained") && t.contains("Key")));
        old("chatFilterReviveStone", G_DUNGEON, false, c -> c.chatFilterReviveStone, t -> t.contains("has obtained") && t.contains("Revive Stone"));
        old("chatFilterDoors", G_DUNGEON, false, c -> c.chatFilterDoors, t -> t.contains(" door!")
                && (t.contains("opened a") || t.contains("opened the")));
        old("chatFilterWish", G_DUNGEON, false, c -> c.chatFilterWish, t -> t.contains("Wish healed you for"));
        old("chatFilterPickups", G_DUNGEON, false, c -> c.chatFilterPickups, t -> t.contains("was picked up!") || t.contains("has obtained"));

        old("chatFilterBlocksInWay", G_COMBAT, false, c -> c.chatFilterBlocksInWay, t -> t.contains("There are blocks in the way!"));
        old("chatFilterUltReady", G_COMBAT, false, c -> c.chatFilterUltReady, t -> t.contains("is ready to use! Press DROP to activate it!"));
        old("chatFilterAoeDamage", G_COMBAT, false, c -> c.chatFilterAoeDamage, t -> (t.contains("enemies for") || t.contains("enemy for")) && t.contains("damage"));
        old("chatFilterGuildXp", G_COMBAT, false, c -> c.chatFilterGuildXp, t -> t.contains("from playing SkyBlock!"));
        old("chatFilterKillCombo", G_COMBAT, false, c -> c.chatFilterKillCombo, t -> t.contains("Kill Combo"));
        old("chatFilterAbilityCooldown", G_COMBAT, false, c -> c.chatFilterAbilityCooldown, t -> {
            String s = t.trim();
            return s.startsWith("This ability is on cooldown for ") && s.endsWith("s.");
        });

        old("chatFilterStash", G_SPAM, false, c -> c.chatFilterStash, t -> t.contains("stashed away!")
                || t.contains("materials stashed!") || t.contains("types of materials stashed")
                // singular form: "(This totals 1 type of material stashed!) (2x)"
                || t.contains("type of material stashed")
                || (t.contains("CLICK HERE") && t.contains("pick them up")));
        old("chatFilterServerMsgs", G_SPAM, false, c -> c.chatFilterServerMsgs, t -> t.contains("Sending to server")
                || t.contains("Warping...") || t.contains("Queuing..."));
        old("chatFilterSacks", G_SPAM, false, c -> c.chatFilterSacks, t -> t.contains("[Sacks]"));
        old("chatFilterGfs", G_SPAM, false, c -> c.chatFilterGfs, t -> t.contains("from your Sacks to your inventory"));
        old("chatFilterBonePlating", G_SPAM, false, c -> c.chatFilterBonePlating, t -> t.contains("bone plating reduced the damage"));
    }

    /** First enabled rule that matches the line, or null. */
    public static Rule match(String raw) {
        return matchStripped(StringUtil.strip(raw));
    }

    private static Rule matchStripped(String t) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        lastReason = null;
        // belongs to the screenshot manager, which prints its own line with buttons,
        // so it is not tied to the chat filter master switch
        if (c.screenshotHideVanillaMessage && t.startsWith("Saved screenshot as ")) return SCREENSHOT;
        if (c.chickenHeadHideChat && ChickenHeadTimer.hideLine(t)) return SCREENSHOT;

        if (!c.chatFilterEnabled) return null;

        boolean colon = t.contains(": ");
        boolean player = false, playerKnown = false;
        for (int i = 0, n = RULES.size(); i < n; i++) {
            Rule r = RULES.get(i);
            if (!r.anyLine && colon) continue;
            if (!r.on.test(c)) continue;
            if (r.systemOnly) {
                if (!playerKnown) { player = PlayerChat.current() != null; playerKnown = true; }
                if (player) continue;
            }
            if (r.match.test(t)) {
                lastReason = r.id;
                return r;
            }
        }
        return null;
    }

    private static final Rule SCREENSHOT = new Rule("screenshotHideVanillaMessage", "", true, false, c -> true, t -> true);

    // ---- actions (ported from HypixelSimpleChat's Registry.match + ChatHudMixin guards) ----

    public static final String[] ACTIONS = {"Hide", "Grey", "Compact", "Compact + Grey", "Redirect"};
    public static final String[] GROUP_ACTIONS = {"Default", "Hide", "Grey", "Compact", "Compact + Grey", "Redirect"};
    public static final String[] GROUPS = {G_DUNGEON, G_COMBAT, G_SPAM, G_MORE};

    /** Short form per rule, § codes allowed. "" = nothing worth showing, the line goes. */
    private static final Map<String, Function<String, String>> COMPACT = new HashMap<>();
    private static final Pattern BLESSING = p("Blessing of (\\w+(?: [IVX]+)?)");
    private static final Pattern KEY = p("(Wither|Blood) Key");
    private static final Pattern DOOR = p("opened (?:a|the) (\\w+) door!");
    private static final Pattern ESSENCE = p("found (?:a |an )?(x\\d+ )?([\\w ]+? Essence)");
    private static final Pattern SACKS_ADD = p("\\+([\\d,]+) items?");
    private static final Pattern SACKS_REM = p("-([\\d,]+) items?");
    private static final Pattern GFS = p("Moved ([\\d,]+) (.+) from your Sacks to your inventory");
    private static final Pattern COMBO = p("\\+(\\d+) Kill Combo");
    private static final Pattern AOE = p("Your (.+?) hit (\\d+) enem(?:y|ies) for ([\\d,.]+) damage");
    private static final Pattern GEXP = p("You earned ([\\d,]+) GEXP");

    static {
        COMPACT.put("chatFilterBlessings", t -> {
            if (t.contains("Granted you +") || t.contains("Also granted you +")) return "";
            Matcher m = BLESSING.matcher(t);
            return m.find() ? "§dBlessing of " + m.group(1) : null;
        });
        COMPACT.put("chatFilterKeys", t -> {
            Matcher m = KEY.matcher(t);
            return m.find() ? (m.group(1).equals("Blood") ? "§c" : "§8") + m.group(1) + " Key §7picked up" : null;
        });
        COMPACT.put("chatFilterDoors", t -> {
            Matcher m = DOOR.matcher(t);
            return m.find() ? "§7" + m.group(1).charAt(0) + m.group(1).substring(1).toLowerCase() + " door opened" : null;
        });
        COMPACT.put("chatFilterEssence", t -> {
            Matcher m = ESSENCE.matcher(t);
            if (!m.find()) return null;
            String n = m.group(1) == null ? "1" : m.group(1).trim().substring(1);
            return "§d+" + n + " " + m.group(2);
        });
        COMPACT.put("chatFilterSacks", t -> {
            Matcher add = SACKS_ADD.matcher(t), rem = SACKS_REM.matcher(t);
            String out = "§6[Sacks]";
            if (add.find()) out += " §a+" + add.group(1);
            if (rem.find()) out += " §c-" + rem.group(1);
            return out;
        });
        COMPACT.put("chatFilterGfs", t -> {
            Matcher m = GFS.matcher(t);
            return m.find() ? "§aSacks §7→ §f" + m.group(1) + "x " + m.group(2) : null;
        });
        COMPACT.put("chatFilterKillCombo", t -> {
            Matcher m = COMBO.matcher(t);
            return m.find() ? "§6+" + m.group(1) + " Combo" : null;
        });
        COMPACT.put("chatFilterAoeDamage", t -> {
            Matcher m = AOE.matcher(t);
            return m.find() ? "§7" + m.group(1) + " §8» §e" + m.group(2) + " §7for §c" + m.group(3) : null;
        });
        COMPACT.put("chatFilterGuildXp", t -> {
            Matcher m = GEXP.matcher(t);
            return m.find() ? "§2+" + m.group(1) + " GEXP" : null;
        });
    }

    private static String pendingText;
    private static Component pendingOut;

    public static String actionOf(Rule r) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String a = c.chatFilterGroupActions == null ? null : c.chatFilterGroupActions.get(r.group);
        if (a == null || a.equals("Default")) a = c.chatFilterAction;
        return a == null ? "Hide" : a;
    }

    /** From ChatMixin after every other handler: true = drop the line. May queue a rewrite. */
    public static boolean process(Component message, String text) {
        pendingText = null;
        pendingOut = null;
        String t = StringUtil.strip(text);
        if (StashCompact.handle(t)) return true;
        if (ChatCompactsMore.handle(message, t)) return true;
        if (MelodyCompact.handle(t)) return true;
        if (HoppityCompactChat.handle(message, text, t)) return true;
        Rule r = matchStripped(t);
        if (r == SCREENSHOT) return true;
        if (r != null) return apply(message, text, t, actionOf(r), COMPACT.get(r.id));
        if (ChatUserFilters.match(t) != null) {
            lastReason = "user";
            return apply(message, text, t, TeslaMapsConfig.get().chatUserFilterAction, null);
        }
        String shortForm = ChatCompactRules.apply(message, t);
        if (shortForm != null) {
            lastReason = "compact";
            if (shortForm.isEmpty()) return true;
            if (shortForm == ChatCompactRules.GREY) queue(text, grey(message));
            else if (!hasClick(message, true)) queue(text, compact(message, shortForm));
            return false;
        }
        Component pet = RareDropNames.petRarity(message, t);
        if (pet != null) queue(text, pet);
        return false;
    }

    /** Swap the line being added for {@code out}; used by the compact helpers. */
    static void queueRewrite(String text, Component out) {
        queue(text, out);
    }

    private static boolean apply(Component message, String text, String t, String action, Function<String, String> f) {
        switch (action == null ? "Hide" : action) {
            case "Grey" -> queue(text, grey(message));
            case "Compact", "Compact + Grey" -> {
                // a web link would not survive the rebuild
                if (hasClick(message, true)) return false;
                String shortForm = f == null ? null : f.apply(t);
                if (shortForm != null && shortForm.isEmpty()) return true;
                boolean greyed = action.equals("Compact + Grey");
                if (shortForm == null) {
                    if (greyed) queue(text, grey(message));
                } else {
                    queue(text, compact(message, greyed ? "§8" + StringUtil.strip(shortForm) : shortForm));
                }
            }
            case "Redirect" -> {
                if (TeslaMapsConfig.get().chatFilterKeepClickable && hasClick(message, false)) return false;
                ChatSpamHud.push(message);
                return true;
            }
            default -> {
                if (TeslaMapsConfig.get().chatFilterKeepClickable && hasClick(message, false)) return false;
                return true;
            }
        }
        return false;
    }

    private static void queue(String text, Component out) {
        pendingText = text;
        pendingOut = out;
    }

    /** Right before vanilla stores the line; the handlers above all saw the original. */
    public static Component rewrite(Component message) {
        if (pendingOut == null) return message;
        Component out = pendingOut;
        boolean same = message.getString().equals(pendingText);
        pendingOut = null;
        pendingText = null;
        return same ? out : message;
    }

    /** Same text, dark grey, click and hover kept. */
    static Component grey(Component c) {
        MutableComponent out = Component.empty();
        c.visit((style, text) -> {
            out.append(Component.literal(StringUtil.strip(text)).setStyle(style.withColor(ChatFormatting.DARK_GRAY)
                    .withBold(false).withObfuscated(false)));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** Short line; the full line moves into the hover, the first command click stays. */
    static Component compact(Component original, String shortForm) {
        HoverEvent[] hover = {null};
        ClickEvent[] click = {null};
        original.visit((style, text) -> {
            if (hover[0] == null && style.getHoverEvent() != null) hover[0] = style.getHoverEvent();
            ClickEvent e = style.getClickEvent();
            if (click[0] == null && (e instanceof ClickEvent.RunCommand || e instanceof ClickEvent.SuggestCommand)) click[0] = e;
            return Optional.empty();
        }, Style.EMPTY);
        HoverEvent h = hover[0] != null ? hover[0] : new HoverEvent.ShowText(original);
        ClickEvent k = click[0];
        return Component.literal(shortForm).withStyle(s -> s.withHoverEvent(h).withClickEvent(k));
    }

    /** {@code url} = web links, otherwise command buttons. */
    static boolean hasClick(Component c, boolean url) {
        return c.visit((style, text) -> {
            ClickEvent e = style.getClickEvent();
            boolean hit = url ? e instanceof ClickEvent.OpenUrl
                    : e instanceof ClickEvent.RunCommand || e instanceof ClickEvent.SuggestCommand;
            return hit ? Optional.of(Boolean.TRUE) : Optional.<Boolean>empty();
        }, Style.EMPTY).isPresent();
    }
}
