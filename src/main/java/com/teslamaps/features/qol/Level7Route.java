package com.teslamaps.features.qol;

// Ported from FloydAddons' SkyBlockLevel7GuideRoute: 119 steps + passive completion checks
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Level7Route {

    record Step(int number, String label, String detection, String instruction) {}

    record Item(String id, String name, int count, boolean enchanted, List<String> lore) {}

    record Obs(String chat, String title, List<Item> screenItems, List<Item> inventory, List<String> nearby,
               List<String> scoreboard, Map<String, Integer> skills, int xpLevel, Long purse, Item held,
               Map<String, Item> armor, boolean sneaking, boolean teleported, long elapsedMs, Long treecapBin) {}

    record Base(List<Item> inventory, int xpLevel, Long purse) {}

    static final int MAX = 119;

    private static Step s(int n, String label, String detection, String instruction) {
        return new Step(n, label, detection, instruction);
    }

    static final List<Step> STEPS = List.of(
            s(1, "Farm fields", "Crop items enter inventory",
                    "In the Hub farm, break wheat, carrots, or potatoes until at least one crop enters your inventory."),
            s(2, "Sell crops to Alchemist", "Crop count falls in the Alchemist shop",
                    "Go to the Village Alchemist at 42, 70, -64 and click every crop stack in your inventory to sell it."),
            s(3, "Walk to Wool Weaver", "Wool Weaver is nearby",
                    "Enter the Builder's House and walk up to Wool Weaver at -47, 74, -31."),
            s(4, "Open Wool Weaver shop", "Wool Weaver shop opens",
                    "Right-click Wool Weaver through her introduction until the wool shop inventory is open."),
            s(5, "Buy White Wool", "64 White Wool are held",
                    "Buy exactly one full stack (64) of White Wool from Wool Weaver."),
            s(6, "Walk to Carpenter", "Carpenter is nearby",
                    "Go to the Furniture Shop and stand next to the Carpenter at 16, 72, -22."),
            s(7, "Unlock Carpentry (give wool)", "Carpentry dialogue or wool hand-in",
                    "Right-click the Carpenter through all dialogue and hand him the full stack of 64 White Wool."),
            s(8, "Walk to Lumber Merchant", "Lumber Merchant is nearby",
                    "Walk to the Lumber Merchant in the Village at -50, 70, -68."),
            s(9, "Open Lumber Merchant shop", "Lumber Merchant shop opens",
                    "Right-click the Lumber Merchant through his introduction until the Lumberjack Shop opens."),
            s(10, "Buy axes + sticks", "An axe and at least two sticks are held",
                    "Buy 32 Sticks plus the Rookie Axe, Promising Axe, Sweet Axe, and Efficient Axe."),
            s(11, "Walk to Weaponsmith", "Weaponsmith is nearby",
                    "Walk into the Weaponsmith building and approach the Weaponsmith at -10, 68, -130."),
            s(12, "Open Weaponsmith shop", "Weaponsmith shop opens",
                    "Right-click the Weaponsmith through the introduction until the Weaponsmith shop opens."),
            s(13, "Buy starter weapons", "Starter weapon purchases are observed",
                    "Buy the Undead Sword, End Sword, Spider Sword, and Wither Bow; skip the vanilla Diamond Sword and Bow because the Museum will not accept them."),
            s(14, "Walk to Blacksmith", "Blacksmith is nearby",
                    "Leave the shop and walk to the Blacksmith in the Village forge."),
            s(15, "Talk to Blacksmith", "Blacksmith dialogue is received",
                    "Right-click the Blacksmith and continue his first-time dialogue until the reforge interface appears."),
            s(16, "Walk to Mine Merchant", "Mine Merchant is nearby",
                    "Return to the Weaponsmith building and stand next to the Mine Merchant at -9, 68, -125."),
            s(17, "Open Mine Merchant shop", "Mine Merchant shop opens",
                    "Right-click the Mine Merchant through the introduction until the Miner Shop opens."),
            s(18, "Buy pickaxes + coal + gold", "Pickaxe, coal, and gold are held",
                    "Buy a Rookie Pickaxe, Promising Pickaxe, 18 Coal, and 19 Gold Ingots; skip the Golden Pickaxe because the Museum will not accept it."),
            s(19, "Craft a Golden Shovel", "Golden Shovel enters inventory",
                    "Open your crafting grid and place 1 Gold Ingot above 2 Sticks to craft a Golden Shovel."),
            s(20, "Warp to hub (Bea)", "Hub teleport is observed",
                    "Manually run /warp hub and wait until the Hub scoreboard and world finish loading."),
            s(21, "Walk to Bea", "Bea is nearby",
                    "Walk into Pet Care and approach Bea at 32, 70, -95."),
            s(22, "Open Bea's shop", "Bea shop opens",
                    "Right-click Bea through her introduction until the Bee pet shop opens."),
            s(23, "Buy Common Bee pet", "Common Bee pet item is held",
                    "Craft 2 Coal Blocks and 2 Gold Blocks, then buy the COMMON Bee for those blocks plus 4,999 coins."),
            s(24, "Add Bee pet", "Bee is added to the pet menu",
                    "Close Bea's shop and right-click the COMMON Bee item in your inventory to add it to your Pet Menu."),
            s(25, "Open pet menu", "Pets menu opens with Bee listed",
                    "Open the SkyBlock Menu, select Pets, and confirm that the Bee appears in the Pets menu."),
            s(26, "Equip Bee pet", "Bee summon/equipped signal is observed",
                    "Click the Bee in the Pets menu so its lore/status shows that it is summoned."),
            s(27, "Walk to Librarian", "Librarian is nearby",
                    "Walk to the Library beside the Auction House and approach the Librarian at -36, 69, -113."),
            s(28, "Talk to Librarian", "Librarian dialogue is received",
                    "Right-click the Librarian once and continue the enchanting tutorial dialogue."),
            s(29, "Open Librarian shop", "Librarian shop opens",
                    "Right-click the Librarian again until the Librarian shop inventory opens."),
            s(30, "Buy Experience Bottles", "Experience Bottles enter inventory",
                    "Buy 128 Experience Bottles; buy extra only if the bottles do not take you to vanilla XP level 20."),
            s(31, "Walk to XP throw spot", "Bottle use or XP gain starts",
                    "Close the shop, move beside the Library enchantment tables, look at the floor, and begin throwing bottles."),
            s(32, "Throw XP to level 20", "Vanilla experience level reaches 20",
                    "Keep throwing Experience Bottles at your feet until the green vanilla experience number reaches level 20."),
            s(33, "Enchant Golden Shovel", "Golden Shovel gains an enchantment",
                    "Use a Library Enchantment Table, insert the Golden Shovel, select any available enchant, and take it back."),
            s(34, "Warp to hub (Jamie)", "Hub teleport is observed",
                    "Manually run /warp hub, then walk toward Jamie in the Village at -36, 68, -39 after the teleport completes."),
            s(35, "Claim Rogue Sword from Jamie", "Rogue Sword enters inventory",
                    "Right-click Jamie, continue the one-time dialogue, and click the Rogue Sword in the Claim Reward menu so it enters your inventory."),
            s(36, "Open Museum with Madame Eleanor", "Madame Eleanor dialogue/menu is received",
                    "Walk to the Museum at -75, 76, 80, right-click Madame Eleanor, and continue until the main Museum donation menu opens."),
            s(37, "Donate eligible items to Museum", "The Rogue Sword donation is observed",
                    "Donate these 11 Museum-compatible items: Rookie Axe, Promising Axe, Sweet Axe, Efficient Axe, End Sword, Spider Sword, Undead Sword, Wither Bow, Rookie Pickaxe, Promising Pickaxe, and Rogue Sword. Do not donate the Golden Shovel."),
            s(38, "Retrieve Rogue Sword", "Rogue Sword returns to inventory",
                    "Open the Museum Weapons menu, select the donated Rogue Sword, retrieve it, and leave it in your inventory."),
            s(39, "Farm to Farming 10", "Farming skill reaches level 10",
                    "Return to the Hub farm and harvest crops continuously until the Farming skill reaches level 10."),
            s(40, "Sell leftovers to Alchemist", "Crop count falls in the Alchemist shop",
                    "Take all remaining farm drops to the Alchemist and sell every crop stack."),
            s(41, "Travel to The Barn", "The Barn location is reported",
                    "Use the farming launch pad at the edge of the Hub to travel to The Barn."),
            s(42, "Travel to Mushroom Desert", "Mushroom Desert location is reported",
                    "Cross The Barn and use its far launch pad to enter the Mushroom Desert."),
            s(43, "Walk to the sand approach", "Sand mining begins",
                    "Walk to an exposed sand patch in the Mushroom Desert and begin mining it with the Golden Shovel."),
            s(44, "Mine sand to Mining 12", "Mining skill reaches level 12",
                    "Keep mining sand without leaving the island until the Mining skill reaches level 12."),
            s(45, "Warp to hub (sell sand)", "Hub teleport is observed",
                    "Manually run /warp hub after Mining 12 and wait for the Hub teleport to finish."),
            s(46, "Sell Sand to Alchemist", "Sand count falls in the Alchemist shop",
                    "Open the Alchemist shop and sell every Sand stack collected in the Mushroom Desert."),
            s(47, "Farm coins for Treecapitator", "Purse reaches the live Treecapitator lowest BIN",
                    "Farm, forage, and sell drops until your purse reaches {treecapTarget}, the current Treecapitator lowest BIN provided by SkyCofl."),
            s(48, "Warp to hub (Auction)", "Hub teleport is observed",
                    "Manually run /warp hub and walk toward the Auction House after the Hub loads."),
            s(49, "Walk to Auction Agent", "Auction NPC or Auction House is reached",
                    "Enter the Auction House and right-click an Auction Agent or the Auction Master to open it."),
            s(50, "Buy a Treecapitator", "Treecapitator enters inventory",
                    "Search the Auction House for Treecapitator, choose the cheapest affordable BIN, and confirm the purchase."),
            s(51, "Equip the Treecapitator", "Treecapitator is held",
                    "Move the Treecapitator to your hotbar and select that slot so the axe is visibly held."),
            s(52, "Walk to Lumber Jack", "Lumber Jack is nearby",
                    "Walk into the Hub Forest and approach Lumber Jack at -113, 74, -37."),
            s(53, "Talk to Lumber Jack", "Lumber Jack dialogue is received",
                    "Right-click Lumber Jack, choose the accepting response, and continue until he requests 20 Oak Logs."),
            s(54, "Forage 20 Oak Logs", "20 Oak Logs are held",
                    "Use the Treecapitator on nearby oak trees until your inventory contains at least 20 Oak Logs."),
            s(55, "Return to Lumber Jack", "Lumber Jack is nearby",
                    "Return to Lumber Jack while keeping at least 20 Oak Logs in your inventory."),
            s(56, "Hand in Oak Logs", "Oak Logs leave inventory near Lumber Jack",
                    "Right-click Lumber Jack and continue the dialogue so exactly 20 Oak Logs are handed in."),
            s(57, "Claim the Iron Axe", "Axe reward claim is observed",
                    "Click the Promising Axe reward in the Claim Reward menu and move it into your inventory."),
            s(58, "Travel to The Park", "The Park location is reported",
                    "Follow the Forest path and use the launch pad to travel to The Park."),
            s(59, "Walk to Charlie", "Charlie is nearby",
                    "At the Birch Park entrance, walk up to Charlie at -278, 80, -18."),
            s(60, "Talk to Charlie", "Charlie dialogue is received",
                    "Right-click Charlie and continue his dialogue until he asks for 64 Birch Logs."),
            s(61, "Forage 64 Birch Logs", "64 Birch Logs are held",
                    "Chop birch trees in Birch Park until your inventory contains at least 64 Birch Logs."),
            s(62, "Return to Charlie", "Charlie is nearby",
                    "Return to Charlie while keeping the 64 Birch Logs in your inventory."),
            s(63, "Hand in Birch Logs", "Birch Logs leave inventory near Charlie",
                    "Right-click Charlie and continue the dialogue so the 64 Birch Logs are handed in."),
            s(64, "Talk to Charlie for reward", "Charlie reward dialogue/menu is received",
                    "Continue talking to Charlie until his Claim Reward menu appears."),
            s(65, "Claim Charlie's Trousers", "Charlie's Trousers enter inventory",
                    "Click Charlie's Trousers in the reward menu and place them in your inventory."),
            s(66, "Equip trousers, drop logs", "Trousers equipped and route logs cleared",
                    "Equip Charlie's Trousers in the leggings slot, then drop or sell every leftover route log."),
            s(67, "Finish Into the Woods (Charlie)", "Quest completion or equipped reward is observed",
                    "Right-click Charlie again and continue until the Into the Woods quest-completion message appears."),
            s(68, "Claim Travel Scroll to The Park", "Park Travel Scroll enters inventory",
                    "Click the Travel Scroll to The Park reward in Charlie's menu and keep the scroll in your inventory."),
            s(69, "Travel to Spruce Woods", "Spruce Woods location is reported",
                    "Follow the Park path past Birch Park until the scoreboard location changes to Spruce Woods."),
            s(70, "Walk to Kelly", "Kelly is nearby",
                    "Walk up to Kelly in Spruce Woods at -351, 94, 34."),
            s(71, "Talk to Kelly", "Kelly dialogue is received",
                    "Right-click Kelly, choose the accepting response, and continue until she requests 128 Spruce Logs."),
            s(72, "Forage 128 Spruce Logs", "128 Spruce Logs are held",
                    "Chop spruce trees until your inventory contains at least 128 Spruce Logs."),
            s(73, "Return to Kelly", "Kelly is nearby",
                    "Return to Kelly while keeping all 128 Spruce Logs in your inventory."),
            s(74, "Hand in Spruce Logs", "Spruce Logs leave inventory near Kelly",
                    "Right-click Kelly and continue the dialogue so the 128 Spruce Logs are handed in."),
            s(75, "Claim Kelly's T-Shirt", "Kelly's T-Shirt enters inventory",
                    "Click Kelly's T-Shirt in the reward menu and place it in your inventory."),
            s(76, "Equip chestplate, drop logs", "T-Shirt equipped and route logs cleared",
                    "Equip Kelly's T-Shirt in the chestplate slot, then drop or sell every leftover route log."),
            s(77, "Travel to Dark Thicket", "Dark Thicket location is reported",
                    "Continue along The Park path until the scoreboard location changes to Dark Thicket."),
            s(78, "Sneak up on Ryan", "Player sneaks near Ryan",
                    "Hold Sneak before approaching Ryan at -365, 102, -91 and remain sneaking beside the cult."),
            s(79, "Join The Cult", "Cult joining dialogue is received",
                    "Continue Ryan's dialogue and select the response that joins the Campfire Cult."),
            s(80, "Acknowledge Ryan (WHAT?)", "Ryan acknowledgement dialogue is received",
                    "Select the WHAT? response when shown and continue until Ryan explains the Trial of Fire."),
            s(81, "Trial of Fire (campfire)", "First campfire trial completion is received",
                    "Step into the campfire and remain inside it for the complete 10-second Trial of Fire."),
            s(82, "Walk back to Ryan", "Ryan is nearby",
                    "After surviving the fire, walk back to Ryan without leaving the Dark Thicket."),
            s(83, "Talk to Ryan (logs task)", "Ryan's next-task dialogue is received",
                    "Right-click Ryan and continue until he gives the Dark Oak Logs task."),
            s(84, "Forage 256 Dark Oak Logs", "256 Dark Oak Logs are held",
                    "Chop dark oak trees until your inventory contains at least 256 Dark Oak Logs."),
            s(85, "Return to Ryan", "Ryan is nearby",
                    "Return to Ryan while keeping all 256 Dark Oak Logs in your inventory."),
            s(86, "Hand in Dark Oak Logs", "Dark Oak Logs leave inventory near Ryan",
                    "Right-click Ryan and continue the dialogue so the 256 Dark Oak Logs are handed in."),
            s(87, "Claim Campfire Initiate Badge", "Campfire Initiate Badge is obtained",
                    "Click the Campfire Initiate Badge in the reward menu and place it in your inventory."),
            s(88, "Travel to Savanna Woodland", "Savanna Woodland location is reported",
                    "Follow The Park path onward until the scoreboard location changes to Savanna Woodland."),
            s(89, "Walk to Melody", "Melody is nearby",
                    "Climb to Melody's Plateau and approach Melody at -412, 109, 71."),
            s(90, "Talk to Melody", "Melody dialogue is received",
                    "Right-click Melody and continue until she asks you to repair the harp with 512 Acacia Logs."),
            s(91, "Forage 512 Acacia Logs", "512 Acacia Logs are held",
                    "Chop acacia trees until your inventory contains at least 512 Acacia Logs."),
            s(92, "Return to Melody", "Melody is nearby",
                    "Return to Melody's Plateau while keeping all 512 Acacia Logs in your inventory."),
            s(93, "Hand in Acacia Logs", "Acacia Logs leave inventory near Melody",
                    "Right-click Melody and continue the dialogue so the 512 Acacia Logs are handed in."),
            s(94, "Claim Melody's Shoes", "Melody's Shoes enter inventory",
                    "Click Melody's Shoes in the reward menu and place them in your inventory."),
            s(95, "Equip shoes", "Melody's Shoes are equipped",
                    "Equip Melody's Shoes in the boots slot."),
            s(96, "Stand on the harp spot", "Harp interaction begins",
                    "Stand directly beside the repaired harp on Melody's Plateau, close enough to interact with it."),
            s(97, "Open the Harp", "Melody/Harp menu opens",
                    "Right-click the harp so the Melody song-selection menu is open."),
            s(98, "Play all 11 Harp songs", "All 11 songs show at least 90% completion",
                    "Play all 11 songs in order and score at least 90% on each; reopen the menu to verify every Best score."),
            s(99, "Travel to Jungle Island", "Jungle Island location is reported",
                    "Follow the Park route past Savanna Woodland until the scoreboard reports Jungle Island."),
            s(100, "Forage to Foraging 12", "Foraging skill reaches level 12",
                    "Chop jungle trees continuously until the Foraging skill reaches level 12."),
            s(101, "Travel to Galatea", "Galatea location is reported",
                    "Take the route from Jungle Island into Galatea and wait until the scoreboard explicitly reports Galatea."),
            s(102, "Wait, then warp to hub", "Wait completes and Hub teleport is observed",
                    "After Galatea has been loaded for at least 2.5 seconds, manually run /warp hub and wait for the Hub."),
            s(103, "Top up to 100k coins", "Purse reaches 100,000 coins",
                    "Earn or sell enough items until the purse line shows at least 100,000 coins."),
            s(104, "Sell inventory to Alchemist", "Inventory falls in the Alchemist shop",
                    "Open the Alchemist shop and sell every disposable leftover item while keeping your Treecapitator and guide rewards."),
            s(105, "Warp to hub for Alda", "Hub teleport is observed",
                    "Manually run /warp hub once more to reset at Hub spawn before starting the Abiphone route."),
            s(106, "Walk to Alda", "Alda is nearby",
                    "Walk to Abiphones & Co. and approach Alda at 66, 71, -59."),
            s(107, "Talk to Alda", "Alda dialogue/shop is received",
                    "Right-click Alda, choose What's an Abiphone?, then choose I guess. and continue to her shop."),
            s(108, "Buy the Abiphone", "Abiphone Basic enters inventory",
                    "Click Abiphone Basic in Alda's shop and confirm the 97,000-coin purchase."),
            s(109, "Add Alda contact", "Alda appears in Abiphone contacts",
                    "Continue Alda's post-purchase dialogue until she is added to the Abiphone contacts."),
            s(110, "Walk to Taylor", "Taylor is nearby",
                    "Hold the Abiphone Basic and walk to Taylor inside the Fashion Shop at 22, 71, -46."),
            s(111, "Add Taylor contact", "Taylor appears in Abiphone contacts",
                    "Right-click Taylor while holding the Abiphone and complete the contact dialogue until Taylor is added."),
            s(112, "Warp to hub (Elizabeth)", "Hub teleport is observed",
                    "Manually run /warp hub to return to spawn before heading to the Community Center."),
            s(113, "Walk to Elizabeth", "Elizabeth is nearby",
                    "Hold the Abiphone and approach Elizabeth inside the Community Center near -4, 72, -102."),
            s(114, "Add Elizabeth contact", "Elizabeth appears in Abiphone contacts",
                    "Right-click Elizabeth with the Abiphone and complete the shown contact prompt until she is added."),
            s(115, "Pause before Seraphine", "Brief pause in the Community Center",
                    "After adding Elizabeth, stop moving and wait at least two seconds for the Seraphine sequence to become ready."),
            s(116, "Walk to Seraphine", "Clerk Seraphine is nearby",
                    "Keep the Abiphone held and walk from Elizabeth to Clerk Seraphine in the Community Center."),
            s(117, "Add Seraphine contact", "Seraphine appears in Abiphone contacts",
                    "Right-click Clerk Seraphine with the Abiphone and continue until Seraphine is added to contacts."),
            s(118, "Stand at Plumber Joe", "Plumber Joe is nearby",
                    "Keep the Abiphone held and walk to Plumber Joe in the Village at 57, 70, -78."),
            s(119, "Add Plumber Joe contact", "Plumber Joe appears in Abiphone contacts",
                    "Right-click Plumber Joe with the Abiphone and continue until the contact-added confirmation appears.")
    );

    static Step step(int n) {
        return n >= 1 && n <= STEPS.size() ? STEPS.get(n - 1) : null;
    }

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern CTRL = Pattern.compile("\\p{C}");
    private static final Pattern OTHER = Pattern.compile("[^a-z0-9%+';:_\\-\\s?!]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern PURSE = Pattern.compile("(?i)(?:purse|piggy)\\s*:\\s*([\\d,.]+)\\s*([kmb]?)");
    private static final Pattern SKILL = Pattern.compile("(?:skill level up\\s+)?(farming|mining|foraging)\\s+([0-9]+|[ivxlcdm]+)");

    static String normalize(String v) {
        String s = Normalizer.normalize(v, Normalizer.Form.NFKD).toLowerCase();
        s = MARKS.matcher(s).replaceAll("");
        s = CTRL.matcher(s).replaceAll("");
        s = OTHER.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    static Long parsePurse(List<String> lines) {
        for (String line : lines) {
            Matcher m = PURSE.matcher(line);
            if (!m.find()) continue;
            try {
                double base = Double.parseDouble(m.group(1).replace(",", ""));
                double mul = switch (m.group(2).toLowerCase()) {
                    case "k" -> 1e3;
                    case "m" -> 1e6;
                    case "b" -> 1e9;
                    default -> 1;
                };
                return (long) (base * mul);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /** [skill, level] from normalized text, or null. */
    static Object[] parseSkill(String normalized) {
        Matcher m = SKILL.matcher(normalized);
        if (!m.find()) return null;
        String lv = m.group(2);
        int level;
        try {
            level = Integer.parseInt(lv);
        } catch (NumberFormatException e) {
            level = roman(lv);
        }
        return new Object[]{m.group(1), level};
    }

    private static int roman(String v) {
        int total = 0, prev = 0;
        for (int i = v.length() - 1; i >= 0; i--) {
            int cur = switch (v.charAt(i)) {
                case 'i' -> 1;
                case 'v' -> 5;
                case 'x' -> 10;
                case 'l' -> 50;
                case 'c' -> 100;
                case 'd' -> 500;
                case 'm' -> 1000;
                default -> 0;
            };
            if (cur == 0) return 0;
            if (cur < prev) total -= cur;
            else total += cur;
            prev = cur;
        }
        return total;
    }

    private static final Set<String> CROPS = Set.of("WHEAT", "CARROT_ITEM", "CARROT", "POTATO_ITEM", "POTATO", "PUMPKIN", "MELON", "MELON_SLICE", "SEEDS", "WHEAT_SEEDS");
    private static final Set<String> WHITE_WOOL = Set.of("WOOL", "WHITE_WOOL", "white wool");
    private static final Set<String> STICKS = Set.of("STICK", "stick");
    private static final Set<String> ROGUE_SWORD = Set.of("ROGUE_SWORD", "rogue sword");
    private static final Set<String> XP_BOTTLES = Set.of("EXP_BOTTLE", "EXPERIENCE_BOTTLE", "experience bottle");
    private static final Set<String> SAND = Set.of("SAND", "sand");
    private static final Set<String> OAK = Set.of("LOG", "OAK_LOG", "oak log");
    private static final Set<String> BIRCH = Set.of("LOG:2", "BIRCH_LOG", "birch log");
    private static final Set<String> SPRUCE = Set.of("LOG:1", "SPRUCE_LOG", "spruce log");
    private static final Set<String> DARK_OAK = Set.of("LOG_2:1", "DARK_OAK_LOG", "dark oak log");
    private static final Set<String> ACACIA = Set.of("LOG_2", "ACACIA_LOG", "acacia log");
    private static final List<Set<String>> ROUTE_LOGS = List.of(OAK, BIRCH, SPRUCE, DARK_OAK, ACACIA);
    private static final List<String> HARP_SONGS = List.of("hymn to the joy", "frere jacques", "amazing grace",
            "brahm's lullaby", "happy birthday to you", "greensleeves", "geothermy", "minuet", "joy to the world",
            "godly imagination", "la vie en rose");

    private static final Map<String, String> NORM = new java.util.concurrent.ConcurrentHashMap<>();

    private static String n(String term) {
        return NORM.computeIfAbsent(term, Level7Route::normalize);
    }

    static boolean isComplete(int n, Obs o, Base b) {
        P p = new P(o, b);
        return switch (n) {
            case 1 -> p.count(CROPS) > 0;
            case 2 -> p.sold(CROPS, "alchemist");
            case 3 -> p.near("wool weaver") || p.screen("wool weaver");
            case 4 -> p.screen("wool weaver");
            case 5 -> p.count(WHITE_WOOL) >= 64;
            case 6 -> p.near("carpenter") || p.chat("carpenter");
            case 7 -> p.chatAll("carpenter", "carpentry") || p.decreased(WHITE_WOOL);
            case 8 -> p.near("lumber merchant") || p.screen("lumber merchant", "lumberjack");
            case 9 -> p.screen("lumber merchant", "lumberjack");
            case 10 -> p.count(STICKS) >= 32 && p.hasAll("rookie axe", "promising axe", "sweet axe", "efficient axe");
            case 11 -> p.near("weaponsmith") || p.screen("weaponsmith");
            case 12 -> p.screen("weaponsmith");
            case 13 -> p.hasAll("undead sword", "end sword", "spider sword", "wither bow");
            case 14 -> p.near("blacksmith") || p.chat("blacksmith");
            case 15 -> p.chat("[npc] blacksmith", "blacksmith:") || p.screen("reforge", "blacksmith");
            case 16 -> p.near("mine merchant", "mining merchant") || p.screen("mine merchant", "mining merchant");
            case 17 -> p.screen("mine merchant", "mining merchant");
            case 18 -> p.hasAll("rookie pickaxe", "promising pickaxe") && p.units("coal") >= 18 && p.units("gold") >= 19;
            case 19 -> p.hasIdOrName(Set.of("GOLDEN_SHOVEL"), "golden shovel");
            case 20, 34, 45, 48, 105, 112 -> o.teleported() && p.location("hub", "village");
            case 21 -> p.near("bea") || p.screen("bea");
            case 22 -> p.screen("bea");
            case 23 -> p.hasIdOrName(Set.of("BEE;0"), "bee");
            case 24 -> p.chatAll("bee", "pet menu") || p.chatAll("bee", "added") || (p.screenItem("bee") && !p.hasBee());
            case 25 -> p.screen("pets") && p.screenItem("bee");
            case 26 -> p.chatAll("bee", "summon") || p.chatAll("bee", "equipped") || p.screenLore("bee", "selected pet", "click to despawn");
            case 27 -> p.near("librarian") || p.screen("librarian");
            case 28 -> p.chat("[npc] librarian", "librarian:");
            case 29 -> p.screen("librarian");
            case 30 -> p.count(XP_BOTTLES) >= 128;
            case 31 -> o.xpLevel() > b.xpLevel() || p.decreased(XP_BOTTLES);
            case 32 -> o.xpLevel() >= 20;
            case 33 -> o.inventory().stream().anyMatch(i -> i.enchanted() && i.name().contains("golden shovel")) || p.chat("enchanted");
            case 35 -> p.hasIdOrName(ROGUE_SWORD, "rogue sword");
            case 36 -> p.chat("madame eleanor", "golds worth", "goldsworth") || p.screen("museum");
            case 37 -> (p.screen("museum") && p.decreased(ROGUE_SWORD)) || p.chatAll("rogue sword", "donat");
            case 38 -> p.hasIdOrName(Set.of("ROGUE_SWORD"), "rogue sword");
            case 39 -> p.skill("farming", 10);
            case 40 -> p.sold(CROPS, "alchemist") || (p.screen("alchemist") && p.count(CROPS) == 0);
            case 41 -> p.location("the barn", "barn");
            case 42 -> p.location("mushroom desert");
            case 43 -> p.count(SAND) > p.baseCount(SAND) || p.chat("sand");
            case 44 -> p.skill("mining", 12);
            case 46 -> p.sold(SAND, "alchemist");
            case 47 -> o.treecapBin() != null && o.treecapBin() > 0 && (o.purse() == null ? 0 : o.purse()) >= o.treecapBin();
            case 49 -> p.near("auction agent", "auction master", "auctioneer") || p.location("auction house") || p.screen("auction");
            case 50 -> p.hasIdOrName(Set.of("TREECAPITATOR", "TREECAPITATOR_AXE"), "treecapitator");
            case 51 -> p.held("treecapitator", "TREECAPITATOR", "TREECAPITATOR_AXE");
            case 52 -> p.near("lumber jack", "lumberjack") || p.chat("lumber jack", "lumberjack");
            case 53 -> p.chat("[npc] lumber jack", "lumber jack:", "[npc] lumberjack", "lumberjack:");
            case 54 -> p.count(OAK) >= 20;
            case 55 -> p.near("lumber jack", "lumberjack");
            case 56 -> p.decreased(OAK) && p.nearOrChat("lumber jack", "lumberjack");
            case 57 -> p.chatAll("claim", "axe") || p.nameIncreased("promising axe", "iron axe");
            case 58 -> p.location("the park", "birch park");
            case 59 -> p.near("charlie") || p.chat("charlie");
            case 60 -> p.chat("[npc] charlie", "charlie:");
            case 61 -> p.count(BIRCH) >= 64;
            case 62 -> p.near("charlie");
            case 63 -> p.decreased(BIRCH) && p.nearOrChat("charlie");
            case 64 -> p.chatAll("charlie", "reward") || p.screen("claim reward");
            case 65 -> p.hasIdOrName(Set.of("CHARLIE_TROUSERS"), "charlie's trousers", "charlies trousers");
            case 66 -> p.equipped("legs", "charlie") && p.routeLogs() == 0;
            case 67 -> p.chat("into the woods", "quest complete");
            case 68 -> p.hasName("travel scroll to the park") || p.chatAll("travel scroll", "park");
            case 69 -> p.location("spruce woods");
            case 70 -> p.near("kelly") || p.chat("kelly");
            case 71 -> p.chat("[npc] kelly", "kelly:");
            case 72 -> p.count(SPRUCE) >= 128;
            case 73 -> p.near("kelly");
            case 74 -> p.decreased(SPRUCE) && p.nearOrChat("kelly");
            case 75 -> p.hasIdOrName(Set.of("KELLY_TSHIRT"), "kelly's t-shirt", "kellys t-shirt");
            case 76 -> p.equipped("chest", "kelly") && p.routeLogs() == 0;
            case 77 -> p.location("dark thicket");
            case 78 -> o.sneaking() && p.near("ryan");
            case 79 -> p.chatAll("cult", "join") || p.chatAll("cult", "welcome");
            case 80 -> p.chat("what?", "what?!", "acknowledge");
            case 81 -> p.chatAll("survived", "campfire") || p.chatAll("trial of fire", "complete");
            case 82 -> p.near("ryan");
            case 83 -> p.chatAll("ryan", "dark oak") || p.chatAll("ryan", "logs");
            case 84 -> p.count(DARK_OAK) >= 256;
            case 85 -> p.near("ryan");
            case 86 -> p.decreased(DARK_OAK) && p.nearOrChat("ryan");
            case 87 -> p.hasIdOrName(Set.of("CAMPFIRE_TALISMAN_1", "CAMPFIRE_INITIATE_BADGE"), "campfire initiate badge")
                    || p.chatAll("campfire initiate badge", "here");
            case 88 -> p.location("savanna woodland", "savanna");
            case 89 -> p.near("melody") || p.chat("melody");
            case 90 -> p.chat("[npc] melody", "melody :", "melody:");
            case 91 -> p.count(ACACIA) >= 512;
            case 92 -> p.near("melody");
            case 93 -> p.decreased(ACACIA) && p.nearOrChat("melody");
            case 94 -> p.hasIdOrName(Set.of("MELODY_SHOES"), "melody's shoes", "melodys shoes");
            case 95 -> p.equipped("feet", "melody");
            case 96 -> p.screen("harp", "melody") || p.chat("pick a song");
            case 97 -> p.screen("harp", "melody") && o.screenItems().size() >= 7;
            case 98 -> p.harpDone() || p.hasIdOrName(Set.of("MELODY_HAIR"), "melody's hair") || p.chatAll("every song", "perfect");
            case 99 -> p.location("jungle island", "jungle");
            case 100 -> p.skill("foraging", 12);
            case 101 -> p.location("galatea");
            case 102 -> o.elapsedMs() >= 2500 && o.teleported() && p.location("hub", "village");
            case 103 -> (o.purse() == null ? 0 : o.purse()) >= 100_000L;
            case 104 -> p.soldAllAt("alchemist");
            case 106 -> p.near("alda") || p.screen("alda");
            case 107 -> p.chat("[npc] alda", "alda:") || p.screen("alda");
            case 108 -> p.hasIdOrName(Set.of("ABIPHONE_BASIC"), "abiphone basic");
            case 109 -> p.contact("alda");
            case 110 -> p.near("taylor") || p.contact("taylor");
            case 111 -> p.contact("taylor");
            case 113 -> p.near("elizabeth") || p.contact("elizabeth");
            case 114 -> p.contact("elizabeth");
            case 115 -> o.elapsedMs() >= 2000 && (p.near("seraphine", "elizabeth") || p.location("community center"));
            case 116 -> p.near("clerk seraphine", "seraphine") || p.contact("seraphine");
            case 117 -> p.contact("clerk seraphine", "seraphine");
            case 118 -> p.near("plumber joe") || p.contact("plumber joe");
            case 119 -> p.contact("plumber joe");
            default -> false;
        };
    }

    // all Obs strings arrive normalized; terms here are already lowercase and plain
    private record P(Obs o, Base b) {
        boolean chat(String... terms) {
            for (String t : terms) if (o.chat().contains(n(t))) return true;
            return false;
        }

        boolean chatAll(String... terms) {
            for (String t : terms) if (!o.chat().contains(n(t))) return false;
            return true;
        }

        boolean screen(String... terms) {
            for (String t : terms) if (o.title().contains(n(t))) return true;
            return false;
        }

        boolean screenItem(String... terms) {
            for (Item i : o.screenItems()) for (String t : terms) if (i.name().contains(n(t))) return true;
            return false;
        }

        boolean screenLore(String item, String... lore) {
            for (Item i : o.screenItems()) {
                if (!i.name().contains(n(item))) continue;
                for (String l : i.lore()) for (String t : lore) if (l.contains(n(t))) return true;
            }
            return false;
        }

        boolean near(String... terms) {
            for (String nb : o.nearby()) for (String t : terms) if (nb.contains(n(t))) return true;
            return false;
        }

        boolean nearOrChat(String... terms) {
            return near(terms) || chat(terms);
        }

        boolean location(String... terms) {
            for (String l : o.scoreboard()) for (String t : terms) if (l.contains(n(t))) return true;
            return false;
        }

        boolean skill(String name, int target) {
            return o.skills().getOrDefault(name, 0) >= target;
        }

        private static boolean matches(Item i, Set<String> set) {
            for (String token : set) {
                if (token.equalsIgnoreCase(i.id())) return true;
                if (i.name().contains(n(token.replace('_', ' ')))) return true;
            }
            return false;
        }

        int count(Set<String> set) {
            int n = 0;
            for (Item i : o.inventory()) if (matches(i, set)) n += i.count();
            return n;
        }

        int baseCount(Set<String> set) {
            int n = 0;
            for (Item i : b.inventory()) if (matches(i, set)) n += i.count();
            return n;
        }

        boolean decreased(Set<String> set) {
            return baseCount(set) > count(set);
        }

        boolean sold(Set<String> set, String shop) {
            return decreased(set) && (screen(shop) || chat("sold", shop));
        }

        boolean hasName(String... terms) {
            for (Item i : o.inventory()) for (String t : terms) if (i.name().contains(n(t))) return true;
            return false;
        }

        boolean hasAll(String... terms) {
            outer:
            for (String t : terms) {
                for (Item i : o.inventory()) {
                    if (i.name().equals(n(t)) || i.id().replace('_', ' ').toLowerCase().equals(t)) continue outer;
                }
                return false;
            }
            return true;
        }

        int units(String material) {
            String up = material.toUpperCase();
            int n = 0;
            for (Item i : o.inventory()) {
                String id = i.id().toUpperCase();
                if (id.equals(up + "_BLOCK") || i.name().contains("block of " + material)) n += i.count() * 9;
                else if (id.equals(up) || id.equals(up + "_INGOT") || i.name().equals(material) || i.name().equals(material + " ingot"))
                    n += i.count();
            }
            return n;
        }

        boolean nameIncreased(String... terms) {
            return nameCount(o.inventory(), terms) > nameCount(b.inventory(), terms);
        }

        private static int nameCount(List<Item> items, String... terms) {
            int n = 0;
            for (Item i : items) for (String t : terms) if (i.name().contains(n(t))) {
                n += i.count();
                break;
            }
            return n;
        }

        boolean hasIdOrName(Set<String> ids, String... names) {
            for (Item i : o.inventory()) {
                if (ids.contains(i.id().toUpperCase())) return true;
                for (String t : names) if (i.name().contains(n(t))) return true;
            }
            return false;
        }

        boolean held(String... terms) {
            Item h = o.held();
            if (h == null) return false;
            for (String t : terms) if (h.name().contains(n(t)) || t.equalsIgnoreCase(h.id())) return true;
            return false;
        }

        boolean equipped(String slot, String term) {
            Item a = o.armor().get(slot);
            return a != null && (a.name().contains(n(term)) || a.id().toLowerCase().contains(term));
        }

        boolean hasBee() {
            for (Item i : o.inventory()) if (i.id().toUpperCase().startsWith("BEE;") || i.name().contains("bee")) return true;
            return false;
        }

        boolean contact(String... names) {
            boolean chatMatch = false;
            for (String nm : names) if (o.chat().contains(n(nm))) chatMatch = true;
            chatMatch = chatMatch && o.chat().contains("contact") && (o.chat().contains("added") || o.chat().contains("already"));
            boolean screenMatch = screen("abiphone", "contacts") && screenItem(names);
            return chatMatch || screenMatch;
        }

        boolean harpDone() {
            int done = 0;
            for (Item i : o.screenItems()) {
                boolean song = false;
                for (String s : HARP_SONGS) if (i.name().contains(s)) song = true;
                if (!song) continue;
                for (String l : i.lore()) {
                    Matcher m = PERCENT.matcher(l);
                    if (m.find() && Integer.parseInt(m.group(1)) >= 90) {
                        done++;
                        break;
                    }
                }
            }
            return done >= HARP_SONGS.size();
        }

        int routeLogs() {
            int n = 0;
            for (Set<String> s : ROUTE_LOGS) n += count(s);
            return n;
        }

        boolean soldAllAt(String shop) {
            int now = 0, base = 0;
            for (Item i : o.inventory()) now += i.count();
            for (Item i : b.inventory()) base += i.count();
            long bp = b.purse() == null ? Long.MAX_VALUE : b.purse();
            return (screen(shop) || chat(shop)) && (now < base || (o.purse() == null ? 0 : o.purse()) > bp);
        }
    }
}
