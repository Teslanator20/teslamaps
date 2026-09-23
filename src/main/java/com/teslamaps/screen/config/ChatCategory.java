package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ChatCategory {

    private ChatCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Chat QoL");
        b.toggle("Infinite Chat History", () -> config.infiniteChat, v -> config.infiniteChat = v);
        b.slider("History Cap (lines)", 1024f, 32768f,
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
        b.slider("Stack Window (min)", 1f, 30f,
                () -> (float) config.chatStackWindowMinutes, v -> config.chatStackWindowMinutes = Math.round(v));
        b.toggle("Right-Click Copy Chat", () -> config.chatCopyEnabled, v -> config.chatCopyEnabled = v);
        b.slider("Hotkey Delay After Chat (ms)", 0f, 1000f,
                () -> (float) config.hotkeyChatDelay, v -> config.hotkeyChatDelay = Math.round(v));
        b.toggle("Gradient [TeslaMaps] Prefix", () -> config.chromaPrefix, v -> config.chromaPrefix = v);
        b.color("Prefix Gradient Color A", () -> config.prefixFadeColorA, v -> config.prefixFadeColorA = v);
        b.color("Prefix Gradient Color B", () -> config.prefixFadeColorB, v -> config.prefixFadeColorB = v);
        b.label("Filter");
        b.toggle("Enable Chat Filter", () -> config.chatFilterEnabled, v -> config.chatFilterEnabled = v);
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
        b.toggle("Hide \"Blocks in the way\"", () -> config.chatFilterBlocksInWay, v -> config.chatFilterBlocksInWay = v);
        b.toggle("Hide Ult Ready (DROP to activate)", () -> config.chatFilterUltReady, v -> config.chatFilterUltReady = v);
        b.toggle("Hide AOE Damage Messages", () -> config.chatFilterAoeDamage, v -> config.chatFilterAoeDamage = v);
        b.toggle("Hide Guild XP (GEXP)", () -> config.chatFilterGuildXp, v -> config.chatFilterGuildXp = v);
        b.toggle("Hide Kill Combo", () -> config.chatFilterKillCombo, v -> config.chatFilterKillCombo = v);
        b.label("Spam / Info");
        b.toggle("Hide Stash Messages", () -> config.chatFilterStash, v -> config.chatFilterStash = v);
        b.toggle("Hide Server Messages", () -> config.chatFilterServerMsgs, v -> config.chatFilterServerMsgs = v);
        b.toggle("Hide Profile Info", () -> config.chatFilterProfileInfo, v -> config.chatFilterProfileInfo = v);
        b.toggle("Hide HOTF/HOTM Perks", () -> config.chatFilterPerkBuffs, v -> config.chatFilterPerkBuffs = v);
        b.toggle("Hide Bone Plating", () -> config.chatFilterBonePlating, v -> config.chatFilterBonePlating = v);
        b.toggle("Hide Oruo Messages", () -> config.chatFilterOruo, v -> config.chatFilterOruo = v);
        b.toggle("Hide Wither King Heroes Line", () -> config.chatFilterWitherKingHeroes, v -> config.chatFilterWitherKingHeroes = v);
        b.toggle("Hide Sacks", () -> config.chatFilterSacks, v -> config.chatFilterSacks = v);
        b.toggle("Hide GFS (Sacks to inventory)", () -> config.chatFilterGfs, v -> config.chatFilterGfs = v);
        b.toggle("Hide Watchdog Announcements", () -> config.chatFilterWatchdog, v -> config.chatFilterWatchdog = v);
        b.toggle("Hide Mort Messages", () -> config.chatFilterMort, v -> config.chatFilterMort = v);
        b.toggle("Hide Milestones", () -> config.chatFilterMilestone, v -> config.chatFilterMilestone = v);
        b.label("Command Suggestions");
        b.toggle("Suggest /warp and /call targets", () -> config.commandSuggestions, v -> config.commandSuggestions = v);
        b.toggle("  Suggest Players (/msg, /visit, ...)", () -> config.commandSuggestionsPlayers, v -> config.commandSuggestionsPlayers = v);
        b.info("Run /g list and /f list once to fill the name list");
        b.info("Player suggestions need a rejoin, and can clash with Hypixel's own commands");
        b.info("Hypixel sends no suggestions for these two");
    }
}
