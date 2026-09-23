package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ChatCategory {

    private ChatCategory() {}

    private static final String[] MORE = {"chatFilterBossAll", "chatFilterLobby", "chatFilterWelcome", "chatFilterFriendJoin", "chatFilterWarpMore", "chatFilterBzAhMinis", "chatFilterSlayerSpam", "chatFilterUselessWarnings", "chatFilterUselessNotifications", "chatFilterFireSale", "chatFilterEventLevel", "chatFilterHoppity", "chatFilterChocolateFactory", "chatFilterSacrifice", "chatFilterNoPests", "chatFilterLegacyItems", "chatFilterParkour", "chatFilterTeleportPads", "chatFilterChefTed", "chatFilterRewardBundles", "chatFilterWinterGift", "chatFilterAnita", "chatFilterRareRewards", "chatFilterSoloClass", "chatFilterFairy"};

    private static void groupAction(ConfigBuilder b, TeslaMapsConfig config, String group, String label) {
        b.dropdown(label, com.teslamaps.features.ChatFilter.GROUP_ACTIONS,
                () -> config.chatFilterGroupActions.getOrDefault(group, "Default"),
                v -> { if (v.equals("Default")) config.chatFilterGroupActions.remove(group); else config.chatFilterGroupActions.put(group, v); });
    }

    private static void toggleAll(TeslaMapsConfig config, String[] fields) {
        try {
            int on = 0;
            for (String f : fields) if (TeslaMapsConfig.class.getField(f).getBoolean(config)) on++;
            boolean nv = on <= fields.length / 2;
            if (nv) config.chatFilterEnabled = true;
            for (String f : fields) TeslaMapsConfig.class.getField(f).setBoolean(config, nv);
        } catch (ReflectiveOperationException ignored) {}
        TeslaMapsConfig.save();
    }

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Chat QoL");
        b.toggle("Infinite Chat History", () -> config.infiniteChat, v -> config.infiniteChat = v);
        b.slider("  History Cap (lines)", 1024f, 32768f,
                () -> (float) config.chatHistoryLimit, v -> config.chatHistoryLimit = v.intValue());
        b.toggle("Keep History Across Restarts", () -> config.chatHistoryPersist, v -> config.chatHistoryPersist = v);
        b.info("Saves the chat on quit and restores it on the next launch");
        b.toggle("Chat Tabs (All/Guild/DM/Party)", () -> config.chatTabs, v -> config.chatTabs = v);
        b.info("Typing in a tab sends to that channel. Everything stays stored");
        String[] tabPositions = {"Above Chat", "Under Chat", "Right of Chat"};
        b.dropdown("  Tab Position",
                tabPositions,
                () -> tabPositions[Math.max(0, Math.min(2, config.chatTabPosition))],
                v -> {
                    for (int i = 0; i < tabPositions.length; i++) {
                        if (tabPositions[i].equals(v)) config.chatTabPosition = i;
                    }
                });
        b.toggle("Stack Duplicate Messages", () -> config.chatStacking, v -> config.chatStacking = v);
        b.slider("  Stack Window (min)", 1f, 30f,
                () -> (float) config.chatStackWindowMinutes, v -> config.chatStackWindowMinutes = Math.round(v));
        b.toggle("  Stack Divider Blocks (---/===/▬▬▬)", () -> config.chatStackBlocks, v -> config.chatStackBlocks = v);
        b.toggle("  Colours Must Match", () -> config.chatStackCompareColors, v -> config.chatStackCompareColors = v);
        b.toggle("Right-Click Copy Chat", () -> config.chatCopyEnabled, v -> config.chatCopyEnabled = v);
        b.slider("Hotkey Delay After Chat (ms)", 0f, 1000f,
                () -> (float) config.hotkeyChatDelay, v -> config.hotkeyChatDelay = Math.round(v));
        b.toggle("Gradient [TeslaMaps] Prefix", () -> config.chromaPrefix, v -> config.chromaPrefix = v);
        b.color("Prefix Gradient Color A", () -> config.prefixFadeColorA, v -> config.prefixFadeColorA = v);
        b.color("Prefix Gradient Color B", () -> config.prefixFadeColorB, v -> config.prefixFadeColorB = v);
        b.label("Filter");
        b.toggle("Enable Chat Filter", () -> config.chatFilterEnabled, v -> config.chatFilterEnabled = v);
        b.dropdown("Filter Action", com.teslamaps.features.ChatFilter.ACTIONS,
                () -> config.chatFilterAction, v -> config.chatFilterAction = v);
        b.info("Grey dims the line, Compact shortens it (full text on hover), Redirect moves it out of chat");
        String[] acts = com.teslamaps.features.ChatFilter.ACTIONS;
        Runnable[] setAll = new Runnable[acts.length];
        for (int i = 0; i < acts.length; i++) {
            String a = acts[i];
            setAll[i] = () -> { config.chatFilterAction = a; config.chatFilterGroupActions.clear(); TeslaMapsConfig.save(); };
        }
        b.buttonRow(new String[]{"All: Hide", "All: Grey", "All: Compact", "All: Compact+Grey", "All: Redirect"}, setAll);
        b.dropdown("  Redirect To", com.teslamaps.features.ChatSpamHud.TARGETS,
                () -> config.chatRedirectTarget, v -> config.chatRedirectTarget = v);
        b.slider("  Redirect Toast Seconds", 1f, 15f, () -> config.chatRedirectSeconds, v -> config.chatRedirectSeconds = v, 1);
        b.info("Redirect moves the line out of chat into a movable toast list (/tmap gui)");
        b.toggle("Never Hide Clickable Lines", () -> config.chatFilterKeepClickable, v -> config.chatFilterKeepClickable = v);
        b.button("Toggle All Filters", () -> {
            int enabled = 0, total = 26;
            if (config.chatFilterEmpty) enabled++;
            if (config.chatFilterBonePlating) enabled++;
            if (config.chatFilterWatcher) enabled++;
            if (config.chatFilterF4Boss) enabled++;
            if (config.chatFilterBlessings) enabled++;
            if (config.chatFilterEssence) enabled++;
            if (config.chatFilterKeys) enabled++;
            if (config.chatFilterReviveStone) enabled++;
            if (config.chatFilterDoors) enabled++;
            if (config.chatFilterWish) enabled++;
            if (config.chatFilterPickups) enabled++;
            if (config.chatFilterBlocksInWay) enabled++;
            if (config.chatFilterUltReady) enabled++;
            if (config.chatFilterAoeDamage) enabled++;
            if (config.chatFilterGuildXp) enabled++;
            if (config.chatFilterKillCombo) enabled++;
            if (config.chatFilterStash) enabled++;
            if (config.chatFilterServerMsgs) enabled++;
            if (config.chatFilterProfileInfo) enabled++;
            if (config.chatFilterPerkBuffs) enabled++;
            if (config.chatFilterOruo) enabled++;
            if (config.chatFilterSacks) enabled++;
            if (config.chatFilterGfs) enabled++;
            if (config.chatFilterWatchdog) enabled++;
            if (config.chatFilterMort) enabled++;
            if (config.chatFilterMilestone) enabled++;
            boolean nv = enabled <= total / 2; // mostly off -> turn all on, else all off
            if (nv) config.chatFilterEnabled = true; // make sure the master switch is on when enabling all
            config.chatFilterWatcher = nv; config.chatFilterF4Boss = nv; config.chatFilterBlessings = nv;
            config.chatFilterEssence = nv; config.chatFilterKeys = nv; config.chatFilterReviveStone = nv; config.chatFilterDoors = nv;
            config.chatFilterWish = nv; config.chatFilterPickups = nv; config.chatFilterBlocksInWay = nv;
            config.chatFilterUltReady = nv; config.chatFilterAoeDamage = nv; config.chatFilterGuildXp = nv;
            config.chatFilterKillCombo = nv; config.chatFilterStash = nv; config.chatFilterServerMsgs = nv;
            config.chatFilterProfileInfo = nv; config.chatFilterPerkBuffs = nv; config.chatFilterOruo = nv;
            config.chatFilterSacks = nv; config.chatFilterGfs = nv; config.chatFilterWatchdog = nv; config.chatFilterMort = nv; config.chatFilterEmpty = nv; config.chatFilterBonePlating = nv;
            config.chatFilterMilestone = nv;
            TeslaMapsConfig.save();
        });
        groupAction(b, config, com.teslamaps.features.ChatFilter.G_DUNGEON, "Dungeon Filter Action");
        b.toggle("Hide Empty Lines", () -> config.chatFilterEmpty, v -> config.chatFilterEmpty = v);
        b.toggle("Hide Watcher Messages", () -> config.chatFilterWatcher, v -> config.chatFilterWatcher = v);
        b.toggle("Hide F4/M4 Boss (Thorn)", () -> config.chatFilterF4Boss, v -> config.chatFilterF4Boss = v);
        b.toggle("Hide Blessings", () -> config.chatFilterBlessings, v -> config.chatFilterBlessings = v);
        b.toggle("Hide Essence Messages", () -> config.chatFilterEssence, v -> config.chatFilterEssence = v);
        b.toggle("Hide Key Pickups", () -> config.chatFilterKeys, v -> config.chatFilterKeys = v);
        b.toggle("Hide Revive Stone Obtained", () -> config.chatFilterReviveStone, v -> config.chatFilterReviveStone = v);
        b.toggle("Hide Door Opens", () -> config.chatFilterDoors, v -> config.chatFilterDoors = v);
        b.toggle("Hide Wish Heal Messages", () -> config.chatFilterWish, v -> config.chatFilterWish = v);
        b.toggle("Hide All \"picked up\"", () -> config.chatFilterPickups, v -> config.chatFilterPickups = v);
        b.label("Combat / Misc");
        groupAction(b, config, com.teslamaps.features.ChatFilter.G_COMBAT, "  Combat Filter Action");
        b.toggle("Hide \"Blocks in the way\"", () -> config.chatFilterBlocksInWay, v -> config.chatFilterBlocksInWay = v);
        b.toggle("Hide Ult Ready (DROP to activate)", () -> config.chatFilterUltReady, v -> config.chatFilterUltReady = v);
        b.toggle("Hide AOE Damage Messages", () -> config.chatFilterAoeDamage, v -> config.chatFilterAoeDamage = v);
        b.toggle("Hide Guild XP (GEXP)", () -> config.chatFilterGuildXp, v -> config.chatFilterGuildXp = v);
        b.toggle("Hide Kill Combo", () -> config.chatFilterKillCombo, v -> config.chatFilterKillCombo = v);
        b.label("Spam / Info");
        groupAction(b, config, com.teslamaps.features.ChatFilter.G_SPAM, "  Spam Filter Action");
        b.toggle("Hide Stash Messages", () -> config.chatFilterStash, v -> config.chatFilterStash = v);
        b.toggle("Compact Stash Warnings", () -> config.stashCompact, v -> config.stashCompact = v);
        b.info("One clickable line instead of the block (wins over Hide Stash Messages)");
        b.toggle("  Hide Repeated Counts", () -> config.stashHideDuplicates, v -> config.stashHideDuplicates = v);
        b.toggle("    Repeat Once Per World", () -> config.stashDuplicatesPerWorld, v -> config.stashDuplicatesPerWorld = v);
        b.toggle("  Hide \"Added To Stash\"", () -> config.stashHideAdded, v -> config.stashHideAdded = v);
        b.slider("  Hide Warnings Up To", 0f, 10000f, () -> (float) config.stashMinCount, v -> config.stashMinCount = Math.round(v));
        b.toggle("  Click Opens /viewstash", () -> config.stashUseViewStash, v -> config.stashUseViewStash = v);
        b.toggle("Hide Server Messages", () -> config.chatFilterServerMsgs, v -> config.chatFilterServerMsgs = v);
        b.toggle("Hide Profile Info", () -> config.chatFilterProfileInfo, v -> config.chatFilterProfileInfo = v);
        b.toggle("Hide HOTF/HOTM Perks", () -> config.chatFilterPerkBuffs, v -> config.chatFilterPerkBuffs = v);
        b.toggle("  Keep Sky Mall/Lottery On Its Island", () -> config.chatFilterPerkBuffsElsewhere, v -> config.chatFilterPerkBuffsElsewhere = v);
        b.toggle("Hide Bone Plating", () -> config.chatFilterBonePlating, v -> config.chatFilterBonePlating = v);
        b.toggle("Hide Oruo Messages", () -> config.chatFilterOruo, v -> config.chatFilterOruo = v);
        b.toggle("Hide Wither King Heroes Line", () -> config.chatFilterWitherKingHeroes, v -> config.chatFilterWitherKingHeroes = v);
        b.toggle("Hide Sacks", () -> config.chatFilterSacks, v -> config.chatFilterSacks = v);
        b.toggle("Hide GFS (Sacks to inventory)", () -> config.chatFilterGfs, v -> config.chatFilterGfs = v);
        b.toggle("Hide Watchdog Announcements", () -> config.chatFilterWatchdog, v -> config.chatFilterWatchdog = v);
        b.toggle("Hide Mort Messages", () -> config.chatFilterMort, v -> config.chatFilterMort = v);
        b.toggle("Hide Milestones", () -> config.chatFilterMilestone, v -> config.chatFilterMilestone = v);
        b.label("Own Filters");
        b.dropdown("Own Filter Action", com.teslamaps.features.ChatFilter.ACTIONS,
                () -> config.chatUserFilterAction, v -> config.chatUserFilterAction = v);
        b.keybind("Filter Hovered Line Key", () -> config.chatFilterLineKey, v -> config.chatFilterLineKey = v);
        b.info("Press it over a line in open chat to hide that exact line");
        b.info("/tmap chatfilter <contains|equals|startswith|endswith|regex> <text>");
        b.button("List Own Filters In Chat", () -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[TeslaMaps] §f" + config.chatUserFilters.size() + " own chat filters"));
            for (int i = 0; i < config.chatUserFilters.size(); i++) {
                var f = config.chatUserFilters.get(i);
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7" + (i + 1) + ". §e" + f.mode + " §f" + f.text));
            }
        });
        b.label("More Filters");
        b.button("Toggle All More Filters", () -> toggleAll(config, MORE));
        groupAction(b, config, com.teslamaps.features.ChatFilter.G_MORE, "  More Filter Action");
        b.toggle("Hide All [BOSS] Lines", () -> config.chatFilterBossAll, v -> config.chatFilterBossAll = v);
        b.toggle("Hide Lobby Messages", () -> config.chatFilterLobby, v -> config.chatFilterLobby = v);
        b.toggle("Hide Welcome Message", () -> config.chatFilterWelcome, v -> config.chatFilterWelcome = v);
        b.toggle("Hide Friend/Guild Join & Leave", () -> config.chatFilterFriendJoin, v -> config.chatFilterFriendJoin = v);
        b.toggle("Hide More Warp/Visit Messages", () -> config.chatFilterWarpMore, v -> config.chatFilterWarpMore = v);
        b.toggle("Hide Bazaar/AH Progress Lines", () -> config.chatFilterBzAhMinis, v -> config.chatFilterBzAhMinis = v);
        b.toggle("Hide Slayer Quest Spam", () -> config.chatFilterSlayerSpam, v -> config.chatFilterSlayerSpam = v);
        b.toggle("Hide Useless Warnings", () -> config.chatFilterUselessWarnings, v -> config.chatFilterUselessWarnings = v);
        b.toggle("Hide Useless Notifications", () -> config.chatFilterUselessNotifications, v -> config.chatFilterUselessNotifications = v);
        b.toggle("Hide Fire Sales", () -> config.chatFilterFireSale, v -> config.chatFilterFireSale = v);
        b.toggle("Hide Event Level-Ups", () -> config.chatFilterEventLevel, v -> config.chatFilterEventLevel = v);
        b.toggle("Hide Hoppity Egg Spawns", () -> config.chatFilterHoppity, v -> config.chatFilterHoppity = v);
        b.toggle("Hide Chocolate Factory Upgrades", () -> config.chatFilterChocolateFactory, v -> config.chatFilterChocolateFactory = v);
        b.toggle("Hide Others' Sacrifices", () -> config.chatFilterSacrifice, v -> config.chatFilterSacrifice = v);
        b.toggle("Hide \"No Pests\" Line", () -> config.chatFilterNoPests, v -> config.chatFilterNoPests = v);
        b.toggle("Hide Legacy Items Warning", () -> config.chatFilterLegacyItems, v -> config.chatFilterLegacyItems = v);
        b.toggle("Hide Parkour Messages", () -> config.chatFilterParkour, v -> config.chatFilterParkour = v);
        b.toggle("Hide Teleport Pad Messages", () -> config.chatFilterTeleportPads, v -> config.chatFilterTeleportPads = v);
        b.toggle("Hide Chef Ted Donations", () -> config.chatFilterChefTed, v -> config.chatFilterChefTed = v);
        b.toggle("Hide Reward Reminders", () -> config.chatFilterRewardBundles, v -> config.chatFilterRewardBundles = v);
        b.toggle("Hide Gift Rewards", () -> config.chatFilterWinterGift, v -> config.chatFilterWinterGift = v);
        b.toggle("Hide Anita Fortune (outside Garden)", () -> config.chatFilterAnita, v -> config.chatFilterAnita = v);
        b.toggle("Hide Dungeon Rare Rewards (others)", () -> config.chatFilterRareRewards, v -> config.chatFilterRareRewards = v);
        b.toggle("Hide Solo Class Messages", () -> config.chatFilterSoloClass, v -> config.chatFilterSoloClass = v);
        b.toggle("Hide Fairy Messages", () -> config.chatFilterFairy, v -> config.chatFilterFairy = v);
        b.label("Command Suggestions");
        b.toggle("Suggest /warp and /call targets", () -> config.commandSuggestions, v -> config.commandSuggestions = v);
        b.toggle("  Suggest Players (/msg, /visit, ...)", () -> config.commandSuggestionsPlayers, v -> config.commandSuggestionsPlayers = v);
        b.info("Run /g list and /f list once to fill the name list");
        b.info("Player suggestions need a rejoin, and can clash with Hypixel's own commands");
        b.info("Hypixel sends no suggestions for these two");
    }
}
