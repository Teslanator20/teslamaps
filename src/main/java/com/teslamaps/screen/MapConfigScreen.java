/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class MapConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 26;
    private static final int SIDEBAR_WIDTH = 110;
    private static final int COLOR_PICKER_HEIGHT = 100;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private final Map<String, List<SettingsEntry>> categories = new LinkedHashMap<>();
    private final Map<String, List<String>> sections = new LinkedHashMap<>();
    private String selectedCategory = "Map";
    private String searchQuery = "";

    private int sidebarScroll = 0;
    private int sidebarMaxScroll = 0;
    private static final int SIDEBAR_TOP = 50;
    private static final int SECTION_HEADER_H = 20;
    private static final int CATEGORY_ROW_H = 24;
    private static final int SECTION_GAP = 6;
    private int sidebarBottom() { return this.height - 92; } // above the /tmap buttons

    private EditBox searchField;
    private boolean wasMouseDown = false;

    private ColorEntry expandedColorEntry = null;
    private float pickerHue = 0, pickerSat = 1, pickerBright = 1;

    private KeybindEntry listeningKeybindEntry = null;
    private SliderEntry editingSlider = null;   // slider whose value is being typed
    private String sliderEditText = "";

    private void startSliderEdit(SliderEntry s) {
        if (editingSlider != null && editingSlider != s) commitSliderEdit();
        editingSlider = s;
        sliderEditText = String.format("%." + s.decimals + "f", s.getter.get());
        if (searchField != null) searchField.setFocused(false);
    }

    private void commitSliderEdit() {
        if (editingSlider == null) return;
        try {
            float v = Float.parseFloat(sliderEditText.trim());
            v = Math.max(editingSlider.min, Math.min(editingSlider.max, v));
            editingSlider.setter.accept(v);
            TeslaMapsConfig.save();
        } catch (NumberFormatException ignored) {}
        editingSlider = null;
    }

    public MapConfigScreen() {
        super(Component.literal("TeslaMaps Settings"));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningKeybindEntry != null) {
            if (event.key() != GLFW.GLFW_KEY_ESCAPE) listeningKeybindEntry.setKey(event.key());
            listeningKeybindEntry = null;
            return true;
        }
        if (editingSlider != null) {
            int k = event.key();
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { commitSliderEdit(); return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { editingSlider = null; return true; } // cancel
            if (k == GLFW.GLFW_KEY_BACKSPACE) {
                if (!sliderEditText.isEmpty()) sliderEditText = sliderEditText.substring(0, sliderEditText.length() - 1);
                return true;
            }
            return true; // swallow other keys while editing
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (editingSlider != null) {
            char c = (char) event.codepoint();
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') sliderEditText += c;
            return true;
        }
        return super.charTyped(event);
    }

    private static String keyName(int key) {
        if (key < 0) return "Unbound";
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    @Override
    protected void init() {
        buildCategories();

        int sfX = SIDEBAR_WIDTH + 14;
        int sfW = this.width - SIDEBAR_WIDTH - 28;
        searchField = new EditBox(font, sfX + 18, 13, sfW - 26, 12, Component.literal("Search"));
        searchField.setHint(Component.literal("Search settings..."));
        searchField.setBordered(false);
        searchField.setResponder(query -> {
            searchQuery = query.toLowerCase();
            scrollOffset = 0;
            expandedColorEntry = null;
        });
        addRenderableWidget(searchField);
    }

    private void buildCategories() {
        categories.clear();
        TeslaMapsConfig config = TeslaMapsConfig.get();

        int contentWidth = this.width - SIDEBAR_WIDTH - 50;
        int contentX = SIDEBAR_WIDTH + 25;

        List<SettingsEntry> map = new ArrayList<>();
        map.add(new LabelEntry(contentX, "General"));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Map", () -> config.mapEnabled, v -> config.mapEnabled = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Only In Dungeon", () -> config.onlyShowInDungeon, v -> config.onlyShowInDungeon = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Background", () -> config.showMapBackground, v -> config.showMapBackground = v));
        map.add(new LabelEntry(contentX, "Rooms"));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Room Names", () -> config.showRoomNames, v -> config.showRoomNames = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Hide Entrance/Blood/Fairy Names", () -> config.hideEntranceBloodFairyNames, v -> config.hideEntranceBloodFairyNames = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Names Only for Puzzles", () -> config.showNamesOnlyForPuzzles, v -> config.showNamesOnlyForPuzzles = v));
        map.add(new SliderEntry(contentX, contentWidth, "Room Name Scale", 0.5f, 2.0f,
                () -> config.roomNameScale, v -> config.roomNameScale = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Secrets", () -> config.showSecretCount, v -> config.showSecretCount = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Hide 1 Secret Count", () -> config.hideOneSecretCount, v -> config.hideOneSecretCount = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Crypts", () -> config.showCrypts, v -> config.showCrypts = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Total Crypts", () -> config.showTotalCrypts, v -> config.showTotalCrypts = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Hide When Done", () -> config.hideSecretsWhenDone, v -> config.hideSecretsWhenDone = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Assume Paul Mayor", () -> config.assumePaulMayor, v -> config.assumePaulMayor = v));
        map.add(new LabelEntry(contentX, "Checkmarks"));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Checkmarks", () -> config.showCheckmarks, v -> config.showCheckmarks = v));
        map.add(new ToggleEntry(contentX, contentWidth, "White Checks", () -> config.showWhiteCheckmarks, v -> config.showWhiteCheckmarks = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Green Checks", () -> config.showGreenCheckmarks, v -> config.showGreenCheckmarks = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Failed Checks", () -> config.showFailedCheckmarks, v -> config.showFailedCheckmarks = v));
        map.add(new LabelEntry(contentX, "Players"));
        map.add(new ToggleEntry(contentX, contentWidth, "Player Markers", () -> config.showPlayerMarker, v -> config.showPlayerMarker = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Self", () -> config.showSelfMarker, v -> config.showSelfMarker = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Others", () -> config.showOtherPlayers, v -> config.showOtherPlayers = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Names", () -> config.showPlayerNames, v -> config.showPlayerNames = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Use Heads (vs Markers)", () -> config.useHeadsInsteadOfMarkers, v -> config.useHeadsInsteadOfMarkers = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Rotate Heads", () -> config.rotatePlayerHeads, v -> config.rotatePlayerHeads = v));
        map.add(new SliderEntry(contentX, contentWidth, "Head Scale", 0.5f, 2.0f,
                () -> config.playerHeadScale, v -> config.playerHeadScale = v));
        map.add(new LabelEntry(contentX, "Room Colors"));
        map.add(new ColorEntry(contentX, contentWidth, "Background", () -> config.colorBackground, v -> config.colorBackground = v));
        map.add(new ColorEntry(contentX, contentWidth, "Unexplored", () -> config.colorUnexplored, v -> config.colorUnexplored = v));
        map.add(new ColorEntry(contentX, contentWidth, "Normal", () -> config.colorNormal, v -> config.colorNormal = v));
        map.add(new ColorEntry(contentX, contentWidth, "Entrance", () -> config.colorEntrance, v -> config.colorEntrance = v));
        map.add(new ColorEntry(contentX, contentWidth, "Blood", () -> config.colorBlood, v -> config.colorBlood = v));
        map.add(new ColorEntry(contentX, contentWidth, "Trap", () -> config.colorTrap, v -> config.colorTrap = v));
        map.add(new ColorEntry(contentX, contentWidth, "Puzzle", () -> config.colorPuzzle, v -> config.colorPuzzle = v));
        map.add(new ColorEntry(contentX, contentWidth, "Fairy", () -> config.colorFairy, v -> config.colorFairy = v));
        map.add(new ColorEntry(contentX, contentWidth, "Miniboss", () -> config.colorMiniboss, v -> config.colorMiniboss = v));
        map.add(new LabelEntry(contentX, "Door Colors"));
        map.add(new ColorEntry(contentX, contentWidth, "Normal Door", () -> config.colorDoorNormal, v -> config.colorDoorNormal = v));
        map.add(new ColorEntry(contentX, contentWidth, "Wither Door", () -> config.colorDoorWither, v -> config.colorDoorWither = v));
        map.add(new ColorEntry(contentX, contentWidth, "Blood Door", () -> config.colorDoorBlood, v -> config.colorDoorBlood = v));
        map.add(new ColorEntry(contentX, contentWidth, "Entrance Door", () -> config.colorDoorEntrance, v -> config.colorDoorEntrance = v));
        map.add(new LabelEntry(contentX, "Text Colors"));
        map.add(new ColorEntry(contentX, contentWidth, "Unexplored Text", () -> config.colorTextUnexplored, v -> config.colorTextUnexplored = v));
        map.add(new ColorEntry(contentX, contentWidth, "Cleared Text", () -> config.colorTextCleared, v -> config.colorTextCleared = v));
        map.add(new ColorEntry(contentX, contentWidth, "Green Text", () -> config.colorTextGreen, v -> config.colorTextGreen = v));
        map.add(new ColorEntry(contentX, contentWidth, "Secret Count", () -> config.colorSecretCount, v -> config.colorSecretCount = v));
        map.add(new LabelEntry(contentX, "Checkmark Colors"));
        map.add(new ColorEntry(contentX, contentWidth, "White Check", () -> config.colorCheckWhite, v -> config.colorCheckWhite = v));
        map.add(new ColorEntry(contentX, contentWidth, "Green Check", () -> config.colorCheckGreen, v -> config.colorCheckGreen = v));
        map.add(new ColorEntry(contentX, contentWidth, "Failed Check", () -> config.colorCheckFailed, v -> config.colorCheckFailed = v));
        categories.put("Map", map);

        List<SettingsEntry> esp = new ArrayList<>();
        esp.add(new LabelEntry(contentX, "ESP Rendering"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Show Glow", () -> config.showGlow, v -> config.showGlow = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Filled ESP (vs Outline)", () -> config.filledESP, v -> config.filledESP = v));
        esp.add(new SliderEntry(contentX, contentWidth, "ESP Transparency", 0.0f, 1.0f,
                () -> config.espAlpha, v -> config.espAlpha = v));
        esp.add(new LabelEntry(contentX, "Dungeon ESP"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Secret Click Highlight", () -> config.secretClickHighlight, v -> config.secretClickHighlight = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Color Portal by Score", () -> config.colorPortal, v -> config.colorPortal = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Starred Mob ESP", () -> config.starredMobESP, v -> config.starredMobESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Tracer When Few Left", () -> config.starredTracerWhenFew, v -> config.starredTracerWhenFew = v));
        esp.add(new SliderEntry(contentX, contentWidth, "Tracer Threshold", 1f, 5f,
                () -> (float) config.starredTracerThreshold, v -> config.starredTracerThreshold = Math.round(v)));
        esp.add(new ToggleEntry(contentX, contentWidth, "Fel ESP", () -> config.felESP, v -> config.felESP = v));
        esp.add(new SliderEntry(contentX, contentWidth, "Fel ESP Range", 10f, 100f,
                () -> (float) config.felESPRange, v -> config.felESPRange = v.intValue()));
        esp.add(new ToggleEntry(contentX, contentWidth, "Sniper ESP", () -> config.sniperESP, v -> config.sniperESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Shadow Assassin ESP", () -> config.shadowAssassinESP, v -> config.shadowAssassinESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Dungeon Bat ESP", () -> config.dungeonBatESP, v -> config.dungeonBatESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Dungeon Bat Tracers", () -> config.dungeonBatTracers, v -> config.dungeonBatTracers = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Wither Key ESP", () -> config.witherKeyESP, v -> config.witherKeyESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Key Tracers", () -> config.keyTracers, v -> config.keyTracers = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Door ESP", () -> config.doorESP, v -> config.doorESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Door Tracers", () -> config.doorTracers, v -> config.doorTracers = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Door Color Based on Key", () -> config.doorColorBasedOnKey, v -> config.doorColorBasedOnKey = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Only Show Next Door", () -> config.onlyShowNextDoor, v -> config.onlyShowNextDoor = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Livid Finder", () -> config.lividFinder, v -> config.lividFinder = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Livid Tracer", () -> config.lividTracer, v -> config.lividTracer = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Mimic Chest ESP", () -> config.mimicChestESP, v -> config.mimicChestESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Mimic Chest Tracers", () -> config.mimicChestTracers, v -> config.mimicChestTracers = v));
        esp.add(new LabelEntry(contentX, "Other ESP"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Highlight Teammates", () -> config.highlightTeammates, v -> config.highlightTeammates = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Pest ESP (Garden)", () -> config.pestESP, v -> config.pestESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Pest Tracers", () -> config.pestTracers, v -> config.pestTracers = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Dropped Item ESP", () -> config.droppedItemESP, v -> config.droppedItemESP = v));
        categories.put("ESP", esp);

        List<SettingsEntry> render = new ArrayList<>();
        render.add(new ButtonEntry(contentX, contentWidth, "Toggle All Render Options", () -> {
            int enabled = 0;
            if (config.noFire) enabled++;
            if (config.noWaterOverlay) enabled++;
            if (config.noVignette) enabled++;
            if (config.noBlind) enabled++;
            if (config.noNausea) enabled++;
            if (config.noDeathAnimation) enabled++;
            if (config.hideInventoryEffects) enabled++;
            if (config.noEffects) enabled++;
            if (config.noLightning) enabled++;
            if (config.noExplosions) enabled++;
            if (config.noBlockBreaking) enabled++;
            if (config.noFallingBlocks) enabled++;
            if (config.noArrows) enabled++;
            if (config.noStuckArrows) enabled++;
            boolean newValue = enabled <= 7;
            config.noFire = newValue;
            config.noWaterOverlay = newValue;
            config.noVignette = newValue;
            config.noBlind = newValue;
            config.noNausea = newValue;
            config.noDeathAnimation = newValue;
            config.hideInventoryEffects = newValue;
            config.noEffects = newValue;
            config.noLightning = newValue;
            config.noExplosions = newValue;
            config.noBlockBreaking = newValue;
            config.noFallingBlocks = newValue;
            config.noArrows = newValue;
            config.noStuckArrows = newValue;
            TeslaMapsConfig.save();
        }));
        render.add(new ToggleEntry(contentX, contentWidth, "No Fire Overlay", () -> config.noFire, v -> config.noFire = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Water Overlay", () -> config.noWaterOverlay, v -> config.noWaterOverlay = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Vignette", () -> config.noVignette, v -> config.noVignette = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Blindness", () -> config.noBlind, v -> config.noBlind = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Nausea", () -> config.noNausea, v -> config.noNausea = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Death Animation", () -> config.noDeathAnimation, v -> config.noDeathAnimation = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Hide Inventory Effects", () -> config.hideInventoryEffects, v -> config.hideInventoryEffects = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Effects (HUD)", () -> config.noEffects, v -> config.noEffects = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Lightning", () -> config.noLightning, v -> config.noLightning = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Explosions", () -> config.noExplosions, v -> config.noExplosions = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Block Breaking", () -> config.noBlockBreaking, v -> config.noBlockBreaking = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Falling Blocks", () -> config.noFallingBlocks, v -> config.noFallingBlocks = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Arrows", () -> config.noArrows, v -> config.noArrows = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Stuck Arrows", () -> config.noStuckArrows, v -> config.noStuckArrows = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Fullbright", () -> config.fullbright, v -> config.fullbright = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Sprinting Overlay", () -> config.sprintingOverlay, v -> config.sprintingOverlay = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Disable World Loading Screen", () -> config.disableWorldLoadingScreen, v -> config.disableWorldLoadingScreen = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Block Overlay", () -> config.blockOverlay, v -> config.blockOverlay = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Chat Waypoint (coords)", () -> config.chatWaypoint, v -> config.chatWaypoint = v));
        render.add(new LabelEntry(contentX, "Etherwarp"));
        render.add(new ToggleEntry(contentX, contentWidth, "Etherwarp Guess Box", () -> config.etherwarp, v -> config.etherwarp = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Show When Failed", () -> config.etherwarpShowFail, v -> config.etherwarpShowFail = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Filled Box", () -> config.etherwarpFilled, v -> config.etherwarpFilled = v));
        render.add(new SliderEntry(contentX, contentWidth, "Eye Height Offset", -0.5f, 0.5f,
                () -> config.etherwarpEyeOffset, v -> config.etherwarpEyeOffset = v, 3));
        categories.put("Render", render);

        List<SettingsEntry> hide = new ArrayList<>();
        hide.add(new LabelEntry(contentX, "Hide Players"));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Players", () -> config.hidePlayers, v -> config.hidePlayers = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Only In Dungeon", () -> config.hidePlayersOnlyDungeon, v -> config.hidePlayersOnlyDungeon = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide All (any distance)", () -> config.hidePlayersAll, v -> config.hidePlayersAll = v));
        hide.add(new SliderEntry(contentX, contentWidth, "Hide Distance", 0f, 32f,
                () -> config.hidePlayersDistance, v -> config.hidePlayersDistance = v));
        hide.add(new LabelEntry(contentX, "Hide Dungeon Objects"));
        hide.add(new ButtonEntry(contentX, contentWidth, "Toggle All", () -> {
            int on = 0, total = 12;
            if (config.hideDeadMobs) on++;
            if (config.hideSoulweaverSkull) on++;
            if (config.hideSkeletonSkull) on++;
            if (config.hideThrownBones) on++;
            if (config.hideSuperboomTnt) on++;
            if (config.hideBlessing) on++;
            if (config.hideReviveStone) on++;
            if (config.hidePremiumFlesh) on++;
            if (config.hideJournalEntry) on++;
            if (config.hideHealerOrbs) on++;
            if (config.hideHealerFairy) on++;
            if (config.hideCheapCoins) on++;
            boolean nv = on <= total / 2;
            config.hideDeadMobs = nv; config.hideSoulweaverSkull = nv; config.hideSkeletonSkull = nv;
            config.hideThrownBones = nv; config.hideSuperboomTnt = nv; config.hideBlessing = nv;
            config.hideReviveStone = nv; config.hidePremiumFlesh = nv; config.hideJournalEntry = nv;
            config.hideHealerOrbs = nv; config.hideHealerFairy = nv; config.hideCheapCoins = nv;
            TeslaMapsConfig.save();
        }));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Dead Mobs", () -> config.hideDeadMobs, v -> config.hideDeadMobs = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Soulweaver Skull", () -> config.hideSoulweaverSkull, v -> config.hideSoulweaverSkull = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Skeleton Skulls", () -> config.hideSkeletonSkull, v -> config.hideSkeletonSkull = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Thrown Bones", () -> config.hideThrownBones, v -> config.hideThrownBones = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Superboom TNT", () -> config.hideSuperboomTnt, v -> config.hideSuperboomTnt = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Blessings", () -> config.hideBlessing, v -> config.hideBlessing = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Revive Stone", () -> config.hideReviveStone, v -> config.hideReviveStone = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Premium Flesh", () -> config.hidePremiumFlesh, v -> config.hidePremiumFlesh = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Journal Entry", () -> config.hideJournalEntry, v -> config.hideJournalEntry = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Healer Orbs", () -> config.hideHealerOrbs, v -> config.hideHealerOrbs = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Healer Fairy", () -> config.hideHealerFairy, v -> config.hideHealerFairy = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Cheap Coins", () -> config.hideCheapCoins, v -> config.hideCheapCoins = v));
        categories.put("Hide", hide);

        List<SettingsEntry> slayer = new ArrayList<>();
        slayer.add(new ToggleEntry(contentX, contentWidth, "Slayer HUD", () -> config.slayerHUD, v -> config.slayerHUD = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Only Own Boss", () -> config.slayerOnlyOwnBoss, v -> config.slayerOnlyOwnBoss = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Boss ESP", () -> config.slayerBossESP, v -> config.slayerBossESP = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Miniboss ESP", () -> config.slayerMinibossESP, v -> config.slayerMinibossESP = v));
        categories.put("Slayer", slayer);

        List<SettingsEntry> solvers = new ArrayList<>();
        solvers.add(new LabelEntry(contentX, "Dungeon Puzzles"));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Block Wrong Terminal Clicks", () -> config.blockWrongTerminalClicks, v -> config.blockWrongTerminalClicks = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Toggle All Puzzles", () -> config.solveBlaze && config.blazeDoneMessage && config.solveThreeWeirdos && config.solveTicTacToe && config.solveCreeperBeams && config.creeperBeamsTracers && config.solveBoulder && config.showAllBoulderClicks && config.solveQuiz && config.quizBeacon && config.solveTPMaze && config.solveWaterBoard && config.waterBoardTracers, v -> {
            config.solveBlaze = v;
            config.blazeDoneMessage = v;
            config.solveThreeWeirdos = v;
            config.solveTicTacToe = v;
            config.solveCreeperBeams = v;
            config.creeperBeamsTracers = v;
            config.solveBoulder = v;
            config.showAllBoulderClicks = v;
            config.solveQuiz = v;
            config.quizBeacon = v;
            config.solveTPMaze = v;
            config.solveWaterBoard = v;
            config.waterBoardTracers = v;
        }));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Blaze Solver", () -> config.solveBlaze, v -> config.solveBlaze = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Modern Blaze Solver", () -> config.modernBlazeSolver, v -> config.modernBlazeSolver = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Blaze Done Message", () -> config.blazeDoneMessage, v -> config.blazeDoneMessage = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Mimic Dead Message", () -> config.mimicDeadMessage, v -> config.mimicDeadMessage = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Prince Dead Message", () -> config.princeDeadMessage, v -> config.princeDeadMessage = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Three Weirdos Solver", () -> config.solveThreeWeirdos, v -> config.solveThreeWeirdos = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Tic Tac Toe Solver", () -> config.solveTicTacToe, v -> config.solveTicTacToe = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Creeper Beams Solver", () -> config.solveCreeperBeams, v -> config.solveCreeperBeams = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Creeper Beams Tracers", () -> config.creeperBeamsTracers, v -> config.creeperBeamsTracers = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Creeper Beams Ding", () -> config.creeperBeamsDing, v -> config.creeperBeamsDing = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Boulder Solver", () -> config.solveBoulder, v -> config.solveBoulder = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Show All Boulder Clicks", () -> config.showAllBoulderClicks, v -> config.showAllBoulderClicks = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Quiz (Trivia) Solver", () -> config.solveQuiz, v -> config.solveQuiz = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Quiz Beacon Beam", () -> config.quizBeacon, v -> config.quizBeacon = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Quiz Chat Highlight", () -> config.quizChatHighlight, v -> config.quizChatHighlight = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Quiz Hide Wrong Answers", () -> config.quizHideWrongAnswers, v -> config.quizHideWrongAnswers = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Teleport Maze Solver", () -> config.solveTPMaze, v -> config.solveTPMaze = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Water Board Solver", () -> config.solveWaterBoard, v -> config.solveWaterBoard = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Water Board Tracers", () -> config.waterBoardTracers, v -> config.waterBoardTracers = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Ice Fill Solver", () -> config.solveIceFill, v -> config.solveIceFill = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Ice Fill Optimized", () -> config.iceFillOptimized, v -> config.iceFillOptimized = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Ice Path Solver", () -> config.icePathSolver, v -> config.icePathSolver = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Puzzle Timers", () -> config.puzzleTimers, v -> config.puzzleTimers = v));
        solvers.add(new LabelEntry(contentX, "F7/M7 Solvers"));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Simon Says Solver", () -> config.solveSimonSays, v -> config.solveSimonSays = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Arrow Align Solver", () -> config.solveArrowAlign, v -> config.solveArrowAlign = v));
        solvers.add(new LabelEntry(contentX, "Boss Timers"));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Terracotta Timer (F6/M6)", () -> config.terracottaTimer, v -> config.terracottaTimer = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Spirit Bear Timer (F4/M4)", () -> config.spiritBearTimer, v -> config.spiritBearTimer = v));
        categories.put("Puzzles", solvers);

        List<SettingsEntry> dragons = new ArrayList<>();
        dragons.add(new ToggleEntry(contentX, contentWidth, "Wither Dragons", () -> config.witherDragons, v -> config.witherDragons = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Spawn Timer", () -> config.witherDragonTimer, v -> config.witherDragonTimer = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Spawn Boxes", () -> config.witherDragonBoxes, v -> config.witherDragonBoxes = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Spawn Title", () -> config.witherDragonTitle, v -> config.witherDragonTitle = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Spawn Messages", () -> config.witherDragonMsg, v -> config.witherDragonMsg = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Boxes (live)", () -> config.dragonBoxes, v -> config.dragonBoxes = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Health %", () -> config.dragonHealth, v -> config.dragonHealth = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Hide Dying Dragons", () -> config.hideDyingDragons, v -> config.hideDyingDragons = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Wither Highlight", () -> config.witherHighlight, v -> config.witherHighlight = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Wither Highlight Box", () -> config.witherHighlightBox, v -> config.witherHighlightBox = v));
        categories.put("Dragons", dragons);

        List<SettingsEntry> sounds = new ArrayList<>();
        sounds.add(new LabelEntry(contentX, "Key Pickup"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Enable Pickup Sound", () -> config.keyPickupSoundEnabled, v -> config.keyPickupSoundEnabled = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Pickup Sound",
                new String[]{"LEVEL_UP", "BLAZE_DEATH", "GHAST_SHOOT", "WITHER_SPAWN", "ENDER_DRAGON_GROWL", "NOTE_PLING"},
                () -> config.keyPickupSound, v -> config.keyPickupSound = v));
        sounds.add(new LabelEntry(contentX, "Key Spawn (On Ground)"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Enable Spawn Sound", () -> config.keyOnGroundSoundEnabled, v -> config.keyOnGroundSoundEnabled = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Spawn Sound",
                new String[]{"NOTE_CHIME", "NOTE_PLING", "EXPERIENCE_ORB", "ANVIL_LAND"},
                () -> config.keyOnGroundSound, v -> config.keyOnGroundSound = v));
        sounds.add(new LabelEntry(contentX, "Secret Found"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Enable Secret Sound", () -> config.secretSound, v -> config.secretSound = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Secret Sound Type",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.secretSoundType, v -> config.secretSoundType = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Secret Chime (ascending)", () -> config.secretChime, v -> config.secretChime = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Secret Chime Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.secretChimeSound, v -> config.secretChimeSound = v));
        sounds.add(new LabelEntry(contentX, "Bear Spawn Warning"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Enable Alert", () -> config.bearSpawnWarning, v -> config.bearSpawnWarning = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Warden Sound", () -> config.bearSpawnWardenSound, v -> config.bearSpawnWardenSound = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Wither Sound", () -> config.bearSpawnWitherSound, v -> config.bearSpawnWitherSound = v));
        sounds.add(new LabelEntry(contentX, "Etherwarp"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Custom Etherwarp Sound", () -> config.etherwarpCustomSound, v -> config.etherwarpCustomSound = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Etherwarp Sound",
                com.teslamaps.features.Etherwarp.soundKeys(),
                () -> config.etherwarpSound, v -> config.etherwarpSound = v));
        sounds.add(new LabelEntry(contentX, "Sound Hiders"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "No Explosion Sound", () -> config.noExplosionSound, v -> config.noExplosionSound = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "No Creeper Hurt Sound", () -> config.noCreeperHurtSound, v -> config.noCreeperHurtSound = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Custom Hype Sound (wither blades)", () -> config.customHypeSound, v -> config.customHypeSound = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Hype Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.customHypeSoundType, v -> config.customHypeSoundType = v));
        categories.put("Sounds", sounds);

        List<SettingsEntry> colors = new ArrayList<>();
        colors.add(new LabelEntry(contentX, "ESP Colors"));
        colors.add(new ColorEntry(contentX, contentWidth, "Starred Mobs", () -> config.colorESPStarred, v -> config.colorESPStarred = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Fels", () -> config.colorESPFel, v -> config.colorESPFel = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Snipers", () -> config.colorESPSniper, v -> config.colorESPSniper = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Shadow Assassins", () -> config.colorESPShadowAssassin, v -> config.colorESPShadowAssassin = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Dungeon Bats", () -> config.colorESPBat, v -> config.colorESPBat = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Wither Key", () -> config.colorESPWitherKey, v -> config.colorESPWitherKey = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Blood Key", () -> config.colorESPBloodKey, v -> config.colorESPBloodKey = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Wither Door", () -> config.colorESPWitherDoor, v -> config.colorESPWitherDoor = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Blood Door", () -> config.colorESPBloodDoor, v -> config.colorESPBloodDoor = v));
        colors.add(new LabelEntry(contentX, "Etherwarp"));
        colors.add(new ColorEntry(contentX, contentWidth, "Etherwarp Box", () -> config.colorEtherwarp, v -> config.colorEtherwarp = v));
        colors.add(new LabelEntry(contentX, "Terminal GUI Colors"));
        colors.add(new ColorEntry(contentX, contentWidth, "GUI Background", () -> config.terminalGuiBackgroundColor, v -> config.terminalGuiBackgroundColor = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Order Color 1", () -> config.terminalGuiOrderColor1, v -> config.terminalGuiOrderColor1 = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Order Color 2", () -> config.terminalGuiOrderColor2, v -> config.terminalGuiOrderColor2 = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Order Color 3", () -> config.terminalGuiOrderColor3, v -> config.terminalGuiOrderColor3 = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Panes Color", () -> config.terminalGuiPanesColor, v -> config.terminalGuiPanesColor = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Starts With Color", () -> config.terminalGuiStartsWithColor, v -> config.terminalGuiStartsWithColor = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Select All Color", () -> config.terminalGuiSelectColor, v -> config.terminalGuiSelectColor = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Rubix Color 1", () -> config.terminalGuiRubixColor1, v -> config.terminalGuiRubixColor1 = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Rubix Color 2", () -> config.terminalGuiRubixColor2, v -> config.terminalGuiRubixColor2 = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Melody Color", () -> config.terminalGuiMelodyColor, v -> config.terminalGuiMelodyColor = v));
        colors.add(new LabelEntry(contentX, "Secret Waypoint Colors"));
        colors.add(new ColorEntry(contentX, contentWidth, "Chest", () -> config.colorSecretChest, v -> config.colorSecretChest = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Item", () -> config.colorSecretItem, v -> config.colorSecretItem = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Bat", () -> config.colorSecretBat, v -> config.colorSecretBat = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Essence", () -> config.colorSecretEssence, v -> config.colorSecretEssence = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Redstone", () -> config.colorSecretRedstone, v -> config.colorSecretRedstone = v));
        categories.put("Colors", colors);

        List<SettingsEntry> autoGFS = new ArrayList<>();
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Enable Auto GFS", () -> config.autoGFS, v -> config.autoGFS = v));
        autoGFS.add(new LabelEntry(contentX, "Triggers"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Refill on Dungeon Start", () -> config.autoGFSOnStart, v -> config.autoGFSOnStart = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Refill on Timer", () -> config.autoGFSTimer, v -> config.autoGFSTimer = v));
        autoGFS.add(new SliderEntry(contentX, contentWidth, "Timer Interval", 5f, 120f,
                () -> (float) config.autoGFSInterval, v -> config.autoGFSInterval = v.intValue()));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Dungeon Only", () -> config.autoGFSDungeonOnly, v -> config.autoGFSDungeonOnly = v));
        autoGFS.add(new LabelEntry(contentX, "Items to Refill"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Ender Pearls", () -> config.autoGFSPearls, v -> config.autoGFSPearls = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Inflatable Jerry", () -> config.autoGFSJerry, v -> config.autoGFSJerry = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Superboom TNT", () -> config.autoGFSTNT, v -> config.autoGFSTNT = v));
        autoGFS.add(new LabelEntry(contentX, "Special"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Auto Draft on Puzzle Fail", () -> config.autoGFSDraft, v -> config.autoGFSDraft = v));
        autoGFS.add(new LabelEntry(contentX, "Auto Wish"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Auto Wish (Healer Ult)", () -> config.autoWish, v -> config.autoWish = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Last Breath Pull Sound", () -> config.lastBreathSound, v -> config.lastBreathSound = v));
        autoGFS.add(new LabelEntry(contentX, "Chests"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Auto Close Chests", () -> config.autoCloseChests, v -> config.autoCloseChests = v));
        autoGFS.add(new SliderEntry(contentX, contentWidth, "Auto Close Delay (ticks)", 0, 20, () -> (float) config.autoCloseDelay, v -> config.autoCloseDelay = v.intValue()));
        autoGFS.add(new SliderEntry(contentX, contentWidth, "Randomization (ticks)", 0, 10, () -> (float) config.autoCloseRandomization, v -> config.autoCloseRandomization = v.intValue()));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Close Chest On Input", () -> config.closeChestOnInput, v -> config.closeChestOnInput = v));
        categories.put("Auto", autoGFS);

        List<SettingsEntry> waypoints = new ArrayList<>();
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Enable Secret Waypoints", () -> config.secretWaypoints, v -> config.secretWaypoints = v));
        waypoints.add(new LabelEntry(contentX, "Secret Types"));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Chests", () -> config.secretWaypointChests, v -> config.secretWaypointChests = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Items", () -> config.secretWaypointItems, v -> config.secretWaypointItems = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Bats", () -> config.secretWaypointBats, v -> config.secretWaypointBats = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Essence", () -> config.secretWaypointEssence, v -> config.secretWaypointEssence = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Redstone Key", () -> config.secretWaypointRedstoneKey, v -> config.secretWaypointRedstoneKey = v));
        waypoints.add(new LabelEntry(contentX, "Display Options"));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Hide When Collected", () -> config.secretWaypointHideCollected, v -> config.secretWaypointHideCollected = v));

        List<SettingsEntry> leap = new ArrayList<>();
        leap.add(new ToggleEntry(contentX, contentWidth, "Enable Leap Overlay", () -> config.leapOverlay, v -> config.leapOverlay = v));
        leap.add(new ToggleEntry(contentX, contentWidth, "Show Player Heads", () -> config.leapShowHeads, v -> config.leapShowHeads = v));
        leap.add(new ToggleEntry(contentX, contentWidth, "Show Map (click to leap)", () -> config.leapShowMap, v -> config.leapShowMap = v));
        leap.add(new ToggleEntry(contentX, contentWidth, "Leap Announce (/pc)", () -> config.leapAnnounce, v -> config.leapAnnounce = v));
        leap.add(new DropdownEntry(contentX, contentWidth, "Sort Mode",
                new String[]{"Odin", "Class A-Z", "Name A-Z", "Custom", "None"},
                () -> config.leapSortMode, v -> config.leapSortMode = v));
        leap.add(new ButtonEntry(contentX, contentWidth, "Edit Custom Order", () -> minecraft.setScreen(new LeapOrderScreen())));
        leap.add(new DropdownEntry(contentX, contentWidth, "Keybind Mode",
                new String[]{"Corners", "Class"}, () -> config.leapKeybindMode, v -> config.leapKeybindMode = v));
        leap.add(new LabelEntry(contentX, "Corner Keybinds"));
        leap.add(new KeybindEntry(contentX, contentWidth, "Top Left", () -> config.leapKeyTL, v -> config.leapKeyTL = v));
        leap.add(new KeybindEntry(contentX, contentWidth, "Top Right", () -> config.leapKeyTR, v -> config.leapKeyTR = v));
        leap.add(new KeybindEntry(contentX, contentWidth, "Bottom Left", () -> config.leapKeyBL, v -> config.leapKeyBL = v));
        leap.add(new KeybindEntry(contentX, contentWidth, "Bottom Right", () -> config.leapKeyBR, v -> config.leapKeyBR = v));
        leap.add(new LabelEntry(contentX, "Class Keybinds"));
        leap.add(new KeybindEntry(contentX, contentWidth, "Archer", () -> config.leapKeyArcher, v -> config.leapKeyArcher = v));
        leap.add(new KeybindEntry(contentX, contentWidth, "Berserk", () -> config.leapKeyBerserk, v -> config.leapKeyBerserk = v));
        leap.add(new KeybindEntry(contentX, contentWidth, "Healer", () -> config.leapKeyHealer, v -> config.leapKeyHealer = v));
        leap.add(new KeybindEntry(contentX, contentWidth, "Mage", () -> config.leapKeyMage, v -> config.leapKeyMage = v));
        leap.add(new KeybindEntry(contentX, contentWidth, "Tank", () -> config.leapKeyTank, v -> config.leapKeyTank = v));
        leap.add(new LabelEntry(contentX, "Other Keybinds"));
        leap.add(new KeybindEntry(contentX, contentWidth, "Last Door Opener", () -> config.leapKeyLastDoor, v -> config.leapKeyLastDoor = v));
        categories.put("Leap", leap);

        List<SettingsEntry> party = new ArrayList<>();
        party.add(new ToggleEntry(contentX, contentWidth, "Party Chat Commands (!8ball etc)", () -> config.chatCommands, v -> config.chatCommands = v));
        party.add(new ToggleEntry(contentX, contentWidth, "Party Duplicate Alert", () -> config.partyDuplicateAlert, v -> config.partyDuplicateAlert = v));
        party.add(new ToggleEntry(contentX, contentWidth, "  Dup: Play Sound", () -> config.partyDuplicateSound, v -> config.partyDuplicateSound = v));
        party.add(new ToggleEntry(contentX, contentWidth, "  Dup: Announce to Party", () -> config.partyDuplicateMessage, v -> config.partyDuplicateMessage = v));
        party.add(new LabelEntry(contentX, "Auto Requeue"));
        party.add(new ToggleEntry(contentX, contentWidth, "Auto Requeue (dungeon end)", () -> config.autoRequeue, v -> config.autoRequeue = v));
        party.add(new ToggleEntry(contentX, contentWidth, "Requeue on Party \"r\"", () -> config.requeueOnPartyR, v -> config.requeueOnPartyR = v));
        party.add(new SliderEntry(contentX, contentWidth, "Requeue Delay (s)", 0f, 30f,
                () -> (float) config.requeueDelaySeconds, v -> config.requeueDelaySeconds = Math.round(v)));
        categories.put("Party", party);

        List<SettingsEntry> score = new ArrayList<>();
        score.add(new LabelEntry(contentX, "Score Announce"));
        score.add(new ToggleEntry(contentX, contentWidth, "Announce 300 (/pc)", () -> config.announce300, v -> config.announce300 = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Announce 270 (/pc)", () -> config.announce270, v -> config.announce270 = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Crypt Reminder (<5 at boss)", () -> config.cryptReminder, v -> config.cryptReminder = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Watcher Kill Alert (blood done)", () -> config.watcherKillAlert, v -> config.watcherKillAlert = v));
        score.add(new LabelEntry(contentX, "Dungeon Splits"));
        score.add(new ToggleEntry(contentX, contentWidth, "Enable Splits HUD", () -> config.splitsEnabled, v -> config.splitsEnabled = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Show PBs (per split)", () -> config.splitsShowPb, v -> config.splitsShowPb = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Send All Splits on End", () -> config.splitsSendAllOnEnd, v -> config.splitsSendAllOnEnd = v));
        categories.put("Score & Splits", score);

        List<SettingsEntry> timers = new ArrayList<>();
        timers.add(new LabelEntry(contentX, "Timers (shared HUD, /tmap gui)"));
        timers.add(new ToggleEntry(contentX, contentWidth, "Warp Cooldown", () -> config.warpCooldown, v -> config.warpCooldown = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Wither Shield Timer (W-Impact)", () -> config.witherShieldTimer, v -> config.witherShieldTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Tactical Insertion Timer", () -> config.tacticalInsertionTimer, v -> config.tacticalInsertionTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Bonzo Mask Timer", () -> config.bonzoTimer, v -> config.bonzoTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Spirit Mask Timer", () -> config.spiritMaskTimer, v -> config.spiritMaskTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Phoenix Pet Timer", () -> config.phoenixTimer, v -> config.phoenixTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Purple Pad Timer (F7)", () -> config.purplePadTimer, v -> config.purplePadTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Death Tick Timer", () -> config.deathTickTimer, v -> config.deathTickTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Relic Timer (M7)", () -> config.relicTimer, v -> config.relicTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Simon Says Progress (F7)", () -> config.simonSaysProgress, v -> config.simonSaysProgress = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Leap Counter (F7)", () -> config.leapCounter, v -> config.leapCounter = v));
        categories.put("Timers", timers);

        List<SettingsEntry> bloodCamp = new ArrayList<>();
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Move Timer HUD", () -> config.bloodCampMoveTimer, v -> config.bloodCampMoveTimer = v));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Move Message", () -> config.bloodCampMoveMessage, v -> config.bloodCampMoveMessage = v));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Party Move Message", () -> config.bloodCampPartyMessage, v -> config.bloodCampPartyMessage = v));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "\"Kill Mobs\" Title", () -> config.bloodCampKillTitle, v -> config.bloodCampKillTitle = v));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Watcher HP Bar", () -> config.bloodCampHpBar, v -> config.bloodCampHpBar = v));
        bloodCamp.add(new LabelEntry(contentX, "Self Alert (title + sound)"));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "\"KILL BLOOD\" on Watcher move", () -> config.bloodCampMoveAlert, v -> config.bloodCampMoveAlert = v));
        bloodCamp.add(new SoundDropdownEntry(contentX, contentWidth, "Alert Sound",
                com.teslamaps.utils.SoundOptions.keys(), () -> config.bloodCampMoveSound, v -> config.bloodCampMoveSound = v));
        bloodCamp.add(new SliderEntry(contentX, contentWidth, "Alert Volume", 0f, 20f,
                () -> config.bloodCampMoveVolume, v -> config.bloodCampMoveVolume = v));
        bloodCamp.add(new LabelEntry(contentX, "Return to Blood Timer"));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Return Timer", () -> config.bloodReturnTimer, v -> config.bloodReturnTimer = v));
        bloodCamp.add(new SliderEntry(contentX, contentWidth, "Start Estimate (s)", 10f, 60f,
                () -> (float) config.bloodReturnEstimate, v -> config.bloodReturnEstimate = Math.round(v)));
        bloodCamp.add(new LabelEntry(contentX, "Spawn Boxes (Assist)"));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Blood Camp Assist", () -> config.bloodCampAssist, v -> config.bloodCampAssist = v));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Spawn Countdown Text", () -> config.bloodAssistTime, v -> config.bloodAssistTime = v));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Line to Spawn", () -> config.bloodAssistLine, v -> config.bloodAssistLine = v));
        bloodCamp.add(new SliderEntry(contentX, contentWidth, "Box Size", 0.1f, 1.0f,
                () -> config.bloodAssistBoxSize, v -> config.bloodAssistBoxSize = v));
        bloodCamp.add(new ColorEntry(contentX, contentWidth, "Spawn Box", () -> config.colorBloodSpawn, v -> config.colorBloodSpawn = v));
        bloodCamp.add(new ColorEntry(contentX, contentWidth, "Position Box", () -> config.colorBloodPosition, v -> config.colorBloodPosition = v));
        bloodCamp.add(new ColorEntry(contentX, contentWidth, "Final Box", () -> config.colorBloodFinal, v -> config.colorBloodFinal = v));
        bloodCamp.add(new LabelEntry(contentX, "Advanced (tune in-game)"));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Ping Offset", () -> config.bloodAssistPingOffset, v -> config.bloodAssistPingOffset = v));
        bloodCamp.add(new SliderEntry(contentX, contentWidth, "Manual Offset (ms)", 0f, 300f,
                () -> config.bloodAssistManualOffset, v -> config.bloodAssistManualOffset = v));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Interpolation", () -> config.bloodAssistInterpolation, v -> config.bloodAssistInterpolation = v));
        bloodCamp.add(new SliderEntry(contentX, contentWidth, "Tick Offset", -100f, 100f,
                () -> (float) config.bloodAssistOffset, v -> config.bloodAssistOffset = Math.round(v)));
        bloodCamp.add(new SliderEntry(contentX, contentWidth, "Spawn Tick", 35f, 41f,
                () -> (float) config.bloodAssistTick, v -> config.bloodAssistTick = Math.round(v)));
        categories.put("Blood Camp", bloodCamp);

        List<SettingsEntry> waypointsCat = new ArrayList<>();
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Dungeon Waypoints", () -> config.dungeonWaypoints, v -> config.dungeonWaypoints = v));
        waypointsCat.add(new LabelEntry(contentX, "New Waypoint Style"));
        waypointsCat.add(new ColorEntry(contentX, contentWidth, "Waypoint Color", () -> config.waypointAddColor, v -> config.waypointAddColor = v));
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Filled Box", () -> config.waypointAddFilled, v -> config.waypointAddFilled = v));
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Through Walls", () -> config.waypointAddThroughWalls, v -> config.waypointAddThroughWalls = v));
        waypointsCat.add(new LabelEntry(contentX, "Edit Keybinds"));
        waypointsCat.add(new KeybindEntry(contentX, contentWidth, "Add (look at block)", () -> config.waypointAddKey, v -> config.waypointAddKey = v));
        waypointsCat.add(new KeybindEntry(contentX, contentWidth, "Remove (nearest)", () -> config.waypointRemoveKey, v -> config.waypointRemoveKey = v));
        waypointsCat.add(new KeybindEntry(contentX, contentWidth, "Clear (room)", () -> config.waypointClearKey, v -> config.waypointClearKey = v));
        waypointsCat.add(new LabelEntry(contentX, "File: config/teslamaps/dungeon_waypoints.json"));
        waypointsCat.add(new LabelEntry(contentX, "Odin format. Reload: /tmap waypoints"));
        waypoints.add(new LabelEntry(contentX, "Dungeon Waypoints"));
        waypoints.addAll(waypointsCat);
        categories.put("Waypoints", waypoints);

        List<SettingsEntry> croesus = new ArrayList<>();
        croesus.add(new ToggleEntry(contentX, contentWidth, "Croesus Profit Overlay", () -> config.croesusProfitOverlay, v -> config.croesusProfitOverlay = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Highlight Most Profitable", () -> config.croesusHighlightBest, v -> config.croesusHighlightBest = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Gray Out Other Chests", () -> config.croesusDimOthers, v -> config.croesusDimOthers = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Highlight Unopened (green)", () -> config.croesusHighlightUnopened, v -> config.croesusHighlightUnopened = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Hide Opened Chests", () -> config.croesusHideOpened, v -> config.croesusHideOpened = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Compact Mode (profit only)", () -> config.croesusCompact, v -> config.croesusCompact = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Debug (log chest lore)", () -> config.croesusDebug, v -> config.croesusDebug = v));
        categories.put("Croesus", croesus);

        List<SettingsEntry> containers = new ArrayList<>();
        containers.add(new LabelEntry(contentX, "Container Helpers"));
        containers.add(new ToggleEntry(contentX, contentWidth, "Salvage Helper", () -> config.salvageHelper, v -> config.salvageHelper = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Sellable Items Highlighter", () -> config.sellableHighlighter, v -> config.sellableHighlighter = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Show Selected Pet", () -> config.showSelectedPet, v -> config.showSelectedPet = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Page Keybinds (A/D)", () -> config.pageKeybinds, v -> config.pageKeybinds = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Sign Enter Key", () -> config.signEnterKey, v -> config.signEnterKey = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "No Cursor Reset", () -> config.noCursorReset, v -> config.noCursorReset = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Auto Copy Screenshot", () -> config.autoCopyScreenshot, v -> config.autoCopyScreenshot = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Searchbar (type to search)", () -> config.searchbar, v -> config.searchbar = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Scrollable Tooltip", () -> config.scrollableTooltip, v -> config.scrollableTooltip = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Estimated Item Value", () -> config.estimatedValue, v -> config.estimatedValue = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Container Value (Top 5)", () -> config.containerValue, v -> config.containerValue = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Backpack/Ender Preview", () -> config.backpackPreview, v -> config.backpackPreview = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Custom Storage Overlay", () -> config.customStorageOverlay, v -> config.customStorageOverlay = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "  Show Page Names", () -> config.storageShowNames, v -> config.storageShowNames = v));
        containers.add(new ToggleEntry(contentX, contentWidth, "Slot Lock", () -> config.slotLock, v -> config.slotLock = v));
        containers.add(new KeybindEntry(contentX, contentWidth, "Slot Lock Key (over slot)", () -> config.slotLockKey, v -> config.slotLockKey = v));
        containers.add(new ColorEntry(contentX, contentWidth, "Locked Slot Color", () -> config.colorSlotLock, v -> config.colorSlotLock = v));
        containers.add(new LabelEntry(contentX, "Rarity Backgrounds"));
        containers.add(new ToggleEntry(contentX, contentWidth, "Enable Rarity Backgrounds", () -> config.rarityBackgrounds, v -> config.rarityBackgrounds = v));
        containers.add(new SliderEntry(contentX, contentWidth, "Opacity", 0f, 1f,
                () -> config.rarityBgOpacity, v -> config.rarityBgOpacity = v));
        containers.add(new DropdownEntry(contentX, contentWidth, "Shape",
                new String[]{"Square", "Circle"}, () -> config.rarityBgShape, v -> config.rarityBgShape = v));
        containers.add(new DropdownEntry(contentX, contentWidth, "Style",
                new String[]{"Filled", "Outline"}, () -> config.rarityBgStyle, v -> config.rarityBgStyle = v));
        containers.add(new LabelEntry(contentX, "Wardrobe Keybinds"));
        containers.add(new ToggleEntry(contentX, contentWidth, "Wardrobe Keybinds (1-9)", () -> config.wardrobeKeybinds, v -> config.wardrobeKeybinds = v));
        for (int i = 0; i < 9; i++) {
            final int idx = i;
            containers.add(new KeybindEntry(contentX, contentWidth, "Wardrobe Slot " + (i + 1),
                    () -> config.wardrobeKeys[idx], v -> config.wardrobeKeys[idx] = v));
        }
        categories.put("Containers", containers);

        List<SettingsEntry> chatFilter = new ArrayList<>();
        chatFilter.add(new LabelEntry(contentX, "Chat QoL"));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Infinite Chat History", () -> config.infiniteChat, v -> config.infiniteChat = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Stack Duplicate Messages", () -> config.chatStacking, v -> config.chatStacking = v));
        chatFilter.add(new SliderEntry(contentX, contentWidth, "Stack Window (min)", 1f, 30f,
                () -> (float) config.chatStackWindowMinutes, v -> config.chatStackWindowMinutes = Math.round(v)));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Right-Click Copy Chat", () -> config.chatCopyEnabled, v -> config.chatCopyEnabled = v));
        chatFilter.add(new LabelEntry(contentX, "Filter"));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Enable Chat Filter", () -> config.chatFilterEnabled, v -> config.chatFilterEnabled = v));
        chatFilter.add(new ButtonEntry(contentX, contentWidth, "Toggle All Filters", () -> {
            int enabled = 0, total = 22;
            if (config.chatFilterEmpty) enabled++;
            if (config.chatFilterBonePlating) enabled++;
            if (config.chatFilterWatcher) enabled++;
            if (config.chatFilterF4Boss) enabled++;
            if (config.chatFilterBlessings) enabled++;
            if (config.chatFilterEssence) enabled++;
            if (config.chatFilterKeys) enabled++;
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
            if (config.chatFilterMilestone) enabled++;
            boolean nv = enabled <= total / 2; // mostly off -> turn all on, else all off
            if (nv) config.chatFilterEnabled = true; // make sure the master switch is on when enabling all
            config.chatFilterWatcher = nv; config.chatFilterF4Boss = nv; config.chatFilterBlessings = nv;
            config.chatFilterEssence = nv; config.chatFilterKeys = nv; config.chatFilterDoors = nv;
            config.chatFilterWish = nv; config.chatFilterPickups = nv; config.chatFilterBlocksInWay = nv;
            config.chatFilterUltReady = nv; config.chatFilterAoeDamage = nv; config.chatFilterGuildXp = nv;
            config.chatFilterKillCombo = nv; config.chatFilterStash = nv; config.chatFilterServerMsgs = nv;
            config.chatFilterProfileInfo = nv; config.chatFilterPerkBuffs = nv; config.chatFilterOruo = nv;
            config.chatFilterSacks = nv; config.chatFilterEmpty = nv; config.chatFilterBonePlating = nv;
            config.chatFilterMilestone = nv;
            TeslaMapsConfig.save();
        }));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Empty Lines", () -> config.chatFilterEmpty, v -> config.chatFilterEmpty = v));
        chatFilter.add(new LabelEntry(contentX, "Hide Messages"));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Watcher Messages", () -> config.chatFilterWatcher, v -> config.chatFilterWatcher = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide F4/M4 Boss (Thorn)", () -> config.chatFilterF4Boss, v -> config.chatFilterF4Boss = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Blessings", () -> config.chatFilterBlessings, v -> config.chatFilterBlessings = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Essence Messages", () -> config.chatFilterEssence, v -> config.chatFilterEssence = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Key Pickups", () -> config.chatFilterKeys, v -> config.chatFilterKeys = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Door Opens", () -> config.chatFilterDoors, v -> config.chatFilterDoors = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Wish Heal Messages", () -> config.chatFilterWish, v -> config.chatFilterWish = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide All \"picked up\"", () -> config.chatFilterPickups, v -> config.chatFilterPickups = v));
        chatFilter.add(new LabelEntry(contentX, "Combat / Misc"));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide \"Blocks in the way\"", () -> config.chatFilterBlocksInWay, v -> config.chatFilterBlocksInWay = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Ult Ready (DROP to activate)", () -> config.chatFilterUltReady, v -> config.chatFilterUltReady = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide AOE Damage Messages", () -> config.chatFilterAoeDamage, v -> config.chatFilterAoeDamage = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Guild XP (GEXP)", () -> config.chatFilterGuildXp, v -> config.chatFilterGuildXp = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Kill Combo", () -> config.chatFilterKillCombo, v -> config.chatFilterKillCombo = v));
        chatFilter.add(new LabelEntry(contentX, "Spam / Info"));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Stash Messages", () -> config.chatFilterStash, v -> config.chatFilterStash = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Server Messages", () -> config.chatFilterServerMsgs, v -> config.chatFilterServerMsgs = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Profile Info", () -> config.chatFilterProfileInfo, v -> config.chatFilterProfileInfo = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide HOTF/HOTM Perks", () -> config.chatFilterPerkBuffs, v -> config.chatFilterPerkBuffs = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Bone Plating", () -> config.chatFilterBonePlating, v -> config.chatFilterBonePlating = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Oruo Messages", () -> config.chatFilterOruo, v -> config.chatFilterOruo = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Sacks", () -> config.chatFilterSacks, v -> config.chatFilterSacks = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Milestones", () -> config.chatFilterMilestone, v -> config.chatFilterMilestone = v));
        categories.put("Chat", chatFilter);

        sections.clear();
        sections.put("Dungeon", List.of("Map", "ESP", "Puzzles", "Dragons", "Blood Camp", "Waypoints", "Leap", "Timers", "Score & Splits"));
        sections.put("Visual", List.of("Render", "Hide", "Colors"));
        sections.put("Items", List.of("Croesus", "Containers"));
        sections.put("Comms", List.of("Chat", "Sounds"));
        sections.put("Party / Auto", List.of("Party", "Auto", "Slayer"));
    }

    private String categoryAtY(int my) {
        int rowY = SIDEBAR_TOP - sidebarScroll;
        for (Map.Entry<String, List<String>> sec : sections.entrySet()) {
            rowY += SECTION_HEADER_H;
            for (String cat : sec.getValue()) {
                if (my >= rowY && my < rowY + CATEGORY_ROW_H) return cat;
                rowY += CATEGORY_ROW_H;
            }
            rowY += SECTION_GAP;
        }
        return null;
    }

    private List<SettingsEntry> getEntriesToShow() {
        List<SettingsEntry> result = new ArrayList<>();
        if (!searchQuery.isEmpty()) {
            for (List<SettingsEntry> entries : categories.values()) {
                for (SettingsEntry entry : entries) {
                    if (entry.matchesSearch(searchQuery)) {
                        result.add(entry);
                    }
                }
            }
        } else {
            List<SettingsEntry> categoryEntries = categories.get(selectedCategory);
            if (categoryEntries != null) result.addAll(categoryEntries);
        }
        return result;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown; // Calculate BEFORE updating wasMouseDown

        if (clicked && mouseX < SIDEBAR_WIDTH && mouseY >= SIDEBAR_TOP && mouseY < sidebarBottom()) {
            String hit = categoryAtY(mouseY);
            if (hit != null) {
                selectedCategory = hit;
                searchQuery = "";
                searchField.setValue("");
                scrollOffset = 0;
                expandedColorEntry = null;
            }
        }

        int shortcutBtnY = this.height - 89;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= shortcutBtnY && mouseY < shortcutBtnY + 22) {
            minecraft.setScreen(new ShortcutScreen());
            wasMouseDown = isMouseDown;
            return;
        }

        int msgBtnY = this.height - 62;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= msgBtnY && mouseY < msgBtnY + 22) {
            minecraft.setScreen(new KeybindMessageScreen());
            wasMouseDown = isMouseDown;
            return;
        }

        int tmapBtnY = this.height - 35;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= tmapBtnY && mouseY < tmapBtnY + 22) {
            if (minecraft.player != null) {
                minecraft.player.connection.sendCommand("tmap gui");
            }
        }

        int doneX = this.width - 70;
        int doneY = this.height - 30;
        if (clicked && mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 22) {
            onClose();
            wasMouseDown = isMouseDown;
            return;
        }

        context.fill(0, 0, this.width, this.height, 0xFF1C1C1E);

        int sfX = SIDEBAR_WIDTH + 14, sfY = 8, sfW = this.width - SIDEBAR_WIDTH - 28, sfH = 22;
        context.fill(sfX, sfY, sfX + sfW, sfY + sfH, 0xFF242426);
        boolean sfFocus = searchField != null && searchField.isFocused();
        drawBorder(context, sfX, sfY, sfW, sfH, sfFocus ? 0xFF30D158 : 0xFF3A3A3C);
        int gx = sfX + 7, gy = sfY + 7; // little magnifier glyph
        context.fill(gx, gy, gx + 5, gy + 1, 0xFF8E8E93);
        context.fill(gx, gy, gx + 1, gy + 5, 0xFF8E8E93);
        context.fill(gx + 4, gy, gx + 5, gy + 5, 0xFF8E8E93);
        context.fill(gx, gy + 4, gx + 5, gy + 5, 0xFF8E8E93);
        context.fill(gx + 5, gy + 5, gx + 8, gy + 8, 0xFF8E8E93);

        context.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xFF2C2C2E);
        context.text(font, "TeslaMaps", 10, 8, 0xFFFFFFFF);
        context.text(font, "Categories", 10, 22, 0xFF30D158);

        int sbTop = SIDEBAR_TOP, sbBottom = sidebarBottom();
        context.enableScissor(0, sbTop, SIDEBAR_WIDTH, sbBottom);
        int rowY = sbTop - sidebarScroll;
        for (Map.Entry<String, List<String>> sec : sections.entrySet()) {
            if (rowY + SECTION_HEADER_H > sbTop && rowY < sbBottom) {
                context.text(font, sec.getKey().toUpperCase(), 10, rowY + 8, 0xFF6A6A6C);
            }
            rowY += SECTION_HEADER_H;
            for (String category : sec.getValue()) {
                boolean selected = category.equals(selectedCategory) && searchQuery.isEmpty();
                boolean hovered = mouseX < SIDEBAR_WIDTH && mouseY >= rowY && mouseY < rowY + CATEGORY_ROW_H
                        && mouseY >= sbTop && mouseY < sbBottom;
                if (rowY + CATEGORY_ROW_H > sbTop && rowY < sbBottom) {
                    if (selected) context.fill(5, rowY, SIDEBAR_WIDTH - 5, rowY + CATEGORY_ROW_H, 0x40FFFFFF);
                    else if (hovered) context.fill(5, rowY, SIDEBAR_WIDTH - 5, rowY + CATEGORY_ROW_H, 0x20FFFFFF);
                    int textColor = selected ? 0xFF30D158 : (hovered ? 0xFFFFFFFF : 0xFF8E8E93);
                    context.text(font, category, 18, rowY + 8, textColor);
                }
                rowY += CATEGORY_ROW_H;
            }
            rowY += SECTION_GAP;
        }
        context.disableScissor();
        int sbContentH = rowY + sidebarScroll - sbTop;
        sidebarMaxScroll = Math.max(0, sbContentH - (sbBottom - sbTop));
        if (sidebarScroll > sidebarMaxScroll) sidebarScroll = sidebarMaxScroll;
        if (sidebarMaxScroll > 0) {
            int trackH = sbBottom - sbTop;
            int thumbH = Math.max(20, (int) ((float) trackH * trackH / sbContentH));
            int thumbY = sbTop + (int) ((trackH - thumbH) * ((float) sidebarScroll / sidebarMaxScroll));
            context.fill(SIDEBAR_WIDTH - 3, thumbY, SIDEBAR_WIDTH - 1, thumbY + thumbH, 0xFF5A5A5C);
        }

        boolean shortcutHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= shortcutBtnY && mouseY < shortcutBtnY + 22;
        context.fill(8, shortcutBtnY, SIDEBAR_WIDTH - 8, shortcutBtnY + 22, shortcutHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, 8, shortcutBtnY, SIDEBAR_WIDTH - 16, 22, shortcutHovered ? 0xFF30D158 : 0xFF48484A);
        context.centeredText(font, "/tmap shortcut", SIDEBAR_WIDTH / 2, shortcutBtnY + 7, 0xFF8E8E93);

        boolean msgHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= msgBtnY && mouseY < msgBtnY + 22;
        context.fill(8, msgBtnY, SIDEBAR_WIDTH - 8, msgBtnY + 22, msgHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, 8, msgBtnY, SIDEBAR_WIDTH - 16, 22, msgHovered ? 0xFF30D158 : 0xFF48484A);
        context.centeredText(font, "/tmap hotkeys", SIDEBAR_WIDTH / 2, msgBtnY + 7, 0xFF8E8E93);

        boolean tmapHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= tmapBtnY && mouseY < tmapBtnY + 22;
        context.fill(8, tmapBtnY, SIDEBAR_WIDTH - 8, tmapBtnY + 22, tmapHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, 8, tmapBtnY, SIDEBAR_WIDTH - 16, 22, tmapHovered ? 0xFF30D158 : 0xFF48484A);
        context.centeredText(font, "/tmap gui", SIDEBAR_WIDTH / 2, tmapBtnY + 7, 0xFF8E8E93);

        int contentTop = 50;
        int contentBottom = this.height - 40;
        int contentLeft = SIDEBAR_WIDTH;
        int contentRight = this.width - 10; // Leave space for scrollbar

        String header = searchQuery.isEmpty() ? selectedCategory : "Search Results";
        context.fill(SIDEBAR_WIDTH, 30, this.width, contentTop, 0xFF1C1C1E); // Header background
        context.text(font, header, SIDEBAR_WIDTH + 20, 35, 0xFFFFFFFF);

        List<SettingsEntry> entries = getEntriesToShow();
        int totalHeight = 0;
        for (SettingsEntry e : entries) totalHeight += e.getHeight();
        int visibleHeight = contentBottom - contentTop;
        maxScroll = Math.max(0, totalHeight - visibleHeight);

        context.enableScissor(contentLeft, contentTop, contentRight, contentBottom);

        int y = contentTop - scrollOffset;
        for (SettingsEntry entry : entries) {
            if (y + entry.getHeight() > contentTop - 50 && y < contentBottom + 50) {
                entry.render(context, this, y, mouseX, mouseY, clicked, isMouseDown);
            }
            y += entry.getHeight();
        }

        context.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = this.width - 8;
            int scrollbarW = 4;
            int scrollbarTrackH = visibleHeight;
            float scrollRatio = (float) scrollOffset / maxScroll;
            float thumbRatio = (float) visibleHeight / totalHeight;
            int thumbH = Math.max(20, (int)(scrollbarTrackH * thumbRatio));
            int thumbY = contentTop + (int)((scrollbarTrackH - thumbH) * scrollRatio);

            context.fill(scrollbarX, contentTop, scrollbarX + scrollbarW, contentBottom, 0xFF2C2C2E);
            context.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF5A5A5C);
        }

        context.fill(SIDEBAR_WIDTH, this.height - 40, this.width, this.height, 0xFF1C1C1E);

        boolean doneHovered = mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 22;
        context.fill(doneX, doneY, doneX + 60, doneY + 22, doneHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, doneX, doneY, 60, 22, doneHovered ? 0xFF30D158 : 0xFF48484A);
        context.centeredText(font, "Done", doneX + 30, doneY + 7, 0xFFFFFFFF);

        wasMouseDown = isMouseDown; // Update AFTER processing

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (mouseX > SIDEBAR_WIDTH) {
            int y = 50 - scrollOffset; // contentTop = 50
            for (SettingsEntry e : getEntriesToShow()) {
                int hgt = e.getHeight();
                if (mouseY >= y && mouseY < y + hgt) {
                    if (e.scrollAt((int) mouseX, (int) mouseY, y, v)) return true;
                    break;
                }
                y += hgt;
            }
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - v * 15));
            return true;
        }
        sidebarScroll = (int) Math.max(0, Math.min(sidebarMaxScroll, sidebarScroll - v * 18));
        return true;
    }

    private void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        super.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {}

    private interface SettingsEntry {
        int getHeight();
        void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown);
        default boolean matchesSearch(String q) { return false; }
        default String getLabel() { return ""; }
        default boolean scrollAt(int mouseX, int mouseY, int rowY, double dir) { return false; }
    }

    private class LabelEntry implements SettingsEntry {
        private final int x;
        private final String text;

        LabelEntry(int x, String text) { this.x = x; this.text = text; }

        @Override public int getHeight() { return 22; }
        @Override public String getLabel() { return text; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            ctx.text(screen.font, text, x, y + 8, 0xFF30D158);
        }
    }

    private class ToggleEntry implements SettingsEntry {
        private final int x, width;
        private final String label;
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;

        ToggleEntry(int x, int w, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.x = x; this.width = w; this.label = label; this.getter = getter; this.setter = setter;
        }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.text(screen.font, label, x + 8, y + 9, 0xFFFFFFFF);

            int toggleX = x + width - 44;
            int toggleY = y + 5;
            boolean value = getter.get();

            if (clicked && hovered) {
                setter.accept(!value);
                TeslaMapsConfig.save();
                value = !value;
            }

            int trackColor = value ? 0xFF30D158 : 0xFF39393D;
            ctx.fill(toggleX, toggleY, toggleX + 36, toggleY + 16, trackColor);
            drawBorder(ctx, toggleX, toggleY, 36, 16, 0xFF48484A);

            int knobX = value ? toggleX + 20 : toggleX + 2;
            ctx.fill(knobX, toggleY + 2, knobX + 14, toggleY + 14, 0xFFFFFFFF);
        }
    }

    private class ButtonEntry implements SettingsEntry {
        private final int x, width;
        private final String label;
        private final Runnable action;

        ButtonEntry(int x, int w, String label, Runnable action) {
            this.x = x; this.width = w; this.label = label; this.action = action;
        }

        @Override public int getHeight() { return ROW_HEIGHT + 4; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            int btnX = x + 8;
            int btnY = y + 2;
            int btnW = width - 16;
            int btnH = 22;

            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            if (clicked && hovered) action.run();

            ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, hovered ? 0xFF3A3A3C : 0xFF2C2C2E);
            drawBorder(ctx, btnX, btnY, btnW, btnH, hovered ? 0xFF5A5A5C : 0xFF48484A);
            ctx.centeredText(screen.font, label, btnX + btnW / 2, btnY + 7, 0xFFFFFFFF);
        }
    }

    private class KeybindEntry implements SettingsEntry {
        private final int x, width;
        private final String label;
        private final IntSupplier getter;
        private final IntConsumer setter;

        KeybindEntry(int x, int w, String label, IntSupplier getter, IntConsumer setter) {
            this.x = x; this.width = w; this.label = label; this.getter = getter; this.setter = setter;
        }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        void setKey(int key) { setter.accept(key); TeslaMapsConfig.save(); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);
            ctx.text(screen.font, label, x + 8, y + 9, 0xFFFFFFFF);

            int btnW = 96, btnX = x + width - btnW - 4, btnY = y + 3, btnH = 20;
            boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            if (clicked && btnHover) screen.listeningKeybindEntry = this;

            boolean listening = screen.listeningKeybindEntry == this;
            String txt = listening ? "press a key..." : keyName(getter.getAsInt());
            ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, (btnHover || listening) ? 0xFF3A3A3C : 0xFF2C2C2E);
            drawBorder(ctx, btnX, btnY, btnW, btnH, listening ? 0xFF30D158 : 0xFF48484A);
            ctx.centeredText(screen.font, txt, btnX + btnW / 2, btnY + 6, 0xFFE0E0E0);
        }
    }

    private class ColorEntry implements SettingsEntry {
        private final int x, width;
        private final String label;
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        ColorEntry(int x, int w, String label, Supplier<String> getter, Consumer<String> setter) {
            this.x = x; this.width = w; this.label = label; this.getter = getter; this.setter = setter;
        }

        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public int getHeight() {
            return expandedColorEntry == this ? ROW_HEIGHT + COLOR_PICKER_HEIGHT : ROW_HEIGHT;
        }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean isExpanded = expandedColorEntry == this;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.text(screen.font, label, x + 8, y + 9, 0xFFFFFFFF);

            int swatchX = x + width - 30;
            int swatchY = y + 5;
            int color = TeslaMapsConfig.parseColor(getter.get());

            if (clicked && hovered) {
                if (isExpanded) {
                    expandedColorEntry = null;
                } else {
                    expandedColorEntry = this;
                    float[] hsv = rgbToHsv((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
                    pickerHue = hsv[0];
                    pickerSat = hsv[1];
                    pickerBright = hsv[2];
                }
            }

            ctx.fill(swatchX, swatchY, swatchX + 22, swatchY + 16, color);
            drawBorder(ctx, swatchX, swatchY, 22, 16, isExpanded ? 0xFF30D158 : 0xFF555555);

            String hex = "#" + getter.get().substring(0, Math.min(6, getter.get().length()));
            ctx.text(screen.font, hex, swatchX - screen.font.width(hex) - 6, y + 9, 0xFF8E8E93);

            if (isExpanded) {
                int pickerY = y + ROW_HEIGHT + 5;
                int pickerX = x + 10;
                int pickerSize = 80;
                int hueWidth = 12;

                for (int cy = 0; cy < pickerSize; cy += 8) {
                    for (int cx = 0; cx < pickerSize; cx += 8) {
                        float sat = (cx + 4f) / pickerSize;
                        float bright = 1.0f - (cy + 4f) / pickerSize;
                        int[] rgb = hsvToRgb(pickerHue, sat, bright);
                        int c = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                        ctx.fill(pickerX + cx, pickerY + cy, pickerX + cx + 8, pickerY + cy + 8, c);
                    }
                }

                int hueX = pickerX + pickerSize + 8;
                for (int cy = 0; cy < pickerSize; cy += 8) {
                    float h = (cy + 4f) / pickerSize;
                    int[] rgb = hsvToRgb(h, 1f, 1f);
                    int c = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                    ctx.fill(hueX, pickerY + cy, hueX + hueWidth, pickerY + cy + 8, c);
                }

                if (mouseDown) {
                    if (mouseX >= pickerX && mouseX < pickerX + pickerSize &&
                        mouseY >= pickerY && mouseY < pickerY + pickerSize) {
                        pickerSat = Math.max(0, Math.min(1, (float)(mouseX - pickerX) / pickerSize));
                        pickerBright = 1.0f - Math.max(0, Math.min(1, (float)(mouseY - pickerY) / pickerSize));
                        updateColorFromPicker();
                    }
                    else if (mouseX >= hueX && mouseX < hueX + hueWidth &&
                             mouseY >= pickerY && mouseY < pickerY + pickerSize) {
                        pickerHue = Math.max(0, Math.min(1, (float)(mouseY - pickerY) / pickerSize));
                        updateColorFromPicker();
                    }
                }

                int cursorX = pickerX + (int)(pickerSat * pickerSize);
                int cursorY = pickerY + (int)((1 - pickerBright) * pickerSize);
                ctx.fill(cursorX - 3, cursorY, cursorX + 3, cursorY + 1, 0xFFFFFFFF);
                ctx.fill(cursorX, cursorY - 3, cursorX + 1, cursorY + 4, 0xFFFFFFFF);

                int hueCursorY = pickerY + (int)(pickerHue * pickerSize);
                ctx.fill(hueX - 1, hueCursorY - 1, hueX + hueWidth + 1, hueCursorY + 2, 0xFFFFFFFF);

                int[] newRgb = hsvToRgb(pickerHue, pickerSat, pickerBright);
                int newColor = 0xFF000000 | (newRgb[0] << 16) | (newRgb[1] << 8) | newRgb[2];
                int previewX = hueX + hueWidth + 15;
                ctx.fill(previewX, pickerY, previewX + 40, pickerY + 25, newColor);
                drawBorder(ctx, previewX, pickerY, 40, 25, 0xFF555555);

                String newHex = String.format("#%02X%02X%02X", newRgb[0], newRgb[1], newRgb[2]);
                ctx.text(screen.font, newHex, previewX, pickerY + 30, 0xFFFFFFFF);
            }
        }

        private void updateColorFromPicker() {
            int[] rgb = hsvToRgb(pickerHue, pickerSat, pickerBright);
            String hex = String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
            setter.accept(hex);
            TeslaMapsConfig.save();
        }
    }

    private static int[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 1f/6f) { r = c; g = x; b = 0; }
        else if (h < 2f/6f) { r = x; g = c; b = 0; }
        else if (h < 3f/6f) { r = 0; g = c; b = x; }
        else if (h < 4f/6f) { r = 0; g = x; b = c; }
        else if (h < 5f/6f) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new int[]{ (int)((r + m) * 255), (int)((g + m) * 255), (int)((b + m) * 255) };
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h = 0;
        if (delta != 0) {
            if (max == rf) h = ((gf - bf) / delta) % 6;
            else if (max == gf) h = (bf - rf) / delta + 2;
            else h = (rf - gf) / delta + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        float s = max == 0 ? 0 : delta / max;
        return new float[]{h, s, max};
    }

    private class SliderEntry implements SettingsEntry {
        private final int x, width;
        private final String label;
        private final float min, max;
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final int decimals;

        SliderEntry(int x, int w, String label, float min, float max, Supplier<Float> getter, Consumer<Float> setter) {
            this(x, w, label, min, max, getter, setter, 1);
        }

        SliderEntry(int x, int w, String label, float min, float max, Supplier<Float> getter, Consumer<Float> setter, int decimals) {
            this.x = x; this.width = w; this.label = label;
            this.min = min; this.max = max; this.getter = getter; this.setter = setter; this.decimals = decimals;
        }

        @Override public boolean scrollAt(int mouseX, int mouseY, int rowY, double dir) {
            int sliderX = x + width - 120;
            if (mouseX < sliderX - 4 || mouseX > sliderX + 80 + 40) return false; // only over the track/value
            float step = (float) Math.pow(10, -decimals);
            float v = Math.max(min, Math.min(max, getter.get() + (float) (dir > 0 ? step : -step)));
            setter.accept(v);
            TeslaMapsConfig.save();
            return true;
        }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.text(screen.font, label, x + 8, y + 9, 0xFFFFFFFF);

            int sliderX = x + width - 120;
            int sliderY = y + 10;
            int sliderW = 80;
            int sliderH = 6;

            float value = getter.get();
            float ratio = (value - min) / (max - min);

            if (mouseDown && mouseX >= sliderX && mouseX < sliderX + sliderW && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                ratio = Math.max(0, Math.min(1, (float)(mouseX - sliderX) / sliderW));
                float newValue = min + ratio * (max - min);
                setter.accept(newValue);
                TeslaMapsConfig.save();
            }

            ctx.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF39393D);
            ctx.fill(sliderX, sliderY, sliderX + (int)(sliderW * ratio), sliderY + sliderH, 0xFF30D158);

            int knobX = sliderX + (int)(sliderW * ratio) - 3;
            ctx.fill(knobX, sliderY - 2, knobX + 6, sliderY + sliderH + 2, 0xFFFFFFFF);

            int valX = sliderX + sliderW + 8;
            boolean editing = screen.editingSlider == this;
            boolean valHover = mouseX >= valX - 2 && mouseX <= valX + 44 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (clicked && valHover && !editing) screen.startSliderEdit(this);
            if (editing) {
                ctx.fill(valX - 2, y + 7, valX + 44, y + 19, 0x40FFFFFF);
                ctx.text(screen.font, screen.sliderEditText + "_", valX, y + 9, 0xFFFFFFFF);
            } else {
                String valueStr = String.format("%." + decimals + "f", getter.get());
                ctx.text(screen.font, valueStr, valX, y + 9, valHover ? 0xFFFFFFFF : 0xFF8E8E93);
            }
        }
    }

    private class DropdownEntry implements SettingsEntry {
        private final int x, width;
        private final String label;
        private final String[] options;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private boolean expanded = false;

        DropdownEntry(int x, int w, String label, String[] options, Supplier<String> getter, Consumer<String> setter) {
            this.x = x; this.width = w; this.label = label;
            this.options = options; this.getter = getter; this.setter = setter;
        }

        @Override public int getHeight() { return expanded ? ROW_HEIGHT + (options.length * 18) : ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.text(screen.font, label, x + 8, y + 9, 0xFFFFFFFF);

            int btnX = x + width - 120;
            int btnY = y + 4;
            int btnW = 110;
            int btnH = 18;

            String currentValue = getter.get();
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

            if (clicked && btnHovered) {
                expanded = !expanded;
            }

            ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
            drawBorder(ctx, btnX, btnY, btnW, btnH, expanded ? 0xFF30D158 : 0xFF48484A);

            String display = currentValue.length() > 14 ? currentValue.substring(0, 12) + ".." : currentValue;
            ctx.text(screen.font, display, btnX + 4, btnY + 5, 0xFFFFFFFF);
            ctx.text(screen.font, expanded ? "▲" : "▼", btnX + btnW - 12, btnY + 5, 0xFF8E8E93);

            if (expanded) {
                int optY = y + ROW_HEIGHT;
                for (String option : options) {
                    boolean optHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= optY && mouseY < optY + 18;

                    if (clicked && optHovered) {
                        setter.accept(option);
                        TeslaMapsConfig.save();
                        expanded = false;
                    }

                    int bgColor = option.equals(currentValue) ? 0xFF30D158 : (optHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
                    ctx.fill(btnX, optY, btnX + btnW, optY + 18, bgColor);
                    drawBorder(ctx, btnX, optY, btnW, 18, 0xFF48484A);

                    String optDisplay = option.length() > 14 ? option.substring(0, 12) + ".." : option;
                    ctx.text(screen.font, optDisplay, btnX + 4, optY + 5, 0xFFFFFFFF);
                    optY += 18;
                }
            }
        }
    }

    private class SoundDropdownEntry implements SettingsEntry {
        private final int x, width;
        private final String label;
        private final String[] options;
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        SoundDropdownEntry(int x, int w, String label, String[] options, Supplier<String> getter, Consumer<String> setter) {
            this.x = x; this.width = w; this.label = label;
            this.options = options; this.getter = getter; this.setter = setter;
        }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);
            ctx.text(screen.font, label, x + 8, y + 9, 0xFFFFFFFF);

            int btnX = x + width - 120, btnY = y + 4, btnW = 110, btnH = 18;
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            if (clicked && btnHovered) {
                Minecraft.getInstance().setScreen(new SoundPickerScreen(screen, label, options, getter.get(),
                        v -> { setter.accept(v); TeslaMapsConfig.save(); }));
            }

            ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
            drawBorder(ctx, btnX, btnY, btnW, btnH, btnHovered ? 0xFF30D158 : 0xFF48484A);
            String cur = getter.get();
            String display = cur.length() > 14 ? cur.substring(0, 12) + ".." : cur;
            ctx.text(screen.font, display, btnX + 4, btnY + 5, 0xFFFFFFFF);
            ctx.text(screen.font, "", btnX + btnW - 12, btnY + 5, 0xFF8E8E93);
        }
    }
}
