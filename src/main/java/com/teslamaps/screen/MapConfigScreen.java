package com.teslamaps.screen;

import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.TeslaGui;
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
    protected static final String[] LOADOUT_NAMES = {
            "Loadout 1", "Loadout 2", "Loadout 3", "Loadout 4", "Loadout 5",
            "Loadout 6", "Loadout 7", "Loadout 8", "Loadout 9"
    };

    protected static final int ROW_HEIGHT = 26;
    // wide enough for the longest category ("Secrets & Waypoints") at full font size
    protected static final int SIDEBAR_WIDTH = 126;
    private static final int COLOR_PICKER_HEIGHT = 100;

    protected int scrollOffset = 0;
    protected int maxScroll = 0;
    protected boolean draggingScrollbar = false;
    protected boolean draggingSidebarScrollbar = false;

    // Collapsed group headers, keyed category#labelOrdinal.
    protected static final java.util.Set<String> GROUP_COLLAPSED = new java.util.HashSet<>();
    private static boolean collapseSeeded = false;

    protected final Map<String, List<SettingsEntry>> categories = new LinkedHashMap<>();
    protected final Map<String, List<String>> sections = new LinkedHashMap<>();
    // Headers shown above search hits so duplicate option names stay tellable apart.
    protected final Map<String, LabelEntry> categoryHeaders = new LinkedHashMap<>();
    protected String selectedCategory = "Profiles";
    protected String searchQuery = "";
    /** Survives a close/reopen of the screen, the widget itself is rebuilt in init(). */
    protected static String lastSearch = "";

    protected int sidebarScroll = 0;
    protected int sidebarMaxScroll = 0;
    protected static final int SIDEBAR_TOP = 50;
    protected static final int SECTION_HEADER_H = 20;
    protected static final int CATEGORY_ROW_H = 24;
    protected static final int SECTION_GAP = 6;
    protected int sidebarBottom() { return this.height - 92; } // above the /tmap buttons

    protected EditBox searchField;
    protected boolean wasMouseDown = false;
    protected boolean wasRightDown = false;
    /** Set by an entry that acted on this frame's click, so no second row reacts to it. */
    protected boolean clickHandled = false;
    /** Profile whose delete button was armed — a second click on it actually deletes. */
    protected String pendingDeleteProfile = null;

    protected ColorEntry expandedColorEntry = null;
    protected float pickerHue = 0, pickerSat = 1, pickerBright = 1;

    protected KeybindEntry listeningKeybindEntry = null;
    protected long keybindListenStart = 0;
    protected SliderEntry editingSlider = null;   // slider whose value is being typed
    protected String sliderEditText = "";

    public boolean isBindingKey() {
        return listeningKeybindEntry != null || editingSlider != null;
    }

    protected void startSliderEdit(SliderEntry s) {
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
        GuiSounds.open();
    }

    /** The visible search bar is 22px tall, the EditBox inside it only 12 - same rect for both. */
    protected boolean teslamaps$overSearch(double mouseX, double mouseY) {
        int sfX = SIDEBAR_WIDTH + 14, sfY = 8, sfW = this.width - SIDEBAR_WIDTH - 28, sfH = 22;
        return mouseX >= sfX && mouseX < sfX + sfW && mouseY >= sfY && mouseY < sfY + sfH;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // Binding a mouse button. The very click that armed the row lands here as well, so
        // anything arriving right after arming is ignored or every bind became Mouse Left.
        if (listeningKeybindEntry != null && System.currentTimeMillis() - keybindListenStart > 250) {
            listeningKeybindEntry.setKey(com.teslamaps.utils.KeyNames.forMouse(event.button()));
            listeningKeybindEntry = null;
            GuiSounds.select();
            return true;
        }
        // Claim clicks on the search bar. Vanilla's dispatch would find no widget in the 10px
        // above/below the EditBox and clear the screen's focus owner, which left the field
        // looking focused while keystrokes went nowhere.
        if (searchField != null && teslamaps$overSearch(event.x(), event.y())) {
            setFocused(searchField);
            searchField.setFocused(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningKeybindEntry != null) {
            // Escape unbinds (-1), any other key binds it
            listeningKeybindEntry.setKey(event.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : event.key());
            listeningKeybindEntry = null;
            GuiSounds.select();
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
        return com.teslamaps.utils.KeyNames.display(key);
    }

    private static String nextProfileName() {
        List<String> taken = com.teslamaps.config.ConfigProfiles.list();
        for (int i = 2; ; i++) {
            String name = com.teslamaps.config.ConfigProfiles.active() + " " + i;
            if (!taken.contains(name)) return name;
        }
    }

    // Cap a control's width so it never eats more than half the row, leaving room for the label.
    private static int controlWidth(int preferred, int rowWidth) {
        return Math.max(40, Math.min(preferred, rowWidth / 2));
    }

    // Truncate label with ellipsis when it would run into the control area at controlX.
    protected String fitLabel(String label, int labelX, int controlX) {
        int avail = controlX - labelX - 6;
        if (avail <= 0) return "";
        if (font.width(label) <= avail) return label;
        String ell = "..";
        while (!label.isEmpty() && font.width(label + ell) > avail) {
            label = label.substring(0, label.length() - 1);
        }
        return label + ell;
    }

    @Override
    protected void init() {
        buildCategories();
        GuiSounds.resetHover();

        String remembered = TeslaMapsConfig.get().lastConfigCategory;
        if (remembered != null && categories.containsKey(remembered)) {
            selectedCategory = remembered;
            scrollSidebarTo(selectedCategory);
        }

        // Start with every group folded (once per session); user toggles persist after.
        if (!collapseSeeded) {
            collapseSeeded = true;
            for (Map.Entry<String, List<SettingsEntry>> c : categories.entrySet()) {
                int ord = 0;
                for (SettingsEntry e : c.getValue()) {
                    if (e.isLabel()) GROUP_COLLAPSED.add(c.getKey() + "#" + (ord++));
                }
            }
        }

        int sfX = SIDEBAR_WIDTH + 14;
        int sfW = this.width - SIDEBAR_WIDTH - 28;
        // unbordered EditBox draws its text at getY(), so place it on the bar's text baseline (bar = y 8, h 22)
        searchField = new EditBox(font, sfX + 18, 8 + (22 - 8) / 2, sfW - 26, 8, Component.literal("Search"));
        searchField.setHint(Component.literal("Search settings..."));
        searchField.setBordered(false);
        searchField.setResponder(query -> {
            searchQuery = query.toLowerCase();
            lastSearch = query;
            scrollOffset = 0;
            expandedColorEntry = null;
        });
        addRenderableWidget(searchField);
        if (!lastSearch.isEmpty()) searchField.setValue(lastSearch); // responder restores searchQuery
    

        ScreenFit.clamp(this); // keep every widget reachable at any gui scale

    }

    protected void buildCategories() {
        categories.clear();
        TeslaMapsConfig config = TeslaMapsConfig.get();

        int contentWidth = Math.max(150, this.width - SIDEBAR_WIDTH - 50);
        int contentX = SIDEBAR_WIDTH + 25;

        List<SettingsEntry> legit = new ArrayList<>();
        legit.add(new LabelEntry(contentX, "Legit Mode"));
        legit.add(new ToggleEntry(contentX, contentWidth, "Enable Legit Mode", () -> config.legitMode, v -> config.legitMode = v));
        legit.add(new ToggleEntry(contentX, contentWidth, "Map Legit Mode Only", () -> config.legitMapOnly, v -> config.legitMapOnly = v));
        legit.add(new KeybindEntry(contentX, contentWidth, "Peek Key (hold to reveal map)", () -> config.legitPeekKey, v -> config.legitPeekKey = v));
        legit.add(new InfoEntry(contentX, "Map: only explored rooms + doors (+ 1x1 guess)."));
        legit.add(new InfoEntry(contentX, "Full mode also blocks all ESP, Auto Wish/GFS."));
        legit.add(new InfoEntry(contentX, "Map Only: just the map, cheats stay on."));
        legit.add(new InfoEntry(contentX, "Solvers, waypoints, timers always stay on."));
        categories.put("Legit Mode", legit);

        List<SettingsEntry> map = new ArrayList<>();
        map.add(new LabelEntry(contentX, "General"));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Map", () -> config.mapEnabled, v -> config.mapEnabled = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Only In Dungeon", () -> config.onlyShowInDungeon, v -> config.onlyShowInDungeon = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Only In Boss", () -> config.mapOnlyInBoss, v -> config.mapOnlyInBoss = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Hide In Boss", () -> config.mapHideInBoss, v -> config.mapHideInBoss = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Background", () -> config.showMapBackground, v -> config.showMapBackground = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Mimic Status", () -> config.showMimicStatus, v -> config.showMimicStatus = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Prince Icon", () -> config.showPrinceIcon, v -> config.showPrinceIcon = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Prince Status", () -> config.showPrinceStatus, v -> config.showPrinceStatus = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Prince ESP (structure)", () -> config.princeESP, v -> config.princeESP = v));
        map.add(new ColorEntry(contentX, contentWidth, "  Prince ESP Color", () -> config.colorPrinceESP, v -> config.colorPrinceESP = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Dungeon Score", () -> config.showDungeonScore, v -> config.showDungeonScore = v));
        map.add(new LabelEntry(contentX, "Rooms"));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Room Names", () -> config.showRoomNames, v -> config.showRoomNames = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Hide Entrance/Blood/Fairy Names", () -> config.hideEntranceBloodFairyNames, v -> config.hideEntranceBloodFairyNames = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Names Only for Puzzles", () -> config.showNamesOnlyForPuzzles, v -> config.showNamesOnlyForPuzzles = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Room Names Only When Leap", () -> config.roomNamesOnlyWithLeap, v -> config.roomNamesOnlyWithLeap = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Player Names Only When Leap", () -> config.playerNamesOnlyWithLeap, v -> config.playerNamesOnlyWithLeap = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Head Border (Class Color)", () -> config.mapHeadBorder, v -> config.mapHeadBorder = v));
        map.add(new SliderEntry(contentX, contentWidth, "Room Name Scale", 0.5f, 2.0f,
                () -> config.roomNameScale, v -> config.roomNameScale = v));
        map.add(new SliderEntry(contentX, contentWidth, "Info Text Scale", 0.5f, 2.0f,
                () -> config.infoTextScale, v -> config.infoTextScale = v));
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
        esp.add(new ToggleEntry(contentX, contentWidth, "Box ESP (Hitbox)", () -> config.boxESP, v -> config.boxESP = v));
        esp.add(new SliderEntry(contentX, contentWidth, "ESP Transparency", 0.0f, 1.0f,
                () -> config.espAlpha, v -> config.espAlpha = v));
        esp.add(new ButtonEntry(contentX, contentWidth, "Custom Entries (/tmap esp)",
                () -> minecraft.setScreen(new EspListScreen())));
        esp.add(new LabelEntry(contentX, "Dungeon ESP"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Color Portal by Score", () -> config.colorPortal, v -> config.colorPortal = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Starred Mob ESP", () -> config.starredMobESP, v -> config.starredMobESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Tracer When Few Left", () -> config.starredTracerWhenFew, v -> config.starredTracerWhenFew = v));
        esp.add(new SliderEntry(contentX, contentWidth, "  Tracer Threshold", 1f, 5f,
                () -> (float) config.starredTracerThreshold, v -> config.starredTracerThreshold = Math.round(v)));
        esp.add(new ColorEntry(contentX, contentWidth, "  Starred Mobs", () -> config.colorESPStarred, v -> config.colorESPStarred = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Fel ESP", () -> config.felESP, v -> config.felESP = v));
        esp.add(new SliderEntry(contentX, contentWidth, "  Fel ESP Range", 10f, 100f,
                () -> (float) config.felESPRange, v -> config.felESPRange = v.intValue()));
        esp.add(new ColorEntry(contentX, contentWidth, "  Fels", () -> config.colorESPFel, v -> config.colorESPFel = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Sniper ESP", () -> config.sniperESP, v -> config.sniperESP = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  Snipers", () -> config.colorESPSniper, v -> config.colorESPSniper = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Shadow Assassin ESP", () -> config.shadowAssassinESP, v -> config.shadowAssassinESP = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  Shadow Assassins", () -> config.colorESPShadowAssassin, v -> config.colorESPShadowAssassin = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Dungeon Bat ESP", () -> config.dungeonBatESP, v -> config.dungeonBatESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Dungeon Bat Tracers", () -> config.dungeonBatTracers, v -> config.dungeonBatTracers = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  Dungeon Bats", () -> config.colorESPBat, v -> config.colorESPBat = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Wither Key ESP", () -> config.witherKeyESP, v -> config.witherKeyESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Key Tracers", () -> config.keyTracers, v -> config.keyTracers = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  Wither Key", () -> config.colorESPWitherKey, v -> config.colorESPWitherKey = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  Blood Key", () -> config.colorESPBloodKey, v -> config.colorESPBloodKey = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Wither ESP", () -> config.bossESP, v -> config.bossESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Wither ESP Filled", () -> config.bossESPFilled, v -> config.bossESPFilled = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Wither ESP Depth Check", () -> config.bossESPDepthCheck, v -> config.bossESPDepthCheck = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  Wither ESP", () -> config.colorBossESP, v -> config.colorBossESP = v));
        esp.add(new LabelEntry(contentX, "Doors"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Door ESP", () -> config.doorESP, v -> config.doorESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Fairy Door ESP", () -> config.fairyDoorESP, v -> config.fairyDoorESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Door Tracers", () -> config.doorTracers, v -> config.doorTracers = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Door Color Based on Key", () -> config.doorColorBasedOnKey, v -> config.doorColorBasedOnKey = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Only Show Next Door", () -> config.onlyShowNextDoor, v -> config.onlyShowNextDoor = v));
        esp.add(new ClassFilterEntry(contentX, contentWidth, "  Doors: Only as Class", config.doorEspClasses));
        esp.add(new ColorEntry(contentX, contentWidth, "  Wither Door", () -> config.colorESPWitherDoor, v -> config.colorESPWitherDoor = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  Blood Door", () -> config.colorESPBloodDoor, v -> config.colorESPBloodDoor = v));
        esp.add(new LabelEntry(contentX, "Livid"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Livid Finder", () -> config.lividFinder, v -> config.lividFinder = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Livid Tracer", () -> config.lividTracer, v -> config.lividTracer = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Livid Dead Message (/pc)", () -> config.lividDeathMessage, v -> config.lividDeathMessage = v));
        esp.add(new LabelEntry(contentX, "Mimic"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Mimic Chest ESP", () -> config.mimicChestESP, v -> config.mimicChestESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Mimic Chest Tracers", () -> config.mimicChestTracers, v -> config.mimicChestTracers = v));
        esp.add(new LabelEntry(contentX, "Other ESP"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Highlight Teammates", () -> config.highlightTeammates, v -> config.highlightTeammates = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Invisible Armor Stand ESP", () -> config.invisibleArmorStandESP, v -> config.invisibleArmorStandESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Hoppity Egg ESP", () -> config.hoppityEggESP, v -> config.hoppityEggESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Hoppity Egg Tracers", () -> config.hoppityEggTracers, v -> config.hoppityEggTracers = v));
        esp.add(new LabelEntry(contentX, "Head ESP"));
        esp.add(new InfoEntry(contentX, "/tmap head add <name> while looking at a head"));
        esp.add(new InfoEntry(contentX, "/tmap head list | remove <name> | probe [radius]"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Golden Dragon Egg ESP", () -> config.headGoldenDragonEgg, v -> config.headGoldenDragonEgg = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  Egg Color", () -> config.headGoldenDragonColor, v -> config.headGoldenDragonColor = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Head ESP (custom list)", () -> config.headESP, v -> config.headESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Show Name", () -> config.headESPName, v -> config.headESPName = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Head Tracers", () -> config.headESPTracers, v -> config.headESPTracers = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "  Head Beacon Beam", () -> config.headESPBeam, v -> config.headESPBeam = v));
        esp.add(new ColorEntry(contentX, contentWidth, "  New Head Color", () -> config.headESPColor, v -> config.headESPColor = v));
        esp.add(new LabelEntry(contentX, "Items"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Dropped Item ESP", () -> config.droppedItemESP, v -> config.droppedItemESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Item Pickup Readiness", () -> config.secretItemReadiness, v -> config.secretItemReadiness = v));
        esp.add(new SliderEntry(contentX, contentWidth, "Item Pickup Delay (ticks)", 0f, 200f,
                () -> (float) config.secretItemPickupTicks, v -> config.secretItemPickupTicks = Math.round(v)));
        categories.put("ESP", esp);

        List<SettingsEntry> render = new ArrayList<>();
        render.add(new LabelEntry(contentX, "World"));
        render.add(new ToggleEntry(contentX, contentWidth, "Fullbright", () -> config.fullbright, v -> config.fullbright = v));
        render.add(new LabelEntry(contentX, "Zoom"));
        render.add(new ToggleEntry(contentX, contentWidth, "Zoom (hold key)", () -> config.zoomEnabled, v -> config.zoomEnabled = v));
        render.add(new KeybindEntry(contentX, contentWidth, "  Zoom Key", () -> config.zoomKey, v -> config.zoomKey = v));
        render.add(new SliderEntry(contentX, contentWidth, "  Zoom FOV", 1f, 110f,
                () -> (float) config.zoomFov, v -> config.zoomFov = v.intValue()));
        render.add(new ToggleEntry(contentX, contentWidth, "  Smooth Zoom", () -> config.zoomSmooth, v -> config.zoomSmooth = v));
        render.add(new SliderEntry(contentX, contentWidth, "  Zoom Speed", 1f, 30f,
                () -> config.zoomSmoothSpeed, v -> config.zoomSmoothSpeed = v, 1));
        render.add(new LabelEntry(contentX, "Motion Blur"));
        render.add(new ToggleEntry(contentX, contentWidth, "Motion Blur", () -> config.motionBlur, v -> config.motionBlur = v));
        render.add(new SliderEntry(contentX, contentWidth, "  Blur Strength", 0f, 0.9f,
                () -> config.motionBlurStrength, v -> config.motionBlurStrength = v, 3));
        render.add(new ToggleEntry(contentX, contentWidth, "  Skip While A Menu Is Open", () -> config.motionBlurWorldOnly, v -> config.motionBlurWorldOnly = v));
        render.add(new LabelEntry(contentX, "Smoothness"));
        render.add(new ToggleEntry(contentX, contentWidth, "Smooth Entity Movement", () -> config.smoothEntities, v -> config.smoothEntities = v));
        render.add(new SliderEntry(contentX, contentWidth, "  Interpolation Ticks", 3f, 12f,
                () -> (float) config.smoothEntitySteps, v -> config.smoothEntitySteps = v.intValue()));
        render.add(new LabelEntry(contentX, "HUD"));
        render.add(new DropdownEntry(contentX, contentWidth, "  HUD Style",
                com.teslamaps.render.gui.HudStyle.NAMES,
                () -> com.teslamaps.render.gui.HudStyle.current(), v -> config.hudStyle = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Sprint Indicator (HUD)", () -> config.sprintingOverlay, v -> config.sprintingOverlay = v));
        render.add(new LabelEntry(contentX, "Dark Mode"));
        render.add(new ToggleEntry(contentX, contentWidth, "Dark Mode", () -> config.darkMode, v -> config.darkMode = v));
        render.add(new SliderEntry(contentX, contentWidth, "  Opacity", 1f, 100f,
                () -> (float) config.darkModeOpacity, v -> config.darkModeOpacity = v.intValue()));
        render.add(new ToggleEntry(contentX, contentWidth, "  Tint HUD", () -> config.darkModeTintHud, v -> config.darkModeTintHud = v));
        render.add(new LabelEntry(contentX, "Loading Screens"));
        render.add(new ToggleEntry(contentX, contentWidth, "Disable World Loading Screen", () -> config.disableWorldLoadingScreen, v -> config.disableWorldLoadingScreen = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Texture Reload Screen", () -> config.noReloadScreen, v -> config.noReloadScreen = v));
        render.add(new LabelEntry(contentX, "Block Overlay"));
        render.add(new ToggleEntry(contentX, contentWidth, "Block Overlay", () -> config.blockOverlay, v -> config.blockOverlay = v));
        render.add(new ColorEntry(contentX, contentWidth, "  Block Overlay Color", () -> config.blockOverlayColor, v -> config.blockOverlayColor = v));
        render.add(new LabelEntry(contentX, "Chat Waypoint"));
        render.add(new ToggleEntry(contentX, contentWidth, "Chat Waypoint (coords)", () -> config.chatWaypoint, v -> config.chatWaypoint = v));
        render.add(new LabelEntry(contentX, "Rare Drop Effects"));
        render.add(new ToggleEntry(contentX, contentWidth, "Rare Drops", () -> config.rareDropEffects, v -> config.rareDropEffects = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Dungeon Chest Rewards", () -> config.rareDungeonDropEffects, v -> config.rareDungeonDropEffects = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Dye Drops", () -> config.rareDyeDropEffects, v -> config.rareDyeDropEffects = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Show Item Name", () -> config.rareDropText, v -> config.rareDropText = v));
        render.add(new ToggleEntry(contentX, contentWidth, "Show Item Value", () -> config.rareDropPrice, v -> config.rareDropPrice = v));
        render.add(new LabelEntry(contentX, "Etherwarp"));
        render.add(new ToggleEntry(contentX, contentWidth, "Etherwarp Guess Box", () -> config.etherwarp, v -> config.etherwarp = v));
        render.add(new ToggleEntry(contentX, contentWidth, "  Show When Failed", () -> config.etherwarpShowFail, v -> config.etherwarpShowFail = v));
        render.add(new ToggleEntry(contentX, contentWidth, "  Fail On Skull", () -> config.etherwarpSkullFail, v -> config.etherwarpSkullFail = v));
        render.add(new ToggleEntry(contentX, contentWidth, "  Filled Box", () -> config.etherwarpFilled, v -> config.etherwarpFilled = v));
        render.add(new SliderEntry(contentX, contentWidth, "  Eye Height Offset", -0.5f, 0.5f,
                () -> config.etherwarpEyeOffset, v -> config.etherwarpEyeOffset = v, 3));
        render.add(new ColorEntry(contentX, contentWidth, "  Etherwarp Box Color", () -> config.colorEtherwarp, v -> config.colorEtherwarp = v));
        render.add(new LabelEntry(contentX, "Cape"));
        render.add(new ToggleEntry(contentX, contentWidth, "Custom Cape", () -> config.capeEnabled, v -> config.capeEnabled = v));
        render.add(new InfoEntry(contentX, "Worn capes are visible to every TeslaMaps user"));
        render.add(new SliderEntry(contentX, contentWidth, "  Waviness", 0f, 1f,
                () -> config.capeWaviness, v -> config.capeWaviness = v, 2));
        render.add(new ButtonEntry(contentX, contentWidth, "Choose Cape",
                () -> minecraft.setScreen(new CapeScreen(this))));
        categories.put("Render", render);

        List<SettingsEntry> title = new ArrayList<>();
        title.add(new LabelEntry(contentX, "Main Menu"));
        title.add(new ToggleEntry(contentX, contentWidth, "Custom Title Screen",
                () -> config.customTitleScreen, v -> config.customTitleScreen = v));
        title.add(new ToggleEntry(contentX, contentWidth, "Custom Loading Screen",
                () -> config.customLoadingScreen, v -> config.customLoadingScreen = v));
        title.add(new InfoEntry(contentX, "Same background while connecting and loading terrain"));
        title.add(new ToggleEntry(contentX, contentWidth, "Modern Vanilla Buttons",
                () -> config.modernVanillaButtons, v -> config.modernVanillaButtons = v));
        title.add(new InfoEntry(contentX, "Glass style in the pause menu, options and every plain button"));
        title.add(new DropdownEntry(contentX, contentWidth, "Background",
                com.teslamaps.render.gui.MenuBackgroundRenderer.names(),
                () -> config.titleBackground, v -> config.titleBackground = v));
        title.add(new ColorEntry(contentX, contentWidth, "Background Tint",
                () -> config.titleBackgroundColor, v -> config.titleBackgroundColor = v));
        title.add(new SliderEntry(contentX, contentWidth, "Image Dim", 0.0f, 0.9f,
                () -> config.titleImageDim, v -> config.titleImageDim = v, 2));
        title.add(new InfoEntry(contentX, "Images: config/teslamaps/backgrounds (png/jpg)"));
        title.add(new SliderEntry(contentX, contentWidth, "Animation Speed", 0.05f, 2.0f,
                () -> config.titleBackgroundSpeed, v -> config.titleBackgroundSpeed = v, 2));
        title.add(new ToggleEntry(contentX, contentWidth, "Auto Title Color",
                () -> config.titleAccentAuto, v -> config.titleAccentAuto = v));
        title.add(new ColorEntry(contentX, contentWidth, "Title Color",
                () -> config.titleAccentColor, v -> config.titleAccentColor = v));
        title.add(new LabelEntry(contentX, "In-World Skybox"));
        title.add(new ToggleEntry(contentX, contentWidth, "Custom Skybox",
                () -> config.customSkybox, v -> config.customSkybox = v));
        title.add(new ToggleEntry(contentX, contentWidth, "Only In Skyblock",
                () -> config.skyboxOnlySkyblock, v -> config.skyboxOnlySkyblock = v));
        title.add(new DropdownEntry(contentX, contentWidth, "Skybox",
                com.teslamaps.render.gui.MenuBackgroundRenderer.shaderNames(),
                () -> config.skyboxPreset, v -> config.skyboxPreset = v));
        title.add(new ColorEntry(contentX, contentWidth, "Skybox Tint",
                () -> config.skyboxColor, v -> config.skyboxColor = v));
        title.add(new SliderEntry(contentX, contentWidth, "Skybox Scale", 0.2f, 3.0f,
                () -> config.skyboxScale, v -> config.skyboxScale = v, 2));
        title.add(new SliderEntry(contentX, contentWidth, "Skybox Speed", 0.05f, 2.0f,
                () -> config.skyboxSpeed, v -> config.skyboxSpeed = v, 2));
        categories.put("Menu & Skybox", title);

        List<SettingsEntry> hide = new ArrayList<>();
        hide.add(new LabelEntry(contentX, "Hide Players"));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Players", () -> config.hidePlayers, v -> config.hidePlayers = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Only In Dungeon", () -> config.hidePlayersOnlyDungeon, v -> config.hidePlayersOnlyDungeon = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide All (any distance)", () -> config.hidePlayersAll, v -> config.hidePlayersAll = v));
        hide.add(new SliderEntry(contentX, contentWidth, "  Hide Distance", 0f, 32f,
                () -> config.hidePlayersDistance, v -> config.hidePlayersDistance = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide After Leap", () -> config.hidePlayersAfterLeap, v -> config.hidePlayersAfterLeap = v));
        hide.add(new SliderEntry(contentX, contentWidth, "  Hide After Leap (s)", 0.5f, 10f,
                () -> config.hidePlayersAfterLeapSeconds, v -> config.hidePlayersAfterLeapSeconds = v));
        hide.add(new LabelEntry(contentX, "Hide Dungeon Objects"));
        hide.add(new ButtonEntry(contentX, contentWidth, "Toggle All Dungeon Objects", () -> {
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
        hide.add(new LabelEntry(contentX, "Screen & World Hiders"));
        hide.add(new ButtonEntry(contentX, contentWidth, "Toggle All Screen & World Hiders", () -> {
            int enabled = 0, total = 22;
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
            if (config.noFog) enabled++;
            if (config.noHurtCam) enabled++;
            if (config.noXpOrbs) enabled++;
            if (config.hideClouds) enabled++;
            if (config.noEnchantGlint) enabled++;
            if (config.noViewBobbing) enabled++;
            if (config.hideArmorBar) enabled++;
            if (config.hideHungerBar) enabled++;
            boolean newValue = enabled <= total / 2;
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
            config.noFog = newValue;
            config.noHurtCam = newValue;
            config.noXpOrbs = newValue;
            config.hideClouds = newValue;
            config.noEnchantGlint = newValue;
            config.noViewBobbing = newValue;
            config.hideArmorBar = newValue;
            config.hideHungerBar = newValue;
            TeslaMapsConfig.save();
        }));
        hide.add(new LabelEntry(contentX, "World & Weather"));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Clouds",
                () -> config.hideClouds, v -> config.hideClouds = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Fog", () -> config.noFog, v -> config.noFog = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Lightning", () -> config.noLightning, v -> config.noLightning = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Falling Blocks", () -> config.noFallingBlocks, v -> config.noFallingBlocks = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Block Breaking", () -> config.noBlockBreaking, v -> config.noBlockBreaking = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide XP Orbs", () -> config.noXpOrbs, v -> config.noXpOrbs = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Block At Packet Level", () -> config.packetHiders, v -> config.packetHiders = v));
        hide.add(new InfoEntry(contentX, "Lightning/Falling Blocks/XP Orbs are then never created"));
        hide.add(new InfoEntry(contentX, "Saves ticking them, not just drawing them"));
        hide.add(new LabelEntry(contentX, "Combat Effects"));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Explosions", () -> config.noExplosions, v -> config.noExplosions = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Arrows", () -> config.noArrows, v -> config.noArrows = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Stuck Arrows", () -> config.noStuckArrows, v -> config.noStuckArrows = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Death Animation", () -> config.noDeathAnimation, v -> config.noDeathAnimation = v));
        hide.add(new LabelEntry(contentX, "Screen Overlays"));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Fire Overlay", () -> config.noFire, v -> config.noFire = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Water Overlay", () -> config.noWaterOverlay, v -> config.noWaterOverlay = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Vignette", () -> config.noVignette, v -> config.noVignette = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Blindness", () -> config.noBlind, v -> config.noBlind = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Nausea", () -> config.noNausea, v -> config.noNausea = v));
        hide.add(new LabelEntry(contentX, "Interface"));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Inventory Effects", () -> config.hideInventoryEffects, v -> config.hideInventoryEffects = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Effects (HUD)", () -> config.noEffects, v -> config.noEffects = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Enchant Glint", () -> config.noEnchantGlint, v -> config.noEnchantGlint = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Armor Bar", () -> config.hideArmorBar, v -> config.hideArmorBar = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "Hide Hunger Bar", () -> config.hideHungerBar, v -> config.hideHungerBar = v));
        hide.add(new LabelEntry(contentX, "Action Bar"));
        hide.add(new ToggleEntry(contentX, contentWidth, "Filter Action Bar", () -> config.actionBarFilter, v -> config.actionBarFilter = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Health", () -> config.abHideHealth, v -> config.abHideHealth = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Defense", () -> config.abHideDefense, v -> config.abHideDefense = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Mana", () -> config.abHideMana, v -> config.abHideMana = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Ability Mana Cost", () -> config.abHideAbilityMana, v -> config.abHideAbilityMana = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Overflow Mana", () -> config.abHideOverflowMana, v -> config.abHideOverflowMana = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Vitality", () -> config.abHideVitality, v -> config.abHideVitality = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Armor Stacks", () -> config.abHideArmorStacks, v -> config.abHideArmorStacks = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Location", () -> config.abHideLocation, v -> config.abHideLocation = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Rift Time", () -> config.abHideRiftTime, v -> config.abHideRiftTime = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Drill Fuel", () -> config.abHideDrillFuel, v -> config.abHideDrillFuel = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Secrets", () -> config.abHideSecrets, v -> config.abHideSecrets = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "  Hide Skill XP", () -> config.abHideSkillXp, v -> config.abHideSkillXp = v));
        hide.add(new InfoEntry(contentX, "/tmap actionbar <text> hides any segment containing that text"));
        hide.add(new InfoEntry(contentX, "/tmap debug actionbar prints the raw action bar + segments"));
        hide.add(new LabelEntry(contentX, "Camera"));
        hide.add(new ToggleEntry(contentX, contentWidth, "No Hurt Camera", () -> config.noHurtCam, v -> config.noHurtCam = v));
        hide.add(new ToggleEntry(contentX, contentWidth, "No View Bobbing", () -> config.noViewBobbing, v -> config.noViewBobbing = v));
        categories.put("Hide", hide);

        List<SettingsEntry> slayer = new ArrayList<>();
        slayer.add(new LabelEntry(contentX, "Slayer"));
        slayer.add(new InfoEntry(contentX, "Slayer kills/bosses live in the Tracker HUD under Skills"));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Only Own Boss", () -> config.slayerOnlyOwnBoss, v -> config.slayerOnlyOwnBoss = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Boss ESP", () -> config.slayerBossESP, v -> config.slayerBossESP = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Miniboss ESP", () -> config.slayerMinibossESP, v -> config.slayerMinibossESP = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Miniboss Tracers", () -> config.slayerMinibossTracer, v -> config.slayerMinibossTracer = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Alerts (Miniboss/Cocoon/Boss)", () -> config.slayerAlerts, v -> config.slayerAlerts = v));
        slayer.add(new LabelEntry(contentX, "Bestiary"));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Bestiary HUD (kills/h and tier ETA)", () -> config.bestiaryTracker, v -> config.bestiaryTracker = v));
        slayer.add(new InfoEntry(contentX, "Reads the Bestiary block in the tab list"));
        slayer.add(new InfoEntry(contentX, "/tmap be add|remove <mob>, /tmap be clear"));
        slayer.add(new InfoEntry(contentX, "Shows nothing until a mob was added"));
        slayer.add(new InfoEntry(contentX, "Kills to max ship with the mod, the Bestiary menu corrects them"));
        categories.put("Combat", slayer);

        List<SettingsEntry> skills = new ArrayList<>();
        skills.add(new LabelEntry(contentX, "Skill Tracker"));
        skills.add(new ToggleEntry(contentX, contentWidth, "Tracker HUD (XP/h and skill rates)", () -> config.skillTracker, v -> config.skillTracker = v));
        skills.add(new ToggleEntry(contentX, contentWidth, "Tracker HUD only in Inventory", () -> config.skillTrackerHidden, v -> config.skillTrackerHidden = v));
        skills.add(new InfoEntry(contentX, "Keeps tracking either way — [Hide]/[Show] in the panel does the same"));
        skills.add(new ToggleEntry(contentX, contentWidth, "Overflow Levels (menu + chat)", () -> config.skillOverflow, v -> config.skillOverflow = v));
        skills.add(new ToggleEntry(contentX, contentWidth, "Overflow Skill Avg. (menu tooltip)", () -> config.skillAvgOverflow, v -> config.skillAvgOverflow = v));
        skills.add(new ToggleEntry(contentX, contentWidth, "Level as Stack Size (skills menu)", () -> config.skillLevelStackSize, v -> config.skillLevelStackSize = v));
        skills.add(new InfoEntry(contentX, "Combat adds kills/bosses/cocoons, Fishing double hooks, Foraging gifts and whispers"));
        String[] catchSources = {"Auto", "Sea Creatures", "Every Catch"};
        skills.add(new DropdownEntry(contentX, contentWidth, "Fishing Counts", catchSources,
                () -> catchSources[Math.max(0, Math.min(2, config.fishingCatchSource))],
                v -> config.fishingCatchSource = switch (v) {
                    case "Sea Creatures" -> 1;
                    case "Every Catch" -> 2;
                    default -> 0;
                }));
        skills.add(new InfoEntry(contentX, "Sea Creatures = chat lines only, Every Catch = counts by Fishing XP"));
        skills.add(new InfoEntry(contentX, "Auto counts creatures while they keep coming in, otherwise every catch"));
        skills.add(new InfoEntry(contentX, "The panel shows the session time and a Reset button in the inventory"));
        skills.add(new InfoEntry(contentX, "Open the Skills menu once so the tracker knows your total XP"));
        skills.add(new LabelEntry(contentX, "Deployables"));
        skills.add(new ToggleEntry(contentX, contentWidth, "Deployable Overlay",
                () -> config.deployableOverlay, v -> config.deployableOverlay = v));
        skills.add(new InfoEntry(contentX, "Time left on power orbs, lanterns, Umberella"));
        skills.add(new ToggleEntry(contentX, contentWidth, "  Only Highest Tier",
                () -> config.deployableHighestTierOnly, v -> config.deployableHighestTierOnly = v));
        skills.add(new ToggleEntry(contentX, contentWidth, "  Power Orbs",
                () -> config.deployableFlux, v -> config.deployableFlux = v));
        skills.add(new ToggleEntry(contentX, contentWidth, "  Lanterns",
                () -> config.deployableLantern, v -> config.deployableLantern = v));
        skills.add(new ToggleEntry(contentX, contentWidth, "  Umberella",
                () -> config.deployableUmbrella, v -> config.deployableUmbrella = v));
        skills.add(new ToggleEntry(contentX, contentWidth, "  Black Hole",
                () -> config.deployableBlackHole, v -> config.deployableBlackHole = v));
        skills.add(new ToggleEntry(contentX, contentWidth, "  Flares",
                () -> config.deployableFlare, v -> config.deployableFlare = v));
        skills.add(new InfoEntry(contentX, "Flares have no nametag, the timer starts when you see them"));
        skills.add(new ToggleEntry(contentX, contentWidth, "  Other Deployables (flares, ...)",
                () -> config.deployableOthers, v -> config.deployableOthers = v));
        skills.add(new InfoEntry(contentX, "Position and scale in /tmap gui"));
        skills.add(new LabelEntry(contentX, "Fixes"));
        skills.add(new ToggleEntry(contentX, contentWidth, "Fix Item Double Clicks",
                () -> config.fixDoubleClicks, v -> config.fixDoubleClicks = v));
        skills.add(new InfoEntry(contentX, "Hypixel fires blaze daggers and autopet rods twice on blocks"));

        categories.put("Skills", skills);

        List<SettingsEntry> fishing = new ArrayList<>();
        fishing.add(new LabelEntry(contentX, "Hook Timer"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "Hook Countdown",
                () -> config.hookDisplay, v -> config.hookDisplay = v));
        fishing.add(new InfoEntry(contentX, "Hypixel's 3.0 / 2.0 / !!! over the bobber, big"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "  Hide Hypixel's Label",
                () -> config.hookHideArmorStand, v -> config.hookHideArmorStand = v));
        fishing.add(new SoundDropdownEntry(contentX, contentWidth, "  Alert Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.hookAlertSound, v -> config.hookAlertSound = v));
        fishing.add(new SliderEntry(contentX, contentWidth, "  Alert Volume", 0f, 4f,
                () -> config.hookAlertVolume, v -> config.hookAlertVolume = v, 2));
        fishing.add(new SliderEntry(contentX, contentWidth, "  Alert Pitch", 0.5f, 2f,
                () -> config.hookAlertPitch, v -> config.hookAlertPitch = v, 2));
        fishing.add(new InfoEntry(contentX, "Volume 0 = no sound. Fires once when !!! appears"));
        fishing.add(new InfoEntry(contentX, "Position and scale in /tmap gui"));

        fishing.add(new LabelEntry(contentX, "Hotspot Radar"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "Guess Hotspot",
                () -> config.hotspotRadar, v -> config.hotspotRadar = v));
        fishing.add(new InfoEntry(contentX, "Reads the radar's particle trail, shows the target"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "  Line To Hotspot",
                () -> config.hotspotLine, v -> config.hotspotLine = v));
        fishing.add(new ToggleEntry(contentX, contentWidth, "  Beacon Beam",
                () -> config.hotspotBeam, v -> config.hotspotBeam = v));
        fishing.add(new ColorEntry(contentX, contentWidth, "  Color",
                () -> config.colorHotspot, v -> config.colorHotspot = v));

        fishing.add(new LabelEntry(contentX, "Hotspot Gone"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "Warn When Hotspot Ends",
                () -> config.hotspotGoneWarning, v -> config.hotspotGoneWarning = v));
        fishing.add(new InfoEntry(contentX, "Fires when the whirlpool you stand at stops"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "  Show Title",
                () -> config.hotspotGoneTitle, v -> config.hotspotGoneTitle = v));
        fishing.add(new SoundDropdownEntry(contentX, contentWidth, "  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.hotspotGoneSound, v -> config.hotspotGoneSound = v));
        fishing.add(new SliderEntry(contentX, contentWidth, "  Volume", 0f, 4f,
                () -> config.hotspotGoneVolume, v -> config.hotspotGoneVolume = v, 2));

        fishing.add(new LabelEntry(contentX, "Exploding Frog"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "Exploding Frog ESP",
                () -> config.explodingFrogESP, v -> config.explodingFrogESP = v));
        fishing.add(new InfoEntry(contentX, "Boxes the lily pad the frog leaves behind"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "  Tracer",
                () -> config.explodingFrogTracer, v -> config.explodingFrogTracer = v));
        fishing.add(new ToggleEntry(contentX, contentWidth, "  Show Name",
                () -> config.explodingFrogName, v -> config.explodingFrogName = v));
        fishing.add(new ColorEntry(contentX, contentWidth, "  Color",
                () -> config.colorExplodingFrog, v -> config.colorExplodingFrog = v));

        fishing.add(new LabelEntry(contentX, "Bobber Timer"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "Bobber Timer",
                () -> config.bobberTimer, v -> config.bobberTimer = v));
        fishing.add(new ToggleEntry(contentX, contentWidth, "  Start on Liquid Touch",
                () -> config.bobberTimerOnLiquid, v -> config.bobberTimerOnLiquid = v));
        fishing.add(new InfoEntry(contentX, "Off = the timer starts when you cast"));
        fishing.add(new InfoEntry(contentX, "Position and scale in /tmap gui"));
        fishing.add(new LabelEntry(contentX, "Umberella"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "Expiry Warning",
                () -> config.umbrellaWarning, v -> config.umbrellaWarning = v));
        fishing.add(new SliderEntry(contentX, contentWidth, "  Warn Seconds Before", 0f, 120f,
                () -> (float) config.umbrellaWarnBefore, v -> config.umbrellaWarnBefore = v.intValue()));
        fishing.add(new ToggleEntry(contentX, contentWidth, "  Show Title",
                () -> config.umbrellaWarningTitle, v -> config.umbrellaWarningTitle = v));
        fishing.add(new SoundDropdownEntry(contentX, contentWidth, "  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.umbrellaWarningSound, v -> config.umbrellaWarningSound = v));
        fishing.add(new SliderEntry(contentX, contentWidth, "  Volume", 0f, 4f,
                () -> config.umbrellaWarningVolume, v -> config.umbrellaWarningVolume = v, 2));
        fishing.add(new LabelEntry(contentX, "Liquids"));
        fishing.add(new ToggleEntry(contentX, contentWidth, "Translucent Lava",
                () -> config.translucentLava, v -> config.translucentLava = v));
        fishing.add(new ColorEntry(contentX, contentWidth, "  Lava Tint",
                () -> config.colorTranslucentLava, v -> config.colorTranslucentLava = v));
        fishing.add(new ToggleEntry(contentX, contentWidth, "Tinted Water",
                () -> config.tintedWater, v -> config.tintedWater = v));
        fishing.add(new ColorEntry(contentX, contentWidth, "  Water Tint",
                () -> config.colorTintedWater, v -> config.colorTintedWater = v));
        categories.put("Fishing", fishing);

        List<SettingsEntry> solvers = new ArrayList<>();
        solvers.add(new LabelEntry(contentX, "Terminals"));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Melody Message (/pc on open)", () -> config.melodyMessage, v -> config.melodyMessage = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Melody Progress (/pc 25/50/75%)", () -> config.melodyProgress, v -> config.melodyProgress = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Terminal Solver", () -> config.terminalSolver, v -> config.terminalSolver = v));
        String[] renderTypes = {"Odin", "Normal", "Custom GUI"};
        solvers.add(new DropdownEntry(contentX, contentWidth, "Render Type", renderTypes,
                () -> renderTypes[Math.max(0, Math.min(2, config.terminalRenderType))],
                v -> config.terminalRenderType = switch (v) {
                    case "Normal" -> 1;
                    case "Custom GUI" -> 2;
                    default -> 0;
                }));
        solvers.add(new SliderEntry(contentX, contentWidth, "Normal Term Size", 1f, 6f,
                () -> (float) config.terminalNormalSize, v -> config.terminalNormalSize = Math.round(v), 0));
        solvers.add(new SliderEntry(contentX, contentWidth, "Custom GUI Size", 1f, 3f,
                () -> config.terminalCustomSize, v -> config.terminalCustomSize = v, 1));
        solvers.add(new SliderEntry(contentX, contentWidth, "Custom GUI Roundness", 0f, 15f,
                () -> config.terminalRoundness, v -> config.terminalRoundness = v, 0));
        solvers.add(new SliderEntry(contentX, contentWidth, "Custom GUI Slot Gap", 0f, 8f,
                () -> (float) config.terminalSlotGap, v -> config.terminalSlotGap = Math.round(v), 0));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Stop Melody Solver", () -> config.terminalCancelMelodySolver, v -> config.terminalCancelMelodySolver = v));
        solvers.add(new SliderEntry(contentX, contentWidth, "Melody GUI Size", 1f, 3f,
                () -> config.terminalMelodySize, v -> config.terminalMelodySize = v, 1));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Show Numbers (Click in Order)", () -> config.terminalShowNumbers, v -> config.terminalShowNumbers = v));
        String[] rubixModes = {"Fewest Clicks", "Left Clicks Only"};
        solvers.add(new DropdownEntry(contentX, contentWidth, "Rubix Mode", rubixModes,
                () -> config.terminalRubixMode == 0 ? rubixModes[0] : rubixModes[1],
                v -> config.terminalRubixMode = v.equals(rubixModes[0]) ? 0 : 1));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Client Prediction", () -> config.terminalHideClicked, v -> config.terminalHideClicked = v));
        solvers.add(new SliderEntry(contentX, contentWidth, "Resolve Timeout (ms)", 300f, 1200f,
                () -> (float) config.terminalReloadThreshold, v -> config.terminalReloadThreshold = Math.round(v), 0));
        solvers.add(new SliderEntry(contentX, contentWidth, "First Click Prot (ms)", 350f, 800f,
                () -> (float) config.terminalFirstClickProt, v -> config.terminalFirstClickProt = Math.round(v), 0));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Account For Server Lag", () -> config.terminalLagProtection, v -> config.terminalLagProtection = v));
        solvers.add(new SliderEntry(contentX, contentWidth, "Lag Protection Ticks", 7f, 16f,
                () -> (float) config.terminalLagProtectionTicks, v -> config.terminalLagProtectionTicks = Math.round(v), 0));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Announce Solve Times", () -> config.terminalTimes, v -> config.terminalTimes = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Solver Debug Overlay", () -> config.terminalSolverDebug, v -> config.terminalSolverDebug = v));
        solvers.add(new LabelEntry(contentX, "Terminal Simulator"));
        solvers.add(new SliderEntry(contentX, contentWidth, "Simulated Ping (ms)", 0f, 500f,
                () -> (float) config.termSimPing, v -> config.termSimPing = Math.round(v), 0));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Skip First Click Prot In Termsim", () -> config.termSimDisableFirstClickProt, v -> config.termSimDisableFirstClickProt = v));
        solvers.add(new ButtonEntry(contentX, contentWidth, "Open Terminal Simulator",
                () -> com.teslamaps.features.TerminalSimulator.open()));
        solvers.add(new LabelEntry(contentX, "Dungeon Puzzles"));
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
        solvers.add(new ToggleEntry(contentX, contentWidth, "TP Maze Next-Portal Tracer", () -> config.tpMazeTracer, v -> config.tpMazeTracer = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "TP Maze Prioritize Diagonal", () -> config.tpMazePrioritizeDiagonal, v -> config.tpMazePrioritizeDiagonal = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Water Board Solver", () -> config.solveWaterBoard, v -> config.solveWaterBoard = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Water Board Tracers", () -> config.waterBoardTracers, v -> config.waterBoardTracers = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Ice Fill Solver", () -> config.solveIceFill, v -> config.solveIceFill = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Ice Fill Optimized", () -> config.iceFillOptimized, v -> config.iceFillOptimized = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Ice Path Solver", () -> config.icePathSolver, v -> config.icePathSolver = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Arrow Align Solver", () -> config.solveArrowAlign, v -> config.solveArrowAlign = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Puzzle Timers", () -> config.puzzleTimers, v -> config.puzzleTimers = v));
        solvers.add(new ToggleEntry(contentX, contentWidth, "Terracotta Spawn Timers (F6/M6)", () -> config.terracottaTimer, v -> config.terracottaTimer = v));
        solvers.add(new LabelEntry(contentX, "Puzzle Colors"));
        solvers.add(new ColorEntry(contentX, contentWidth, "Boulder Click", () -> config.colorBoulder, v -> config.colorBoulder = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Quiz Correct", () -> config.colorQuiz, v -> config.colorQuiz = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Ice Fill Path", () -> config.colorIceFill, v -> config.colorIceFill = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "TP Maze: Single", () -> config.colorTPMazeOne, v -> config.colorTPMazeOne = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "TP Maze: Multiple", () -> config.colorTPMazeMultiple, v -> config.colorTPMazeMultiple = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "TP Maze: Visited", () -> config.colorTPMazeVisited, v -> config.colorTPMazeVisited = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "TP Maze: Tracer", () -> config.colorTPMazeTracer, v -> config.colorTPMazeTracer = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Puzzle Unexplored", () -> config.colorPuzzleUnexplored, v -> config.colorPuzzleUnexplored = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Simon Says: First", () -> config.colorSimonSaysFirst, v -> config.colorSimonSaysFirst = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Simon Says: Second", () -> config.colorSimonSaysSecond, v -> config.colorSimonSaysSecond = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Simon Says: Third+", () -> config.colorSimonSaysThird, v -> config.colorSimonSaysThird = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Water Board: First", () -> config.colorWaterFirst, v -> config.colorWaterFirst = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Water Board: Second", () -> config.colorWaterSecond, v -> config.colorWaterSecond = v));
        solvers.add(new LabelEntry(contentX, "Terminal Colors"));
        solvers.add(new ColorEntry(contentX, contentWidth, "Background", () -> config.terminalBackgroundColor, v -> config.terminalBackgroundColor = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Panes", () -> config.terminalPanesColor, v -> config.terminalPanesColor = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Rubix 1", () -> config.terminalRubixColor1, v -> config.terminalRubixColor1 = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Rubix 2", () -> config.terminalRubixColor2, v -> config.terminalRubixColor2 = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Rubix -1", () -> config.terminalRubixColorNeg1, v -> config.terminalRubixColorNeg1 = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Rubix -2", () -> config.terminalRubixColorNeg2, v -> config.terminalRubixColorNeg2 = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Order 1", () -> config.terminalOrderColor1, v -> config.terminalOrderColor1 = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Order 2", () -> config.terminalOrderColor2, v -> config.terminalOrderColor2 = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Order 3", () -> config.terminalOrderColor3, v -> config.terminalOrderColor3 = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Starts With", () -> config.terminalStartsWithColor, v -> config.terminalStartsWithColor = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Select", () -> config.terminalSelectColor, v -> config.terminalSelectColor = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Melody Column", () -> config.terminalMelodyColumnColor, v -> config.terminalMelodyColumnColor = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Melody Pointer", () -> config.terminalMelodyPointerColor, v -> config.terminalMelodyPointerColor = v));
        solvers.add(new ColorEntry(contentX, contentWidth, "Melody Background", () -> config.terminalMelodyBackgroundColor, v -> config.terminalMelodyBackgroundColor = v));
        categories.put("Puzzles & Terminals", solvers);

        List<SettingsEntry> f7 = new ArrayList<>();
        f7.add(new LabelEntry(contentX, "Simon Says"));
        f7.add(new ToggleEntry(contentX, contentWidth, "Simon Says Solver", () -> config.solveSimonSays, v -> config.solveSimonSays = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "  Block Wrong Clicks (sneak = override)", () -> config.simonSaysBlockWrongClicks, v -> config.simonSaysBlockWrongClicks = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "  Broke/Restart Alerts", () -> config.simonSaysAlerts, v -> config.simonSaysAlerts = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "  Alerts to Party Chat", () -> config.simonSaysAlertChat, v -> config.simonSaysAlertChat = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "  Simon Says Progress", () -> config.simonSaysProgress, v -> config.simonSaysProgress = v));
        f7.add(new LabelEntry(contentX, "Phase Timers"));
        f7.add(new ToggleEntry(contentX, contentWidth, "Purple Pad Timer", () -> config.purplePadTimer, v -> config.purplePadTimer = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "Necron Drop Timer", () -> config.necronDropTimer, v -> config.necronDropTimer = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "Goldor Tick Timer", () -> config.goldorTickTimer, v -> config.goldorTickTimer = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "Storm Pad Timer", () -> config.stormPadTimer, v -> config.stormPadTimer = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "Storm Lightning Timer", () -> config.lightningTimer, v -> config.lightningTimer = v));
        f7.add(new ToggleEntry(contentX, contentWidth, "Relic Timer (M7)", () -> config.relicTimer, v -> config.relicTimer = v));
        f7.add(new LabelEntry(contentX, "Other"));
        f7.add(new ToggleEntry(contentX, contentWidth, "Leap Counter", () -> config.leapCounter, v -> config.leapCounter = v));
        categories.put("F7 / M7", f7);

        List<SettingsEntry> dragons = new ArrayList<>();
        dragons.add(new ToggleEntry(contentX, contentWidth, "Wither Dragons", () -> config.witherDragons, v -> config.witherDragons = v));
        dragons.add(new LabelEntry(contentX, "Spawn Timer & Alerts"));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Spawn Timer", () -> config.witherDragonTimer, v -> config.witherDragonTimer = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Spawn Boxes", () -> config.witherDragonBoxes, v -> config.witherDragonBoxes = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Spawn Title", () -> config.witherDragonTitle, v -> config.witherDragonTitle = v));
        dragons.add(new DropdownEntry(contentX, contentWidth, "Timer Style",
                new String[]{"Milliseconds", "Seconds", "Ticks"},
                () -> switch (config.witherDragonTimerStyle) { case 1 -> "Seconds"; case 2 -> "Ticks"; default -> "Milliseconds"; },
                v -> config.witherDragonTimerStyle = switch (v) { case "Seconds" -> 1; case "Ticks" -> 2; default -> 0; }));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Timer Symbol", () -> config.witherDragonTimerSymbol, v -> config.witherDragonTimerSymbol = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Timer HUD", () -> config.witherDragonHud, v -> config.witherDragonHud = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Spawn Messages", () -> config.witherDragonMsg, v -> config.witherDragonMsg = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Health (spawn phase)", () -> config.witherDragonHealth, v -> config.witherDragonHealth = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Send Dragon Confirmation", () -> config.witherDragonConfirm, v -> config.witherDragonConfirm = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Send Dragon Time Alive", () -> config.witherDragonSendTime, v -> config.witherDragonSendTime = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Send Ice Sprayed", () -> config.witherDragonSendSpray, v -> config.witherDragonSendSpray = v));
        dragons.add(new LabelEntry(contentX, "Priority & Splits"));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Priority (split)", () -> config.witherDragonPriority, v -> config.witherDragonPriority = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Priority Tracer", () -> config.witherDragonPriorityTracer, v -> config.witherDragonPriorityTracer = v));
        dragons.add(new SliderEntry(contentX, contentWidth, "Normal Power (split)", 0f, 32f,
                () -> (float) config.witherDragonNormalPower, v -> config.witherDragonNormalPower = Math.round(v)));
        dragons.add(new SliderEntry(contentX, contentWidth, "Easy Power (purple split)", 0f, 32f,
                () -> (float) config.witherDragonEasyPower, v -> config.witherDragonEasyPower = Math.round(v)));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Healer Solos Purple (off=Tank)", () -> config.witherDragonSoloDebuff == 1, v -> config.witherDragonSoloDebuff = v ? 1 : 0));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Solo Debuff on All Splits", () -> config.witherDragonSoloDebuffAll, v -> config.witherDragonSoloDebuffAll = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Paul Buff (×1.25 power)", () -> config.witherDragonPaulBuff, v -> config.witherDragonPaulBuff = v));
        dragons.add(new LabelEntry(contentX, "Live Dragons"));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Boxes (live)", () -> config.dragonBoxes, v -> config.dragonBoxes = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Dragon Health %", () -> config.dragonHealth, v -> config.dragonHealth = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Hide Dying Dragons", () -> config.hideDyingDragons, v -> config.hideDyingDragons = v));
        dragons.add(new LabelEntry(contentX, "Wither Highlight"));
        dragons.add(new ToggleEntry(contentX, contentWidth, "Wither Highlight", () -> config.witherHighlight, v -> config.witherHighlight = v));
        dragons.add(new ToggleEntry(contentX, contentWidth, "  Wither Highlight Box", () -> config.witherHighlightBox, v -> config.witherHighlightBox = v));
        dragons.add(new ColorEntry(contentX, contentWidth, "  Wither Highlight Color", () -> config.witherHighlightColor, v -> config.witherHighlightColor = v));
        f7.add(new LabelEntry(contentX, "Wither Dragons"));
        f7.addAll(dragons);

        List<SettingsEntry> m4 = new ArrayList<>();
        m4.add(new LabelEntry(contentX, "Highlight (F4/M4)"));
        m4.add(new ToggleEntry(contentX, contentWidth, "Enable Highlight", () -> config.m4Highlight, v -> config.m4Highlight = v));
        m4.add(new DropdownEntry(contentX, contentWidth, "Style", new String[]{"Filled", "Outline", "Filled Outline"},
                () -> config.m4HighlightStyle, v -> config.m4HighlightStyle = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Hide Nametags", () -> config.m4HideNametags, v -> config.m4HideNametags = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Thorn", () -> config.m4HlThorn, v -> config.m4HlThorn = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Thorn Damage Flash", () -> config.m4ThornDmgFlash, v -> config.m4ThornDmgFlash = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Bear", () -> config.m4HlBear, v -> config.m4HlBear = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Bear Only On Mage", () -> config.m4BearOnlyMage, v -> config.m4BearOnlyMage = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Cows", () -> config.m4HlCow, v -> config.m4HlCow = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Sheep", () -> config.m4HlSheep, v -> config.m4HlSheep = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Chickens", () -> config.m4HlChicken, v -> config.m4HlChicken = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Bats", () -> config.m4HlBat, v -> config.m4HlBat = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Wolves", () -> config.m4HlWolf, v -> config.m4HlWolf = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Rabbits", () -> config.m4HlRabbit, v -> config.m4HlRabbit = v));
        m4.add(new LabelEntry(contentX, "Highlight Colors"));
        m4.add(new ColorEntry(contentX, contentWidth, "Thorn", () -> config.m4ColorThorn, v -> config.m4ColorThorn = v));
        m4.add(new ColorEntry(contentX, contentWidth, "Thorn Damaged", () -> config.m4ColorThornDmg, v -> config.m4ColorThornDmg = v));
        m4.add(new ColorEntry(contentX, contentWidth, "Bear", () -> config.m4ColorBear, v -> config.m4ColorBear = v));
        m4.add(new ColorEntry(contentX, contentWidth, "Cow", () -> config.m4ColorCow, v -> config.m4ColorCow = v));
        m4.add(new ColorEntry(contentX, contentWidth, "Sheep", () -> config.m4ColorSheep, v -> config.m4ColorSheep = v));
        m4.add(new ColorEntry(contentX, contentWidth, "Chicken", () -> config.m4ColorChicken, v -> config.m4ColorChicken = v));
        m4.add(new ColorEntry(contentX, contentWidth, "Bat", () -> config.m4ColorBat, v -> config.m4ColorBat = v));
        m4.add(new ColorEntry(contentX, contentWidth, "Wolf", () -> config.m4ColorWolf, v -> config.m4ColorWolf = v));
        m4.add(new ColorEntry(contentX, contentWidth, "Rabbit", () -> config.m4ColorRabbit, v -> config.m4ColorRabbit = v));
        m4.add(new LabelEntry(contentX, "Decoy & Trajectories"));
        m4.add(new ToggleEntry(contentX, contentWidth, "Best Decoy Spot", () -> config.m4Decoy, v -> config.m4Decoy = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Trajectories", () -> config.m4Trajectories, v -> config.m4Trajectories = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "  Trajectory: Cows", () -> config.m4TrajCow, v -> config.m4TrajCow = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "  Trajectory: Sheep", () -> config.m4TrajSheep, v -> config.m4TrajSheep = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "  Trajectory: Chickens", () -> config.m4TrajChicken, v -> config.m4TrajChicken = v));
        m4.add(new LabelEntry(contentX, "Boss Timers"));
        m4.add(new ToggleEntry(contentX, contentWidth, "Spirit Bear Timer", () -> config.spiritBearTimer, v -> config.spiritBearTimer = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Thorn Stun Timer", () -> config.thornStunTimer, v -> config.thornStunTimer = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "  Thorn Stun: Healer Only", () -> config.thornStunHealerOnly, v -> config.thornStunHealerOnly = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Overkill Counter", () -> config.overkillCounter, v -> config.overkillCounter = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "  Overkill: Hide Zero", () -> config.overkillHideZero, v -> config.overkillHideZero = v));
        m4.add(new LabelEntry(contentX, "Titles"));
        m4.add(new ToggleEntry(contentX, contentWidth, "Bow Missed Title", () -> config.titleBowMiss, v -> config.titleBowMiss = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Bow Pickup Title", () -> config.titleBowPickup, v -> config.titleBowPickup = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Bear Event Titles", () -> config.titleBearEvents, v -> config.titleBearEvents = v));
        m4.add(new ToggleEntry(contentX, contentWidth, "Hide Default Titles (boss)", () -> config.hideDefaultTitles, v -> config.hideDefaultTitles = v));
        categories.put("F4 / M4", m4);

        List<SettingsEntry> sounds = new ArrayList<>();
        sounds.add(new LabelEntry(contentX, "Wither Key"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Enable Pickup Sound", () -> config.keyPickupSoundEnabled, v -> config.keyPickupSoundEnabled = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Pickup Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.keyPickupSound, v -> config.keyPickupSound = v));
        sounds.add(new SliderEntry(contentX, contentWidth, "Pickup Volume", 0f, 4f,
                () -> config.keyPickupVolume, v -> config.keyPickupVolume = v, 2));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Enable Spawn Sound", () -> config.keyOnGroundSoundEnabled, v -> config.keyOnGroundSoundEnabled = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Spawn Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.keyOnGroundSound, v -> config.keyOnGroundSound = v));
        sounds.add(new SliderEntry(contentX, contentWidth, "Spawn Volume", 0f, 4f,
                () -> config.keyOnGroundVolume, v -> config.keyOnGroundVolume = v, 2));
        sounds.add(new ClassFilterEntry(contentX, contentWidth, "Key Sounds: Only as Class", config.keySoundClasses));
        sounds.add(new LabelEntry(contentX, "Secret Found"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Enable Secret Sound", () -> config.secretSound, v -> config.secretSound = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Secret Sound Type",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.secretSoundType, v -> config.secretSoundType = v));
        sounds.add(new SliderEntry(contentX, contentWidth, "Secret Volume", 0f, 4f,
                () -> config.secretSoundVolume, v -> config.secretSoundVolume = v, 2));
        sounds.add(new SliderEntry(contentX, contentWidth, "Secret Pitch", 0.5f, 2f,
                () -> config.secretSoundPitch, v -> config.secretSoundPitch = v, 2));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Secret Item Pickup Sound", () -> config.secretItemPickupSound, v -> config.secretItemPickupSound = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Secret Chime (Rising Pitch)", () -> config.secretChime, v -> config.secretChime = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "  Chime Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.secretChimeSound, v -> config.secretChimeSound = v));
        sounds.add(new SliderEntry(contentX, contentWidth, "  Chime Volume", 0f, 4f,
                () -> config.secretChimeVolume, v -> config.secretChimeVolume = v, 2));
        sounds.add(new LabelEntry(contentX, "Bear Spawn Warning"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Enable Alert", () -> config.bearSpawnWarning, v -> config.bearSpawnWarning = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Warden Sound", () -> config.bearSpawnWardenSound, v -> config.bearSpawnWardenSound = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Wither Sound", () -> config.bearSpawnWitherSound, v -> config.bearSpawnWitherSound = v));
        sounds.add(new SliderEntry(contentX, contentWidth, "Alert Volume", 0f, 20f,
                () -> config.bearSpawnVolume, v -> config.bearSpawnVolume = v, 1));
        sounds.add(new LabelEntry(contentX, "Etherwarp"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Custom Etherwarp Sound", () -> config.etherwarpCustomSound, v -> config.etherwarpCustomSound = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Etherwarp Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.etherwarpSound, v -> config.etherwarpSound = v));
        sounds.add(new SliderEntry(contentX, contentWidth, "Etherwarp Volume", 0f, 20f,
                () -> config.etherwarpSoundVolume, v -> config.etherwarpSoundVolume = v, 1));
        sounds.add(new SliderEntry(contentX, contentWidth, "Etherwarp Pitch", 0.5f, 2f,
                () -> config.etherwarpSoundPitch, v -> config.etherwarpSoundPitch = v, 2));
        sounds.add(new LabelEntry(contentX, "Last Breath"));
        sounds.add(new SliderEntry(contentX, contentWidth, "Last Breath Volume", 0f, 5f,
                () -> config.lastBreathVolume, v -> config.lastBreathVolume = v, 2));
        sounds.add(new SliderEntry(contentX, contentWidth, "Last Breath Threshold (ticks)", 0f, 21f,
                () -> (float) config.lastBreathThreshold, v -> config.lastBreathThreshold = Math.round(v)));
        sounds.add(new LabelEntry(contentX, "Sound Hiders"));
        sounds.add(new ToggleEntry(contentX, contentWidth, "No Explosion Sound", () -> config.noExplosionSound, v -> config.noExplosionSound = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "No Creeper Hurt Sound", () -> config.noCreeperHurtSound, v -> config.noCreeperHurtSound = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Mute Bonzo Staff", () -> config.muteBonzoStaff, v -> config.muteBonzoStaff = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Mute Lightning", () -> config.muteLightning, v -> config.muteLightning = v));
        sounds.add(new ToggleEntry(contentX, contentWidth, "Custom Hype Sound (wither blades)", () -> config.customHypeSound, v -> config.customHypeSound = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Hype Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.customHypeSoundType, v -> config.customHypeSoundType = v));
        sounds.add(new SliderEntry(contentX, contentWidth, "Hype Sound Volume", 0f, 20f,
                () -> config.customHypeSoundVolume, v -> config.customHypeSoundVolume = v, 1));
        sounds.add(new SliderEntry(contentX, contentWidth, "Hype Sound Pitch", 0.5f, 2f,
                () -> config.customHypeSoundPitch, v -> config.customHypeSoundPitch = v, 2));
        categories.put("Sounds", sounds);

        List<SettingsEntry> autoGFS = new ArrayList<>();
        autoGFS.add(new LabelEntry(contentX, "Auto GFS"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Enable Auto GFS", () -> config.autoGFS, v -> config.autoGFS = v));
        autoGFS.add(new LabelEntry(contentX, "Triggers"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Refill on Dungeon Start", () -> config.autoGFSOnStart, v -> config.autoGFSOnStart = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Refill on Timer", () -> config.autoGFSTimer, v -> config.autoGFSTimer = v));
        autoGFS.add(new SliderEntry(contentX, contentWidth, "  Timer Interval", 5f, 120f,
                () -> (float) config.autoGFSInterval, v -> config.autoGFSInterval = v.intValue()));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Dungeon Only", () -> config.autoGFSDungeonOnly, v -> config.autoGFSDungeonOnly = v));
        autoGFS.add(new LabelEntry(contentX, "Items to Refill"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Ender Pearls", () -> config.autoGFSPearls, v -> config.autoGFSPearls = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Inflatable Jerry", () -> config.autoGFSJerry, v -> config.autoGFSJerry = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Superboom TNT", () -> config.autoGFSTNT, v -> config.autoGFSTNT = v));
        autoGFS.add(new LabelEntry(contentX, "Get From Sacks"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Queued GFS", () -> config.gfsQueued, v -> config.gfsQueued = v));
        autoGFS.add(new InfoEntry(contentX, "Spaces /gfs out so Hypixel stops dropping them"));
        if (com.teslamaps.features.GetFromSacks.SKYHANNI) {
            autoGFS.add(new InfoEntry(contentX, "§cInactive: SkyHanni handles /gfs"));
        }
        autoGFS.add(new SliderEntry(contentX, contentWidth, "  Default Amount", 1f, 64f,
                () -> (float) config.gfsDefaultAmount, v -> config.gfsDefaultAmount = v.intValue()));
        autoGFS.add(new KeybindEntry(contentX, contentWidth, "GFS Key (over a slot)",
                () -> config.gfsKeybind, v -> config.gfsKeybind = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Tab-Complete Sack Items", () -> config.gfsSuggestions, v -> config.gfsSuggestions = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Bazaar Fallback", () -> config.gfsBazaar, v -> config.gfsBazaar = v));
        autoGFS.add(new InfoEntry(contentX, "Offers the missing rest from the bazaar"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Supercraft Grab Link", () -> config.gfsSuperCraft, v -> config.gfsSuperCraft = v));

        autoGFS.add(new LabelEntry(contentX, "Special"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Auto Draft on Puzzle Fail", () -> config.autoGFSDraft, v -> config.autoGFSDraft = v));
        autoGFS.add(new LabelEntry(contentX, "Auto Wish"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Auto Wish (Healer Ult)", () -> config.autoWish, v -> config.autoWish = v));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Last Breath Pull Sound", () -> config.lastBreathSound, v -> config.lastBreathSound = v));
        autoGFS.add(new LabelEntry(contentX, "Chests"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Auto Close Chests", () -> config.autoCloseChests, v -> config.autoCloseChests = v));
        autoGFS.add(new SliderEntry(contentX, contentWidth, "  Auto Close Delay (ticks)", 0, 20, () -> (float) config.autoCloseDelay, v -> config.autoCloseDelay = v.intValue()));
        autoGFS.add(new SliderEntry(contentX, contentWidth, "  Randomization (ticks)", 0, 10, () -> (float) config.autoCloseRandomization, v -> config.autoCloseRandomization = v.intValue()));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "  Close Chest On Input", () -> config.closeChestOnInput, v -> config.closeChestOnInput = v));
        autoGFS.add(new LabelEntry(contentX, "Movement"));
        autoGFS.add(new ToggleEntry(contentX, contentWidth, "Auto Sprint", () -> config.autoSprint, v -> config.autoSprint = v));
        categories.put("Auto", autoGFS);

        List<SettingsEntry> waypoints = new ArrayList<>();
        waypoints.add(new LabelEntry(contentX, "Secret Waypoints"));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Enable Secret Waypoints", () -> config.secretWaypoints, v -> config.secretWaypoints = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "  Hide When Collected", () -> config.secretWaypointHideCollected, v -> config.secretWaypointHideCollected = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "  Tracers to Waypoints", () -> config.secretWaypointTracers, v -> config.secretWaypointTracers = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "  Secret Click Highlight", () -> config.secretClickHighlight, v -> config.secretClickHighlight = v));
        waypoints.add(new LabelEntry(contentX, "Secret Types"));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Chests", () -> config.secretWaypointChests, v -> config.secretWaypointChests = v));
        waypoints.add(new ColorEntry(contentX, contentWidth, "  Chest", () -> config.colorSecretChest, v -> config.colorSecretChest = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Items", () -> config.secretWaypointItems, v -> config.secretWaypointItems = v));
        waypoints.add(new ColorEntry(contentX, contentWidth, "  Item", () -> config.colorSecretItem, v -> config.colorSecretItem = v));
        waypoints.add(new ColorEntry(contentX, contentWidth, "  Item Ready", () -> config.colorItemReady, v -> config.colorItemReady = v));
        waypoints.add(new ColorEntry(contentX, contentWidth, "  Item Nearly Ready", () -> config.colorItemSoon, v -> config.colorItemSoon = v));
        waypoints.add(new ColorEntry(contentX, contentWidth, "  Item Not Ready", () -> config.colorItemNotReady, v -> config.colorItemNotReady = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Bats", () -> config.secretWaypointBats, v -> config.secretWaypointBats = v));
        waypoints.add(new ColorEntry(contentX, contentWidth, "  Bat", () -> config.colorSecretBat, v -> config.colorSecretBat = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Essence", () -> config.secretWaypointEssence, v -> config.secretWaypointEssence = v));
        waypoints.add(new ColorEntry(contentX, contentWidth, "  Essence", () -> config.colorSecretEssence, v -> config.colorSecretEssence = v));
        waypoints.add(new ToggleEntry(contentX, contentWidth, "Show Redstone Key", () -> config.secretWaypointRedstoneKey, v -> config.secretWaypointRedstoneKey = v));
        waypoints.add(new ColorEntry(contentX, contentWidth, "  Redstone", () -> config.colorSecretRedstone, v -> config.colorSecretRedstone = v));

        List<SettingsEntry> leap = new ArrayList<>();
        leap.add(new ToggleEntry(contentX, contentWidth, "Enable Leap Overlay", () -> config.leapOverlay, v -> config.leapOverlay = v));
        leap.add(new ToggleEntry(contentX, contentWidth, "Show Player Heads", () -> config.leapShowHeads, v -> config.leapShowHeads = v));
        leap.add(new ToggleEntry(contentX, contentWidth, "Show Map (click to leap)", () -> config.leapShowMap, v -> config.leapShowMap = v));
        leap.add(new SliderEntry(contentX, contentWidth, "Overlay Scale", 0.5f, 2.0f,
                () -> config.leapScale, v -> config.leapScale = v));
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
        leap.add(new KeybindEntry(contentX, contentWidth, "Fast Leap (smart)", () -> config.fastLeapKey, v -> config.fastLeapKey = v));
        categories.put("Leap", leap);

        List<SettingsEntry> party = new ArrayList<>();
        party.add(new LabelEntry(contentX, "Party Chat & Alerts"));
        party.add(new ToggleEntry(contentX, contentWidth, "Party Chat Commands (!8ball etc)", () -> config.chatCommands, v -> config.chatCommands = v));
        party.add(new ToggleEntry(contentX, contentWidth, "Show PBs on Party Join (needs API key)", () -> config.pbOnJoin, v -> config.pbOnJoin = v));
        party.add(new InfoEntry(contentX, "Key: /tmap apikey <key> (developer.hypixel.net)"));
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
        score.add(new ToggleEntry(contentX, contentWidth, "Mimic Dead Message", () -> config.mimicDeadMessage, v -> config.mimicDeadMessage = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Prince Dead Message", () -> config.princeDeadMessage, v -> config.princeDeadMessage = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Crypt Reminder (<5 at boss)", () -> config.cryptReminder, v -> config.cryptReminder = v));
        score.add(new SoundDropdownEntry(contentX, contentWidth, "  Crypt Reminder Sound",
                com.teslamaps.utils.SoundOptions.keys(), () -> config.cryptReminderSound, v -> config.cryptReminderSound = v));
        score.add(new ClassFilterEntry(contentX, contentWidth, "  Crypt Reminder: Only as Class", config.cryptReminderClasses));
        score.add(new LabelEntry(contentX, "Dungeon Splits"));
        score.add(new ToggleEntry(contentX, contentWidth, "Enable Splits HUD", () -> config.splitsEnabled, v -> config.splitsEnabled = v));
        score.add(new ToggleEntry(contentX, contentWidth, "  Show PBs (per split)", () -> config.splitsShowPb, v -> config.splitsShowPb = v));
        score.add(new ToggleEntry(contentX, contentWidth, "  Send All Splits on End", () -> config.splitsSendAllOnEnd, v -> config.splitsSendAllOnEnd = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Live Room Splits HUD", () -> config.roomSplits, v -> config.roomSplits = v));
        score.add(new SliderEntry(contentX, contentWidth, "  Room Splits Scale", 0.5f, 2.0f,
                () -> config.roomSplitsScale, v -> config.roomSplitsScale = v, 1));
        score.add(new LabelEntry(contentX, "Alerts & Stats"));
        score.add(new ToggleEntry(contentX, contentWidth, "Insta-Clear Alert", () -> config.instaClearAlert, v -> config.instaClearAlert = v));
        score.add(new ToggleEntry(contentX, contentWidth, "Trinity Alert", () -> config.rareRoomAlert, v -> config.rareRoomAlert = v));
        score.add(new SliderEntry(contentX, contentWidth, "  Alert Volume", 0.0f, 5.0f,
                () -> config.rareRoomVolume, v -> config.rareRoomVolume = v, 1));
        score.add(new InfoEntry(contentX, "Title + chat when Trinity is scanned, once per run"));
        score.add(new ToggleEntry(contentX, contentWidth, "Auto Extra Stats", () -> config.autoExtraStats, v -> config.autoExtraStats = v));
        score.add(new InfoEntry(contentX, "Runs /showextrastats at the end of a run"));
        categories.put("Score & Splits", score);

        List<SettingsEntry> timers = new ArrayList<>();
        timers.add(new InfoEntry(contentX, "All timers share one movable HUD block (/tmap gui)."));
        timers.add(new LabelEntry(contentX, "Cooldowns"));
        timers.add(new ToggleEntry(contentX, contentWidth, "Warp Cooldown", () -> config.warpCooldown, v -> config.warpCooldown = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Wither Shield Timer (W-Impact)", () -> config.witherShieldTimer, v -> config.witherShieldTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Tactical Insertion Timer", () -> config.tacticalInsertionTimer, v -> config.tacticalInsertionTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Invincibility Timer", () -> config.invincibilityTimer, v -> config.invincibilityTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "  Announce Invincibility", () -> config.invincibilityAnnounce, v -> config.invincibilityAnnounce = v));
        timers.add(new LabelEntry(contentX, "Dungeon"));
        timers.add(new ToggleEntry(contentX, contentWidth, "Spirit Pet Reminder (boss)", () -> config.spiritPetReminder, v -> config.spiritPetReminder = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Secret Tick Timer", () -> config.secretTickTimer, v -> config.secretTickTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "Bossbar Health (❤)", () -> config.bossbarHealth, v -> config.bossbarHealth = v));
        timers.add(new DropdownEntry(contentX, contentWidth, "Timer Format",
                new String[]{"Seconds", "Ticks", "Both"}, () -> config.tickTimerFormat, v -> config.tickTimerFormat = v));
        timers.add(new LabelEntry(contentX, "Kicked"));
        timers.add(new ToggleEntry(contentX, contentWidth, "Kicked Timer", () -> config.kickedTimer, v -> config.kickedTimer = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "  Send /pc kicked", () -> config.kickedPartyMessage, v -> config.kickedPartyMessage = v));
        timers.add(new ToggleEntry(contentX, contentWidth, "  Center Warning (60s)", () -> config.kickedCenterWarning, v -> config.kickedCenterWarning = v));
        categories.put("Timers", timers);

        List<SettingsEntry> bloodCamp = new ArrayList<>();
        bloodCamp.add(new ClassFilterEntry(contentX, contentWidth, "Blood Camp: Only as Class", config.bloodCampClasses));
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
        bloodCamp.add(new LabelEntry(contentX, "Watcher"));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Watcher Kill Alert (blood done)", () -> config.watcherKillAlert, v -> config.watcherKillAlert = v));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Watcher Speed Grade", () -> config.watcherSpeedGrade, v -> config.watcherSpeedGrade = v));
        bloodCamp.add(new ClassFilterEntry(contentX, contentWidth, "  Watcher Grade: Only as Class", config.watcherGradeClasses));
        bloodCamp.add(new ToggleEntry(contentX, contentWidth, "Watcher Stats HUD", () -> config.watcherHud, v -> config.watcherHud = v));
        bloodCamp.add(new SliderEntry(contentX, contentWidth, "  Watcher HUD Scale", 0.5f, 2.0f,
                () -> config.watcherHudScale, v -> config.watcherHudScale = v, 1));
        categories.put("Blood Camp", bloodCamp);

        List<SettingsEntry> waypointsCat = new ArrayList<>();
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Dungeon Waypoints", () -> config.dungeonWaypoints, v -> config.dungeonWaypoints = v));

        waypointsCat.add(new LabelEntry(contentX, "Add Waypoint #1"));
        waypointsCat.add(new KeybindEntry(contentX, contentWidth, "Keybind #1 (look at block)", () -> config.waypointAddKey, v -> config.waypointAddKey = v));
        waypointsCat.add(new ColorEntry(contentX, contentWidth, "Color #1", () -> config.waypointAddColor, v -> config.waypointAddColor = v));
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Filled Box #1", () -> config.waypointAddFilled, v -> config.waypointAddFilled = v));
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Through Walls #1", () -> config.waypointAddThroughWalls, v -> config.waypointAddThroughWalls = v));

        waypointsCat.add(new LabelEntry(contentX, "Add Waypoint #2"));
        waypointsCat.add(new KeybindEntry(contentX, contentWidth, "Keybind #2 (look at block)", () -> config.waypointAdd2Key, v -> config.waypointAdd2Key = v));
        waypointsCat.add(new ColorEntry(contentX, contentWidth, "Color #2", () -> config.waypointAdd2Color, v -> config.waypointAdd2Color = v));
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Filled Box #2", () -> config.waypointAdd2Filled, v -> config.waypointAdd2Filled = v));
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Through Walls #2", () -> config.waypointAdd2ThroughWalls, v -> config.waypointAdd2ThroughWalls = v));

        waypointsCat.add(new LabelEntry(contentX, "Add Waypoint #3"));
        waypointsCat.add(new KeybindEntry(contentX, contentWidth, "Keybind #3 (look at block)", () -> config.waypointAdd3Key, v -> config.waypointAdd3Key = v));
        waypointsCat.add(new ColorEntry(contentX, contentWidth, "Color #3", () -> config.waypointAdd3Color, v -> config.waypointAdd3Color = v));
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Filled Box #3", () -> config.waypointAdd3Filled, v -> config.waypointAdd3Filled = v));
        waypointsCat.add(new ToggleEntry(contentX, contentWidth, "Through Walls #3", () -> config.waypointAdd3ThroughWalls, v -> config.waypointAdd3ThroughWalls = v));

        waypointsCat.add(new LabelEntry(contentX, "Remove / Clear Keybinds"));
        waypointsCat.add(new KeybindEntry(contentX, contentWidth, "Remove (nearest)", () -> config.waypointRemoveKey, v -> config.waypointRemoveKey = v));
        waypointsCat.add(new KeybindEntry(contentX, contentWidth, "Clear (room)", () -> config.waypointClearKey, v -> config.waypointClearKey = v));
        waypointsCat.add(new InfoEntry(contentX, "File: config/teslamaps/dungeon_waypoints.json"));
        waypointsCat.add(new InfoEntry(contentX, "Odin format. Reload: /tmap waypoints"));
        waypoints.add(new LabelEntry(contentX, "Dungeon Waypoints"));
        waypoints.addAll(waypointsCat);
        categories.put("Secrets & Waypoints", waypoints);

        List<SettingsEntry> ordered = new ArrayList<>();
        ordered.add(new InfoEntry(contentX, "Routes outside dungeons - Coleweight / Skytils format"));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Render Route",
                () -> config.orderedWaypoints, v -> config.orderedWaypoints = v));
        ordered.add(new ButtonEntry(contentX, contentWidth, "Manage Routes",
                () -> Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(new WaypointRoutesScreen()))));
        ordered.add(new ButtonEntry(contentX, contentWidth, "Import From Clipboard",
                () -> orderedFeedback(com.teslamaps.features.waypoints.OrderedWaypoints.importClipboard())));
        ordered.add(new ButtonEntry(contentX, contentWidth, "Unload Route",
                () -> orderedFeedback(com.teslamaps.features.waypoints.OrderedWaypoints.unload())));
        ordered.add(new InfoEntry(contentX, "/tmap waypoints for the GUI, /tmap route ... for commands"));

        ordered.add(new LabelEntry(contentX, "Display"));
        ordered.add(new ColorEntry(contentX, contentWidth, "Current Color", () -> config.orderedColorCurrent, v -> config.orderedColorCurrent = v));
        ordered.add(new ColorEntry(contentX, contentWidth, "Previous Color", () -> config.orderedColorPrevious, v -> config.orderedColorPrevious = v));
        ordered.add(new ColorEntry(contentX, contentWidth, "Next Color", () -> config.orderedColorNext, v -> config.orderedColorNext = v));
        ordered.add(new SliderEntry(contentX, contentWidth, "Next Waypoints", 1f, 5f,
                () -> (float) config.orderedNextCount, v -> config.orderedNextCount = Math.round(v)));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Filled Boxes", () -> config.orderedFillBlock, v -> config.orderedFillBlock = v));
        ordered.add(new SliderEntry(contentX, contentWidth, "Outline Thickness", 1f, 10f,
                () -> config.orderedOutlineThickness, v -> config.orderedOutlineThickness = v));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Show Number", () -> config.orderedShowName, v -> config.orderedShowName = v));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Show Distance", () -> config.orderedShowDistance, v -> config.orderedShowDistance = v));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Show All Waypoints", () -> config.orderedShowAll, v -> config.orderedShowAll = v));
        ordered.add(new ColorEntry(contentX, contentWidth, "  All Color", () -> config.orderedColorAll, v -> config.orderedColorAll = v));

        ordered.add(new LabelEntry(contentX, "Trace Line"));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Trace Line", () -> config.orderedTraceLine, v -> config.orderedTraceLine = v));
        ordered.add(new ColorEntry(contentX, contentWidth, "  Trace Line Color", () -> config.orderedColorTraceLine, v -> config.orderedColorTraceLine = v));

        ordered.add(new LabelEntry(contentX, "Progression"));
        ordered.add(new SliderEntry(contentX, contentWidth, "Waypoint Range", 1f, 10f,
                () -> config.orderedWaypointRange, v -> config.orderedWaypointRange = v, 1));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Auto-Skip Forward", () -> config.orderedAutoSkipForward, v -> config.orderedAutoSkipForward = v));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Unload On World Change", () -> config.orderedAutoUnload, v -> config.orderedAutoUnload = v));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Auto-Load Route Named Like Area", () -> config.orderedAutoLoadByArea, v -> config.orderedAutoLoadByArea = v));
        ordered.add(new InfoEntry(contentX, "Save a route as e.g. \"Crystal Hollows\" to load it there"));

        ordered.add(new LabelEntry(contentX, "Setup Mode"));
        ordered.add(new ToggleEntry(contentX, contentWidth, "Setup Mode", () -> config.orderedSetupMode, v -> config.orderedSetupMode = v));
        ordered.add(new InfoEntry(contentX, "Shows nearby waypoints + the line between them"));
        ordered.add(new ColorEntry(contentX, contentWidth, "  Setup Waypoint Color", () -> config.orderedColorSetup, v -> config.orderedColorSetup = v));
        ordered.add(new ColorEntry(contentX, contentWidth, "  Setup Line Color", () -> config.orderedColorSetupLine, v -> config.orderedColorSetupLine = v));
        ordered.add(new SliderEntry(contentX, contentWidth, "  Setup Range", 1f, 100f,
                () -> config.orderedSetupRange, v -> config.orderedSetupRange = v));
        ordered.add(new SliderEntry(contentX, contentWidth, "  Setup Line Thickness", 1f, 10f,
                () -> config.orderedSetupLineThickness, v -> config.orderedSetupLineThickness = v));
        ordered.add(new ToggleEntry(contentX, contentWidth, "  Sneaking During Route", () -> config.orderedSneaking, v -> config.orderedSneaking = v));
        categories.put("Ordered Waypoints", ordered);

        List<SettingsEntry> croesus = new ArrayList<>();
        croesus.add(new ToggleEntry(contentX, contentWidth, "Croesus Profit Overlay", () -> config.croesusProfitOverlay, v -> config.croesusProfitOverlay = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Highlight Most Profitable", () -> config.croesusHighlightBest, v -> config.croesusHighlightBest = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Gray Out Other Chests", () -> config.croesusDimOthers, v -> config.croesusDimOthers = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Highlight Unopened (green)", () -> config.croesusHighlightUnopened, v -> config.croesusHighlightUnopened = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Hide Opened Chests", () -> config.croesusHideOpened, v -> config.croesusHideOpened = v));
        croesus.add(new ToggleEntry(contentX, contentWidth, "Compact Mode (profit only)", () -> config.croesusCompact, v -> config.croesusCompact = v));
        categories.put("Croesus", croesus);

        List<SettingsEntry> inventory = new ArrayList<>();
        inventory.add(new LabelEntry(contentX, "Container Helpers"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Salvage Helper", () -> config.salvageHelper, v -> config.salvageHelper = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Sellable Items Highlighter", () -> config.sellableHighlighter, v -> config.sellableHighlighter = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Show Selected Pet", () -> config.showSelectedPet, v -> config.showSelectedPet = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Page Keybinds (A/D, W/S)", () -> config.pageKeybinds, v -> config.pageKeybinds = v));
        inventory.add(new InfoEntry(contentX, "A/D = Previous/Next Page, W/S = Scroll Up/Down"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Sign Enter Key", () -> config.signEnterKey, v -> config.signEnterKey = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "No Cursor Reset", () -> config.noCursorReset, v -> config.noCursorReset = v));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Hold Time (s)", 0.5f, 30f,
                () -> config.noCursorResetHold, v -> config.noCursorResetHold = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Own Nametag (3rd person)", () -> config.ownNameTag, v -> config.ownNameTag = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Searchbar (type to search)", () -> config.searchbar, v -> config.searchbar = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Backpack/Ender Preview", () -> config.backpackPreview, v -> config.backpackPreview = v));
        inventory.add(new LabelEntry(contentX, "Item Search"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Index Opened Containers", () -> config.itemSearchIndex, v -> config.itemSearchIndex = v));
        inventory.add(new InfoEntry(contentX, "/tmap search finds items in museum, sacks, vault, chests, storage"));
        inventory.add(new KeybindEntry(contentX, contentWidth, "  Open Item Search", () -> config.itemSearchKey, v -> config.itemSearchKey = v));
        inventory.add(new ButtonEntry(contentX, contentWidth, "Open Item Search",
                () -> minecraft.setScreen(new ItemSearchScreen(null))));
        inventory.add(new ButtonEntry(contentX, contentWidth, "Clear Item Index",
                com.teslamaps.features.ItemIndex::clear));
        inventory.add(new LabelEntry(contentX, "Auction Price Input"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Auction Price Input", () -> config.auctionPriceInput, v -> config.auctionPriceInput = v));
        inventory.add(new DropdownEntry(contentX, contentWidth, "  Mode",
                new String[]{"Normal", "Undercut"}, () -> config.auctionDefaultMode, v -> config.auctionDefaultMode = v));
        inventory.add(new LabelEntry(contentX, "Auction Price Comparison"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Highlight Value Deals", () -> config.auctionPriceCompare, v -> config.auctionPriceCompare = v));
        inventory.add(new InfoEntry(contentX, "Green when it sells below the estimated item value, red above"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "  Only Good Deals", () -> config.auctionCompareOnlyGood, v -> config.auctionCompareOnlyGood = v));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Threshold (%)", 1f, 50f,
                () -> config.auctionCompareThreshold, v -> config.auctionCompareThreshold = v, 0));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Opacity", 0.05f, 1f,
                () -> config.auctionCompareOpacity, v -> config.auctionCompareOpacity = v, 2));
        inventory.add(new LabelEntry(contentX, "Auction / Bazaar Search"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Custom Search Input", () -> config.searchOverlay, v -> config.searchOverlay = v));
        inventory.add(new InfoEntry(contentX, "Textbox with item name suggestions instead of the search sign"));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Suggestions", 0f, 12f,
                () -> (float) config.searchOverlaySuggestions, v -> config.searchOverlaySuggestions = Math.round(v)));
        inventory.add(new ToggleEntry(contentX, contentWidth, "  Keep Previous Text", () -> config.searchOverlayKeepText, v -> config.searchOverlayKeepText = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "  Recent Searches", () -> config.searchOverlayHistory, v -> config.searchOverlayHistory = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Middle Click Copies Item Name", () -> config.middleClickCopyName, v -> config.middleClickCopyName = v));
        inventory.add(new LabelEntry(contentX, "Storage Overlay"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Custom Storage Overlay", () -> config.customStorageOverlay, v -> config.customStorageOverlay = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "  Show Page Names", () -> config.storageShowNames, v -> config.storageShowNames = v));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Overlay Scale", 0.5f, 1.5f,
                () -> config.storageOverlayScale, v -> config.storageOverlayScale = v));
        inventory.add(new LabelEntry(contentX, "Hovered Slot"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Hovered Slot Outline", () -> config.hoveredSlotOutline, v -> config.hoveredSlotOutline = v));
        inventory.add(new ColorEntry(contentX, contentWidth, "  Outline Color", () -> config.colorHoveredSlot, v -> config.colorHoveredSlot = v));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Thickness", 1f, 3f,
                () -> (float) config.hoveredSlotThickness, v -> config.hoveredSlotThickness = Math.round(v)));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Fill Opacity", 0f, 0.5f,
                () -> config.hoveredSlotFill, v -> config.hoveredSlotFill = v));
        inventory.add(new LabelEntry(contentX, "Slot Lock"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Slot Lock", () -> config.slotLock, v -> config.slotLock = v));
        inventory.add(new KeybindEntry(contentX, contentWidth, "  Slot Lock Key (over slot)", () -> config.slotLockKey, v -> config.slotLockKey = v));
        inventory.add(new ColorEntry(contentX, contentWidth, "  Locked Slot Color", () -> config.colorSlotLock, v -> config.colorSlotLock = v));
        inventory.add(new LabelEntry(contentX, "Item Protection"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Protect Valuable Items", () -> config.itemProtection, v -> config.itemProtection = v));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Min Value (million)", 1f, 500f,
                () -> (float) config.itemProtectionValue, v -> config.itemProtectionValue = Math.round(v), 0));
        inventory.add(new ToggleEntry(contentX, contentWidth, "  Block Salvage & Selling", () -> config.itemProtectionSell, v -> config.itemProtectionSell = v));
        inventory.add(new InfoEntry(contentX, "Blocks the Q drop and dropping onto empty space"));
        inventory.add(new InfoEntry(contentX, "Hold ctrl to override. Uses the estimated item value"));
        inventory.add(new LabelEntry(contentX, "Slot Binds"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Slot Binds", () -> config.slotBinds, v -> config.slotBinds = v));
        inventory.add(new KeybindEntry(contentX, contentWidth, "Bind Set Key (over slot)", () -> config.slotBindsKey, v -> config.slotBindsKey = v));
        inventory.add(new ColorEntry(contentX, contentWidth, "  Bind Color", () -> config.colorSlotBinds, v -> config.colorSlotBinds = v));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Line Width", 0.1f, 2f,
                () -> config.slotBindsLineWidth, v -> config.slotBindsLineWidth = v));
        inventory.add(new DropdownEntry(contentX, contentWidth, "  Line Display",
                com.teslamaps.features.SlotBinds.LINE_MODES, () -> config.slotBindsLineDisplay, v -> config.slotBindsLineDisplay = v));
        inventory.add(new DropdownEntry(contentX, contentWidth, "  Profile",
                com.teslamaps.features.SlotBinds.PROFILES, () -> config.slotBindsProfile, v -> config.slotBindsProfile = v));
        inventory.add(new LabelEntry(contentX, "Customization"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Button In Inventory", () -> config.showCustomizeButton, v -> config.showCustomizeButton = v));
        inventory.add(new ButtonEntry(contentX, contentWidth, "Open Customize Screen",
                () -> minecraft.setScreen(new com.teslamaps.screen.customize.CustomizeScreen(this, false))));
        inventory.add(new InfoEntry(contentX, "Rename, dye, trim and reskin items - client side only (/tmap customize)"));
        inventory.add(new LabelEntry(contentX, "Rarity Backgrounds"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Enable Rarity Backgrounds", () -> config.rarityBackgrounds, v -> config.rarityBackgrounds = v));
        inventory.add(new SliderEntry(contentX, contentWidth, "  Opacity", 0f, 1f,
                () -> config.rarityBgOpacity, v -> config.rarityBgOpacity = v));
        inventory.add(new DropdownEntry(contentX, contentWidth, "  Shape",
                com.teslamaps.features.RarityBackground.SHAPES, () -> config.rarityBgShape, v -> config.rarityBgShape = v));
        inventory.add(new DropdownEntry(contentX, contentWidth, "  Style",
                com.teslamaps.features.RarityBackground.STYLES, () -> config.rarityBgStyle, v -> config.rarityBgStyle = v));
        inventory.add(new LabelEntry(contentX, "Bazaar & Progress"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Bazaar: Highlight Own Orders", () -> config.bazaarOwnOrders, v -> config.bazaarOwnOrders = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Bazaar: Check Mark On Filled Orders", () -> config.bazaarOrderFilled, v -> config.bazaarOrderFilled = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Progress Highlight", () -> config.progressHighlight, v -> config.progressHighlight = v));
        inventory.add(new ColorEntry(contentX, contentWidth, "  Progress Color", () -> config.colorProgressDone, v -> config.colorProgressDone = v));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Commission Highlight", () -> config.commissionHighlight, v -> config.commissionHighlight = v));
        inventory.add(new ColorEntry(contentX, contentWidth, "  Commission Color", () -> config.colorCommissionDone, v -> config.colorCommissionDone = v));
        inventory.add(new LabelEntry(contentX, "Wardrobe & Loadout Keybinds"));
        inventory.add(new ToggleEntry(contentX, contentWidth, "Wardrobe Keybinds", () -> config.wardrobeKeybinds, v -> config.wardrobeKeybinds = v));
        inventory.add(new InfoEntry(contentX, "Unbound slots fall back to your hotbar keys (1-9)"));
        inventory.add(new KeybindEntry(contentX, contentWidth, "  Next Page",
                () -> config.wardrobeNextPageKey, v -> config.wardrobeNextPageKey = v));
        inventory.add(new KeybindEntry(contentX, contentWidth, "  Previous Page",
                () -> config.wardrobePrevPageKey, v -> config.wardrobePrevPageKey = v));
        inventory.add(new LabelEntry(contentX, "Wardrobe Slots (9 per page)"));
        for (int i = 0; i < 9; i++) {
            final int idx = i;
            inventory.add(new KeybindEntry(contentX, contentWidth, "Wardrobe Slot " + (i + 1),
                    () -> config.wardrobeKeys[idx], v -> config.wardrobeKeys[idx] = v));
        }
        // 12 per page, so 10-12 need binds of their own - the hotbar only has nine keys
        inventory.add(new LabelEntry(contentX, "Loadouts (12 per page)"));
        for (int i = 0; i < 12; i++) {
            final int idx = i;
            inventory.add(new KeybindEntry(contentX, contentWidth, "Loadout " + (i + 1),
                    () -> config.loadoutKeys[idx], v -> config.loadoutKeys[idx] = v));
        }
        List<SettingsEntry> media = new ArrayList<>();
        media.add(new LabelEntry(contentX, "Screenshots"));
        media.add(new ToggleEntry(contentX, contentWidth, "Auto Copy To Clipboard",
                () -> config.autoCopyScreenshot, v -> config.autoCopyScreenshot = v));
        media.add(new ToggleEntry(contentX, contentWidth, "Chat Actions",
                () -> config.screenshotChatActions, v -> config.screenshotChatActions = v));
        media.add(new InfoEntry(contentX, "Clickable buttons under a saved screenshot"));
        media.add(new ToggleEntry(contentX, contentWidth, "Hide Vanilla Message",
                () -> config.screenshotHideVanillaMessage, v -> config.screenshotHideVanillaMessage = v));
        media.add(new ButtonEntry(contentX, contentWidth, "Open Screenshot Browser", () ->
                net.minecraft.client.Minecraft.getInstance().schedule(() ->
                        net.minecraft.client.Minecraft.getInstance().setScreen(new ScreenshotScreen(null)))));

        media.add(new LabelEntry(contentX, "Shared Images"));
        media.add(new ToggleEntry(contentX, contentWidth, "Show Shared Images In Chat",
                () -> config.imageShareReceive, v -> config.imageShareReceive = v));
        media.add(new SliderEntry(contentX, contentWidth, "  Image Height (chat lines)", 3f, 24f,
                () -> (float) config.imageShareChatLines, v -> config.imageShareChatLines = v.intValue()));
        media.add(new KeybindEntry(contentX, contentWidth, "  Hold To Enlarge",
                () -> config.imagePeekKey, v -> config.imagePeekKey = v));
        media.add(new InfoEntry(contentX, "Hold the key to blow up the newest image"));
        media.add(new DropdownEntry(contentX, contentWidth, "Upload Lifetime",
                new String[]{"1h", "12h", "24h", "72h", "permanent"},
                () -> config.imageShareExpiry, v -> config.imageShareExpiry = v));
        categories.put("Screenshots", media);

        categories.put("Inventory", inventory);

        List<SettingsEntry> tooltips = new ArrayList<>();
        tooltips.add(new LabelEntry(contentX, "Tooltips"));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Scrollable Tooltip", () -> config.scrollableTooltip, v -> config.scrollableTooltip = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Compactor/Deletor Preview", () -> config.compactorPreview, v -> config.compactorPreview = v));
        tooltips.add(new InfoEntry(contentX, "Tooltip shows on/off status and configured items"));
        tooltips.add(new LabelEntry(contentX, "Item Value"));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Estimated Item Value", () -> config.estimatedValue, v -> config.estimatedValue = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Value Breakdown Panel (hover)", () -> config.itemValueGui, v -> config.itemValueGui = v));
        tooltips.add(new SliderEntry(contentX, contentWidth, "  Panel Scale", 0.5f, 2.0f,
                () -> config.itemValueScale, v -> config.itemValueScale = v, 2));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "  Free Position", () -> config.itemValueFixedPos, v -> config.itemValueFixedPos = v));
        tooltips.add(new InfoEntry(contentX, "Drag it in /tmap gui instead of pinning it to the container"));
        tooltips.add(new SliderEntry(contentX, contentWidth, "Hover Delay (ms)", 0f, 2000f,
                () -> (float) config.itemValueHoverDelay, v -> config.itemValueHoverDelay = Math.round(v / 50f) * 50));
        tooltips.add(new SliderEntry(contentX, contentWidth, "Enchantments Cap", 1f, 30f,
                () -> (float) config.itemValueEnchantmentsCap, v -> config.itemValueEnchantmentsCap = Math.round(v)));
        tooltips.add(new SliderEntry(contentX, contentWidth, "Star Material Cap", 1f, 15f,
                () -> (float) config.itemValueStarMaterialCap, v -> config.itemValueStarMaterialCap = Math.round(v)));
        tooltips.add(new DropdownEntry(contentX, contentWidth, "Price Source",
                new String[]{"BZ Instant Buy", "BZ Instant Sell", "Lowest BIN", "NPC Sell"},
                () -> config.itemValuePriceSource, v -> config.itemValuePriceSource = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Lowest BIN / Bazaar Line", () -> config.itemValueShowBin, v -> config.itemValueShowBin = v));
        tooltips.add(new InfoEntry(contentX, "Market price on every tooltip, BZ insta buy/sell or lowest BIN"));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Show Exact Price", () -> config.itemValueExactPrice, v -> config.itemValueExactPrice = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Ignore Helmet Skins", () -> config.itemValueIgnoreHelmetSkins, v -> config.itemValueIgnoreHelmetSkins = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Ignore Armor Dyes", () -> config.itemValueIgnoreArmorDyes, v -> config.itemValueIgnoreArmorDyes = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Ignore Runes", () -> config.itemValueIgnoreRunes, v -> config.itemValueIgnoreRunes = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Container Value (Top 5)", () -> config.containerValue, v -> config.containerValue = v));
        tooltips.add(new LabelEntry(contentX, "Enchant Colors"));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Enchant Colors", () -> config.enchantColors, v -> config.enchantColors = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Levels as Numbers", () -> config.enchantLevelsAsNumbers, v -> config.enchantLevelsAsNumbers = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Chroma Max Enchants (rainbow)", () -> config.enchantChromaMax, v -> config.enchantChromaMax = v));
        tooltips.add(new SliderEntry(contentX, contentWidth, "  Chroma Size", 5f, 120f,
                () -> config.chromaSize, v -> config.chromaSize = v, 0));
        tooltips.add(new SliderEntry(contentX, contentWidth, "  Chroma Speed", 0f, 30f,
                () -> config.chromaSpeed, v -> config.chromaSpeed = v, 1));
        tooltips.add(new SliderEntry(contentX, contentWidth, "  Chroma Saturation", 0f, 1f,
                () -> config.chromaSaturation, v -> config.chromaSaturation = v, 2));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "  Chroma Slant Forward", () -> config.chromaForward, v -> config.chromaForward = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "  Chroma Bold", () -> config.enchantChromaBold, v -> config.enchantChromaBold = v));
        tooltips.add(new ToggleEntry(contentX, contentWidth, "Bold Max Level", () -> config.enchantBoldMax, v -> config.enchantBoldMax = v));
        tooltips.add(new ColorEntry(contentX, contentWidth, "Max Level Color", () -> config.enchantColorMax, v -> config.enchantColorMax = v));
        tooltips.add(new ColorEntry(contentX, contentWidth, "High Level Color", () -> config.enchantColorHigh, v -> config.enchantColorHigh = v));
        tooltips.add(new ColorEntry(contentX, contentWidth, "Normal Level Color", () -> config.enchantColorNormal, v -> config.enchantColorNormal = v));
        tooltips.add(new ColorEntry(contentX, contentWidth, "Bad Level Color", () -> config.enchantColorBad, v -> config.enchantColorBad = v));
        categories.put("Tooltips & Value", tooltips);

        List<SettingsEntry> chatFilter = new ArrayList<>();
        chatFilter.add(new LabelEntry(contentX, "Chat QoL"));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Infinite Chat History", () -> config.infiniteChat, v -> config.infiniteChat = v));
        chatFilter.add(new SliderEntry(contentX, contentWidth, "History Cap (lines)", 1024f, 32768f,
                () -> (float) config.chatHistoryLimit, v -> config.chatHistoryLimit = v.intValue()));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Stack Duplicate Messages", () -> config.chatStacking, v -> config.chatStacking = v));
        chatFilter.add(new SliderEntry(contentX, contentWidth, "Stack Window (min)", 1f, 30f,
                () -> (float) config.chatStackWindowMinutes, v -> config.chatStackWindowMinutes = Math.round(v)));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Right-Click Copy Chat", () -> config.chatCopyEnabled, v -> config.chatCopyEnabled = v));
        chatFilter.add(new SliderEntry(contentX, contentWidth, "Hotkey Delay After Chat (ms)", 0f, 1000f,
                () -> (float) config.hotkeyChatDelay, v -> config.hotkeyChatDelay = Math.round(v)));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Gradient [TeslaMaps] Prefix", () -> config.chromaPrefix, v -> config.chromaPrefix = v));
        chatFilter.add(new ColorEntry(contentX, contentWidth, "Prefix Gradient Color A", () -> config.prefixFadeColorA, v -> config.prefixFadeColorA = v));
        chatFilter.add(new ColorEntry(contentX, contentWidth, "Prefix Gradient Color B", () -> config.prefixFadeColorB, v -> config.prefixFadeColorB = v));
        chatFilter.add(new LabelEntry(contentX, "Filter"));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Enable Chat Filter", () -> config.chatFilterEnabled, v -> config.chatFilterEnabled = v));
        chatFilter.add(new ButtonEntry(contentX, contentWidth, "Toggle All Filters", () -> {
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
        }));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Empty Lines", () -> config.chatFilterEmpty, v -> config.chatFilterEmpty = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Watcher Messages", () -> config.chatFilterWatcher, v -> config.chatFilterWatcher = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide F4/M4 Boss (Thorn)", () -> config.chatFilterF4Boss, v -> config.chatFilterF4Boss = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Blessings", () -> config.chatFilterBlessings, v -> config.chatFilterBlessings = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Essence Messages", () -> config.chatFilterEssence, v -> config.chatFilterEssence = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Key Pickups", () -> config.chatFilterKeys, v -> config.chatFilterKeys = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Revive Stone Obtained", () -> config.chatFilterReviveStone, v -> config.chatFilterReviveStone = v));
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
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Wither King Heroes Line", () -> config.chatFilterWitherKingHeroes, v -> config.chatFilterWitherKingHeroes = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Sacks", () -> config.chatFilterSacks, v -> config.chatFilterSacks = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide GFS (Sacks to inventory)", () -> config.chatFilterGfs, v -> config.chatFilterGfs = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Watchdog Announcements", () -> config.chatFilterWatchdog, v -> config.chatFilterWatchdog = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Mort Messages", () -> config.chatFilterMort, v -> config.chatFilterMort = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Hide Milestones", () -> config.chatFilterMilestone, v -> config.chatFilterMilestone = v));
        chatFilter.add(new LabelEntry(contentX, "Command Suggestions"));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "Suggest /warp and /call targets", () -> config.commandSuggestions, v -> config.commandSuggestions = v));
        chatFilter.add(new ToggleEntry(contentX, contentWidth, "  Suggest Players (/msg, /visit, ...)", () -> config.commandSuggestionsPlayers, v -> config.commandSuggestionsPlayers = v));
        chatFilter.add(new InfoEntry(contentX, "Run /g list and /f list once to fill the name list"));
        chatFilter.add(new InfoEntry(contentX, "Player suggestions need a rejoin, and can clash with Hypixel's own commands"));
        chatFilter.add(new InfoEntry(contentX, "Hypixel sends no suggestions for these two"));
        categories.put("Chat", chatFilter);

        List<SettingsEntry> mining = new ArrayList<>();
        mining.add(new LabelEntry(contentX, "Crystal Hollows Scanner"));
        mining.add(new ToggleEntry(contentX, contentWidth, "Crystal Hollows Scanner", () -> config.chScanner, v -> config.chScanner = v));
        mining.add(new InfoEntry(contentX, "Scans loaded chunks, boxes + chat alert"));
        mining.add(new LabelEntry(contentX, "Structures"));
        mining.add(new ToggleEntry(contentX, contentWidth, "Golden Dragon Nest", () -> config.chScanDragon, v -> config.chScanDragon = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "Crystals (King/Queen/Temple/City/Divan/Bal)", () -> config.chScanCrystals, v -> config.chScanCrystals = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "Mob Spots (Corleone/Keys/Xalx/Pete/Odawa)", () -> config.chScanMobSpots, v -> config.chScanMobSpots = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "Fairy Grottos", () -> config.chScanGrottos, v -> config.chScanGrottos = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "Worm Fishing Spots", () -> config.chScanWorm, v -> config.chScanWorm = v));
        mining.add(new LabelEntry(contentX, "Display"));
        mining.add(new ToggleEntry(contentX, contentWidth, "Tracers", () -> config.chScanTracers, v -> config.chScanTracers = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "Chat Alert", () -> config.chScanChat, v -> config.chScanChat = v));
        mining.add(new LabelEntry(contentX, "Glacite Corpses"));
        mining.add(new ToggleEntry(contentX, contentWidth, "Corpse ESP", () -> config.corpseESP, v -> config.corpseESP = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "  Type Colors", () -> config.corpseESPTypeColors, v -> config.corpseESPTypeColors = v));
        mining.add(new ColorEntry(contentX, contentWidth, "  Corpse Color", () -> config.colorCorpseESP, v -> config.colorCorpseESP = v));
        mining.add(new LabelEntry(contentX, "Drill / Pickaxe Ability"));
        mining.add(new ToggleEntry(contentX, contentWidth, "Ability Cooldown Display",
                () -> config.pickaxeAbilityDisplay, v -> config.pickaxeAbilityDisplay = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "  Only On Mining Islands",
                () -> config.pickaxeAbilityMiningOnly, v -> config.pickaxeAbilityMiningOnly = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "  Hide While Available",
                () -> config.pickaxeAbilityOnlyOnCooldown, v -> config.pickaxeAbilityOnlyOnCooldown = v));
        mining.add(new InfoEntry(contentX, "Needs the Pickaxe Ability tab widget: /tab"));
        mining.add(new LabelEntry(contentX, "Mineshaft Pity"));
        mining.add(new ToggleEntry(contentX, contentWidth, "Pity Counter Display",
                () -> config.mineshaftPityDisplay, v -> config.mineshaftPityDisplay = v));
        mining.add(new ToggleEntry(contentX, contentWidth, "Chat Line On Mineshaft Found",
                () -> config.mineshaftFoundChat, v -> config.mineshaftFoundChat = v));
        mining.add(new SliderEntry(contentX, contentWidth, "Mineshaft Chance Bonus (%)", 0f, 100f,
                () -> config.mineshaftChanceBonus, v -> config.mineshaftChanceBonus = v, 1));
        mining.add(new InfoEntry(contentX, "Set from Surveyor + Core of the Mountain when you open the HOTM menu"));
        mining.add(new ButtonEntry(contentX, contentWidth, "Reset Mineshaft Stats",
                () -> com.teslamaps.features.mining.MineshaftPity.resetStats()));
        mining.add(new InfoEntry(contentX, "Needs the Pity widget: /tab > Pity > Glacite Tunnels"));
        categories.put("Mining", mining);

        List<SettingsEntry> garden = new ArrayList<>();
        garden.add(new LabelEntry(contentX, "Plot Teleport Grid"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Plots Overlay",
                () -> config.gardenPlotsOverlay, v -> config.gardenPlotsOverlay = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "Close Inventory On Teleport",
                () -> config.gardenPlotsCloseOnClick, v -> config.gardenPlotsCloseOnClick = v));
        garden.add(new LabelEntry(contentX, "Visitors"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Charmed Visitor List",
                () -> config.charmedVisitorList, v -> config.charmedVisitorList = v));
        garden.add(new InfoEntry(contentX, "Visitors charmed with the Gift Vinyl Set, click a name to drop it"));
        garden.add(new ButtonEntry(contentX, contentWidth, "Clear Charmed List",
                () -> com.teslamaps.features.CharmedVisitors.clear()));
        garden.add(new LabelEntry(contentX, "Pests"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Pest ESP",
                () -> config.pestESP, v -> config.pestESP = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "Pest Tracers",
                () -> config.pestTracers, v -> config.pestTracers = v));
        garden.add(new LabelEntry(contentX, "Pest Tracker"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Pest Overlay",
                () -> config.pestOverlay, v -> config.pestOverlay = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "Cooldown Arms Routine",
                () -> config.pestCooldownArmsRoutine, v -> config.pestCooldownArmsRoutine = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Profit Idle After (sec)", 30f, 600f,
                () -> (float) config.farmIdleSeconds,
                v -> config.farmIdleSeconds = (int) (float) v, 0));
        garden.add(new ToggleEntry(contentX, contentWidth, "Remind: Pest Chance Gear",
                () -> config.pestGearReminder, v -> config.pestGearReminder = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "Remind: Teleport To Pests",
                () -> config.pestTpReminder, v -> config.pestTpReminder = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "Custom Cooldown",
                () -> config.pestCustomCooldown, v -> config.pestCustomCooldown = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Custom Cooldown (sec)", 30f, 600f,
                () -> (float) config.pestCustomCooldownSeconds,
                v -> config.pestCustomCooldownSeconds = (int) (float) v, 0));
        garden.add(new SliderEntry(contentX, contentWidth, "Warn Seconds Before Ready", 0f, 30f,
                () -> (float) config.pestCooldownWarnAt, v -> config.pestCooldownWarnAt = (int) (float) v, 0));
        garden.add(new ToggleEntry(contentX, contentWidth, "Cooldown Title",
                () -> config.pestCooldownTitle, v -> config.pestCooldownTitle = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "Cooldown Sound",
                () -> config.pestCooldownSound, v -> config.pestCooldownSound = v));
        garden.add(new SoundDropdownEntry(contentX, contentWidth, "Cooldown Sound Type",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.pestCooldownSoundName, v -> config.pestCooldownSoundName = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Cooldown Volume", 0f, 4f,
                () -> config.pestCooldownVolume, v -> config.pestCooldownVolume = v, 2));
        garden.add(new LabelEntry(contentX, "Panels"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Move Panels From Inventory",
                () -> config.hudDragInInventory, v -> config.hudDragInInventory = v));
        garden.add(new LabelEntry(contentX, "Spray"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Queue Trip When Spray Runs Out",
                () -> config.sprayRoutine, v -> config.sprayRoutine = v));
        garden.add(new LabelEntry(contentX, "Round Timer"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Pest Round Timer",
                () -> config.pestTimer, v -> config.pestTimer = v));
        garden.add(new ButtonEntry(contentX, contentWidth, "Reset Pest PB",
                () -> com.teslamaps.features.PestTimer.resetPersonalBest()));
        garden.add(new LabelEntry(contentX, "Profit"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Profit Tracker",
                () -> config.farmProfit, v -> config.farmProfit = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Items Listed", 0f, 12f,
                () -> (float) config.farmProfitRows, v -> config.farmProfitRows = (int) (float) v, 0));
        garden.add(new ButtonEntry(contentX, contentWidth, "Show Breakdown In Chat",
                () -> com.teslamaps.features.FarmProfit.printBreakdown()));
        garden.add(new ButtonEntry(contentX, contentWidth, "Reset Session Profit",
                () -> com.teslamaps.features.FarmProfit.resetSession()));
        garden.add(new ButtonEntry(contentX, contentWidth, "Reset All Time Profit",
                () -> com.teslamaps.features.FarmProfit.resetAllTime()));
        garden.add(new LabelEntry(contentX, "Mouse"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Lower Sensitivity With Tool",
                () -> config.farmingSensitivity, v -> config.farmingSensitivity = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Sensitivity While Farming", 0f, 1f,
                () -> config.farmingSensitivityValue, v -> config.farmingSensitivityValue = v, 2));
        garden.add(new LabelEntry(contentX, "Farm Lane Bar"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Show Lane Bar",
                () -> config.farmBar, v -> config.farmBar = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Warn Distance", 0f, 20f,
                () -> config.farmWarnDistance, v -> config.farmWarnDistance = v, 0));
        garden.add(new SoundDropdownEntry(contentX, contentWidth, "Warn Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.farmWarnSound, v -> config.farmWarnSound = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Warn Volume", 0f, 4f,
                () -> config.farmWarnVolume, v -> config.farmWarnVolume = v, 2));
        garden.add(new ButtonEntry(contentX, contentWidth, "Set Lane Point 1 (here)",
                () -> com.teslamaps.features.FarmProgress.setPoint(1)));
        garden.add(new ButtonEntry(contentX, contentWidth, "Set Lane Point 2 (here)",
                () -> com.teslamaps.features.FarmProgress.setPoint(2)));
        garden.add(new LabelEntry(contentX, "Pest Fortune Buff"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Warn When Expired",
                () -> config.pestBuffWarning, v -> config.pestBuffWarning = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Warn Seconds Before Expiry", 0f, 300f,
                () -> (float) config.philipWarnSeconds,
                v -> config.philipWarnSeconds = (int) (float) v, 0));
        garden.add(new ToggleEntry(contentX, contentWidth, "Expiry Queues Philip Step",
                () -> config.philipArmsRoutine, v -> config.philipArmsRoutine = v));
        garden.add(new SoundDropdownEntry(contentX, contentWidth, "Warning Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.pestBuffSound, v -> config.pestBuffSound = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Warning Volume", 0f, 4f,
                () -> config.pestBuffVolume, v -> config.pestBuffVolume = v, 2));
        garden.add(new LabelEntry(contentX, "Pest Routine"));
        garden.add(new KeybindEntry(contentX, contentWidth, "Routine Key",
                () -> config.pestRoutineKey, v -> config.pestRoutineKey = v));
        garden.add(new KeybindEntry(contentX, contentWidth, "Dump Open Menu Key",
                () -> config.dumpGuiKey, v -> config.dumpGuiKey = v));
        garden.add(new DropdownEntry(contentX, contentWidth, "Pest Chance Loadout", LOADOUT_NAMES,
                () -> config.pestChanceLoadout, v -> config.pestChanceLoadout = v));
        garden.add(new DropdownEntry(contentX, contentWidth, "Rose Drag Loadout", LOADOUT_NAMES,
                () -> config.roseDragLoadout, v -> config.roseDragLoadout = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "Resume Attack After Loadout",
                () -> config.pestResumeAttack, v -> config.pestResumeAttack = v));
        garden.add(new KeybindEntry(contentX, contentWidth, "Attack Key (empty = vanilla)",
                () -> config.pestAttackKey, v -> config.pestAttackKey = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "Show Next Step HUD",
                () -> config.pestRoutineHud, v -> config.pestRoutineHud = v));
        garden.add(new SliderEntry(contentX, contentWidth, "Warp Delay", 0.0f, 5.0f,
                () -> config.pestWarpDelay, v -> config.pestWarpDelay = v, 1));
        garden.add(new LabelEntry(contentX, "Greenhouse Planner"));
        garden.add(new ToggleEntry(contentX, contentWidth, "Layout Ghost",
                () -> config.greenhousePlanner, v -> config.greenhousePlanner = v));
        garden.add(new InfoEntry(contentX, "Only active in the Garden · paste a code with /tmap greenhouse <code>"));
        garden.add(new DropdownEntry(contentX, contentWidth, "  Phase",
                new String[]{"Auto", "Floor", "Plants"},
                () -> com.teslamaps.features.GreenhousePlanner.phaseName(config.greenhousePhase),
                v -> config.greenhousePhase = "Floor".equals(v) ? 1 : "Plants".equals(v) ? 2 : 0));
        garden.add(new InfoEntry(contentX, "Auto shows the floor blocks first, plants once the floor is right"));
        garden.add(new ToggleEntry(contentX, contentWidth, "  Dim Everything But The Mutation",
                () -> config.greenhouseDimOthers, v -> config.greenhouseDimOthers = v));
        garden.add(new InfoEntry(contentX, "Rarest plant of the layout · /tmap greenhouse focus <plant|all>"));
        garden.add(new ToggleEntry(contentX, contentWidth, "  Growth Stage Colors",
                () -> config.greenhouseStages, v -> config.greenhouseStages = v));
        garden.add(new InfoEntry(contentX, "Red young to green ripe, with the stage over the plant"));
        garden.add(new SliderEntry(contentX, contentWidth, "    Highest Stage (0 = auto)", 0f, 20f,
                () -> (float) config.greenhouseStageMax, v -> config.greenhouseStageMax = Math.round(v), 0));
        garden.add(new ToggleEntry(contentX, contentWidth, "    Stage 1 Is Ripe",
                () -> config.greenhouseStageInverted, v -> config.greenhouseStageInverted = v));
        garden.add(new InfoEntry(contentX, "/tmap greenhouse stages lists what is actually on the plot"));
        garden.add(new ToggleEntry(contentX, contentWidth, "  Plant Names",
                () -> config.greenhouseLabels, v -> config.greenhouseLabels = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "  Hide Placed Cells",
                () -> config.greenhouseHideDone, v -> config.greenhouseHideDone = v));
        garden.add(new ToggleEntry(contentX, contentWidth, "  Through Walls",
                () -> config.greenhouseThroughWalls, v -> config.greenhouseThroughWalls = v));
        garden.add(new SliderEntry(contentX, contentWidth, "  Ghost Opacity", 0.05f, 1f,
                () -> config.greenhouseAlpha, v -> config.greenhouseAlpha = v, 2));
        garden.add(new SliderEntry(contentX, contentWidth, "  Rotation (x90°)", 0f, 3f,
                () -> (float) config.greenhouseRotation, v -> config.greenhouseRotation = Math.round(v), 0));
        garden.add(new ToggleEntry(contentX, contentWidth, "  Mirror",
                () -> config.greenhouseMirror, v -> config.greenhouseMirror = v));
        garden.add(new ButtonEntry(contentX, contentWidth, "Paste Code From Clipboard", () -> {
            String err = com.teslamaps.features.GreenhousePlanner.load(
                    net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard());
            com.teslamaps.features.GreenhousePlanner.rescan();
            net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(err == null
                            ? "§b[TeslaMaps] §fLayout loaded · " + com.teslamaps.features.GreenhousePlanner.status()
                            : "§b[TeslaMaps] §c" + err));
        }));
        categories.put("Garden", garden);

        List<SettingsEntry> foraging = new ArrayList<>();
        foraging.add(new InfoEntry(contentX, "For Moonglade Marsh and Torrhus Canyon"));
        foraging.add(new LabelEntry(contentX, "Chat"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Compact Tree Gifts",
                () -> config.foragingCompactGifts, v -> config.foragingCompactGifts = v));
        foraging.add(new InfoEntry(contentX, "One line instead of the whole gift block, hover kept"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Hide Sweep Details",
                () -> config.foragingHideSweepDetails, v -> config.foragingHideSweepDetails = v));
        foraging.add(new InfoEntry(contentX, "Toughness / Logs / penalty lines"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Hide Lottery Buffs",
                () -> config.foragingHideLottery, v -> config.foragingHideLottery = v));
        foraging.add(new InfoEntry(contentX, "Lottery and Beekeeper messages off the foraging islands"));
        foraging.add(new LabelEntry(contentX, "Sounds"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Mute Phantoms",
                () -> config.foragingMutePhantoms, v -> config.foragingMutePhantoms = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Mute Fusion Machine",
                () -> config.foragingMuteFusion, v -> config.foragingMuteFusion = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Mute Tree Breaking",
                () -> config.foragingMuteTreeBreak, v -> config.foragingMuteTreeBreak = v));
        foraging.add(new InfoEntry(contentX, "Mutes it everywhere except Moonglade / Torrhus"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "  Also on Moonglade / Torrhus",
                () -> config.foragingMuteTreeBreakGalatea, v -> config.foragingMuteTreeBreakGalatea = v));
        foraging.add(new LabelEntry(contentX, "Clean Tree View"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Hide Block Break Effects",
                () -> config.foragingCleanTreeView, v -> config.foragingCleanTreeView = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Hide Rune Effects",
                () -> config.foragingHideRuneEffects, v -> config.foragingHideRuneEffects = v));
        foraging.add(new LabelEntry(contentX, "Honeycomb"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Lather Reminder",
                () -> config.latherReminder, v -> config.latherReminder = v));
        foraging.add(new InfoEntry(contentX, "12 min per tree, tracked for Helix / Fig / Mangrove"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "  Show Title",
                () -> config.latherReminderTitle, v -> config.latherReminderTitle = v));
        foraging.add(new SoundDropdownEntry(contentX, contentWidth, "  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.latherReminderSound, v -> config.latherReminderSound = v));
        foraging.add(new SliderEntry(contentX, contentWidth, "  Volume", 0f, 4f,
                () -> config.latherReminderVolume, v -> config.latherReminderVolume = v, 2));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Lather Overlay",
                () -> config.latherOverlay, v -> config.latherOverlay = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "  Foraging Islands Only",
                () -> config.latherOverlayForagingOnly, v -> config.latherOverlayForagingOnly = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "  Hide When Nothing Lathered",
                () -> config.latherOverlayOnlyWhenActive, v -> config.latherOverlayOnlyWhenActive = v));
        foraging.add(new InfoEntry(contentX, "Position and scale in /tmap gui"));

        foraging.add(new LabelEntry(contentX, "Honeyhives"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Refill Reminder",
                () -> config.honeyhiveReminder, v -> config.honeyhiveReminder = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "  Remind Outside Torrhus Canyon",
                () -> config.honeyhiveRemindOutside, v -> config.honeyhiveRemindOutside = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Queen Bee Title",
                () -> config.honeyhiveQueenBeeTitle, v -> config.honeyhiveQueenBeeTitle = v));
        foraging.add(new LabelEntry(contentX, "Heart of the Forest"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Highlight Perks",
                () -> config.hotfHighlight, v -> config.hotfHighlight = v));
        foraging.add(new ColorEntry(contentX, contentWidth, "  Enabled Color",
                () -> config.colorHotfEnabled, v -> config.colorHotfEnabled = v));
        foraging.add(new ColorEntry(contentX, contentWidth, "  Disabled Color",
                () -> config.colorHotfDisabled, v -> config.colorHotfDisabled = v));
        foraging.add(new ColorEntry(contentX, contentWidth, "  Locked Color",
                () -> config.colorHotfLocked, v -> config.colorHotfLocked = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Level Stack",
                () -> config.hotfLevelStack, v -> config.hotfLevelStack = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Token Stack",
                () -> config.hotfTokenStack, v -> config.hotfTokenStack = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Rotating Perk Display",
                () -> config.hotfPerkDisplay, v -> config.hotfPerkDisplay = v));
        foraging.add(new ToggleEntry(contentX, contentWidth, "  Foraging Islands Only",
                () -> config.hotfPerkDisplayForagingOnly, v -> config.hotfPerkDisplayForagingOnly = v));
        foraging.add(new InfoEntry(contentX, "Position and scale in /tmap gui"));

        foraging.add(new LabelEntry(contentX, "Lasso"));
        foraging.add(new ToggleEntry(contentX, contentWidth, "Lasso Display",
                () -> config.lassoDisplay, v -> config.lassoDisplay = v));
        foraging.add(new InfoEntry(contentX, "Progress bar and REEL on the HUD, position in /tmap gui"));
        foraging.add(new SoundDropdownEntry(contentX, contentWidth, "  Reel Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.lassoReelSound, v -> config.lassoReelSound = v));
        foraging.add(new SliderEntry(contentX, contentWidth, "  Volume", 0f, 4f,
                () -> config.lassoReelVolume, v -> config.lassoReelVolume = v, 2));
        foraging.add(new SliderEntry(contentX, contentWidth, "  Pitch", 0.5f, 2f,
                () -> config.lassoReelPitch, v -> config.lassoReelPitch = v, 2));

        categories.put("Foraging", foraging);

        List<SettingsEntry> contests = new ArrayList<>();
        contests.add(new InfoEntry(contentX, "Foraging: :15 / :35 / :55, Farming: hourly at :15"));
        contests.add(new ToggleEntry(contentX, contentWidth, "Contest Timer HUD",
                () -> config.contestTimer, v -> config.contestTimer = v));
        contests.add(new ToggleEntry(contentX, contentWidth, "  Show Foraging",
                () -> config.contestShowForaging, v -> config.contestShowForaging = v));
        contests.add(new ToggleEntry(contentX, contentWidth, "  Show Farming",
                () -> config.contestShowFarming, v -> config.contestShowFarming = v));
        contests.add(new ToggleEntry(contentX, contentWidth, "  SkyBlock Only",
                () -> config.contestTimerSkyblockOnly, v -> config.contestTimerSkyblockOnly = v));
        contests.add(new ToggleEntry(contentX, contentWidth, "  Button In Inventory",
                () -> config.contestTimerButton, v -> config.contestTimerButton = v));
        contests.add(new InfoEntry(contentX, "Drag it in the inventory, or in /tmap gui"));
        contests.add(new LabelEntry(contentX, "Alerts"));
        contests.add(new ToggleEntry(contentX, contentWidth, "Start Alerts",
                () -> config.contestAlerts, v -> config.contestAlerts = v));
        contests.add(new SliderEntry(contentX, contentWidth, "  Warn Seconds Before", 0f, 300f,
                () -> (float) config.contestWarnBefore, v -> config.contestWarnBefore = Math.round(v), 0));
        contests.add(new ToggleEntry(contentX, contentWidth, "  Chat Message",
                () -> config.contestAlertChat, v -> config.contestAlertChat = v));
        contests.add(new ToggleEntry(contentX, contentWidth, "  Show Title",
                () -> config.contestAlertTitle, v -> config.contestAlertTitle = v));
        contests.add(new SoundDropdownEntry(contentX, contentWidth, "  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.contestAlertSound, v -> config.contestAlertSound = v));
        contests.add(new SliderEntry(contentX, contentWidth, "  Volume", 0f, 4f,
                () -> config.contestAlertVolume, v -> config.contestAlertVolume = v, 2));
        contests.add(new InfoEntry(contentX, "Alerts fire once before and once when a contest starts"));
        categories.put("Contest Timers", contests);

        List<SettingsEntry> torrhus = new ArrayList<>();
        torrhus.add(new LabelEntry(contentX, "Critter ESP"));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Critter ESP", () -> config.critterESP, v -> config.critterESP = v));
        torrhus.add(new InfoEntry(contentX, "Only active in Area: " + com.teslamaps.features.CritterESP.AREA));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Filled Boxes", () -> config.critterFilled, v -> config.critterFilled = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Tracers To All Critters", () -> config.critterTracers, v -> config.critterTracers = v));
        torrhus.add(new LabelEntry(contentX, "Tiki (3 stacked heads)"));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Tiki ESP", () -> config.tikiESP, v -> config.tikiESP = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Show Name", () -> config.tikiESPName, v -> config.tikiESPName = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "Tiki Box Color", () -> config.tikiESPColor, v -> config.tikiESPColor = v));
        torrhus.add(new LabelEntry(contentX, "Critters"));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Beeheemoth & Honeybuzz ESP", () -> config.critterBee, v -> config.critterBee = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterBee, v -> config.colorCritterBee = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Blue Jay ESP", () -> config.critterBlueJay, v -> config.critterBlueJay = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterBlueJay, v -> config.colorCritterBlueJay = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Bunbun ESP", () -> config.critterBunbun, v -> config.critterBunbun = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterBunbun, v -> config.colorCritterBunbun = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Drybark ESP", () -> config.critterDrybark, v -> config.critterDrybark = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterDrybark, v -> config.colorCritterDrybark = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Firefox ESP", () -> config.critterFirefox, v -> config.critterFirefox = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterFirefox, v -> config.colorCritterFirefox = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Grizzly Bear ESP", () -> config.critterGrizzly, v -> config.critterGrizzly = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterGrizzly, v -> config.colorCritterGrizzly = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Groundhog ESP", () -> config.critterGroundhog, v -> config.critterGroundhog = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterGroundhog, v -> config.colorCritterGroundhog = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Hideonsun ESP", () -> config.critterHideonsun, v -> config.critterHideonsun = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterHideonsun, v -> config.colorCritterHideonsun = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Mountain Goat ESP", () -> config.critterMountainGoat, v -> config.critterMountainGoat = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterMountainGoat, v -> config.colorCritterMountainGoat = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Sepialot ESP", () -> config.critterSepialot, v -> config.critterSepialot = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterSepialot, v -> config.colorCritterSepialot = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Dustybit ESP", () -> config.critterDustybit, v -> config.critterDustybit = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterDustybit, v -> config.colorCritterDustybit = v));
        torrhus.add(new ToggleEntry(contentX, contentWidth, "Puck ESP", () -> config.critterPuck, v -> config.critterPuck = v));
        torrhus.add(new ColorEntry(contentX, contentWidth, "  Color", () -> config.colorCritterPuck, v -> config.colorCritterPuck = v));
        categories.put("Torrhus Canyon", torrhus);

        List<SettingsEntry> safari = new ArrayList<>();
        safari.add(new LabelEntry(contentX, "Run Tracker"));
        safari.add(new ToggleEntry(contentX, contentWidth, "Track Run", () -> config.safariTracker, v -> config.safariTracker = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Show HUD", () -> config.safariHud, v -> config.safariHud = v));
        safari.add(new InfoEntry(contentX, "Position and scale in /tmap gui"));
        safari.add(new LabelEntry(contentX, "Checklist"));
        safari.add(new ToggleEntry(contentX, contentWidth, "Show Checklist", () -> config.safariChecklist, v -> config.safariChecklist = v));
        safari.add(new DropdownEntry(contentX, contentWidth, "Biome",
                new String[] { "Auto", "Cavern", "Forest", "Haunted", "Icy" },
                () -> config.safariChecklistBiome, v -> config.safariChecklistBiome = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Check/Cross Icons", () -> config.safariChecklistIcons, v -> config.safariChecklistIcons = v));
        safari.add(new InfoEntry(contentX, "Green = caught, red = escaped, grey = not seen"));
        safari.add(new LabelEntry(contentX, "Alerts"));
        safari.add(new ToggleEntry(contentX, contentWidth, "Sparkling Alert", () -> config.safariSparklingAlert, v -> config.safariSparklingAlert = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Sparkling to Party Chat", () -> config.safariSparklingParty, v -> config.safariSparklingParty = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Force Sparkle Particles", () -> config.safariSparkleForce, v -> config.safariSparkleForce = v));
        safari.add(new InfoEntry(contentX, "Sparkles render at any distance and particle setting"));
        safari.add(new ToggleEntry(contentX, contentWidth, "Announce Biome Done", () -> config.safariAnnounceBiomeDone, v -> config.safariAnnounceBiomeDone = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Announce Unique Done", () -> config.safariAnnounceUniqueDone, v -> config.safariAnnounceUniqueDone = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "  Share Time To Party", () -> config.safariShareTimeParty, v -> config.safariShareTimeParty = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Warn If Not Full", () -> config.safariWarnNotFull, v -> config.safariWarnNotFull = v));
        safari.add(new InfoEntry(contentX, "Screen warning + sound when fewer than 4 players are in"));
        safari.add(new SliderEntry(contentX, contentWidth, "Alert Volume", 0.0f, 3.0f,
                () -> config.safariAlertVolume, v -> config.safariAlertVolume = v));
        safari.add(new LabelEntry(contentX, "Quality of Life"));
        safari.add(new ToggleEntry(contentX, contentWidth, "Hide Cold Overlay (Icy)", () -> config.safariHideCold, v -> config.safariHideCold = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Auto Accept Hideyho", () -> config.safariAutoHideyho, v -> config.safariAutoHideyho = v));
        safari.add(new SliderEntry(contentX, contentWidth, "  Min Delay (ms)", 0f, 2000f,
                () -> (float) config.safariAutoHideyhoMinMs, v -> config.safariAutoHideyhoMinMs = (int) (float) v, 0));
        safari.add(new SliderEntry(contentX, contentWidth, "  Extra Random (ms)", 0f, 2000f,
                () -> (float) config.safariAutoHideyhoJitterMs, v -> config.safariAutoHideyhoJitterMs = (int) (float) v, 0));
        safari.add(new ToggleEntry(contentX, contentWidth, "Show PB in HUD", () -> config.safariShowPb, v -> config.safariShowPb = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Show Hotspot in HUD", () -> config.safariShowHotspot, v -> config.safariShowHotspot = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Show Head Start in HUD", () -> config.safariShowHeadStart, v -> config.safariShowHeadStart = v));
        safari.add(new InfoEntry(contentX, "Run history: /tmap safari"));
        safari.add(new LabelEntry(contentX, "ESP"));
        safari.add(new ToggleEntry(contentX, contentWidth, "Critter ESP (Safari)", () -> config.safariCritterESP, v -> config.safariCritterESP = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Filled Boxes", () -> config.safariFilled, v -> config.safariFilled = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Only Uncaught", () -> config.safariOnlyMissing, v -> config.safariOnlyMissing = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Name Over Every Critter", () -> config.safariNames, v -> config.safariNames = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Tracers To Named", () -> config.safariTracers, v -> config.safariTracers = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "  Tracer To Hideyho", () -> config.safariHideyhoTracer, v -> config.safariHideyhoTracer = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Bee Nest ESP", () -> config.safariNestESP, v -> config.safariNestESP = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Floor Spot ESP", () -> config.critterSpotESP, v -> config.critterSpotESP = v));
        safari.add(new ToggleEntry(contentX, contentWidth, "Recatch Spot", () -> config.safariRecatchSpot, v -> config.safariRecatchSpot = v));
        safari.add(new InfoEntry(contentX, "Marks where an escaped critter comes back"));
        safari.add(new ToggleEntry(contentX, contentWidth, "Hidden Critter Walls", () -> config.safariWallMarkers, v -> config.safariWallMarkers = v));
        safari.add(new InfoEntry(contentX, "Unbroken Snoozle / Troodon walls, while uncaught"));
        safari.add(new ColorEntry(contentX, contentWidth, "Sparkling Color", () -> config.colorSafariSparkling, v -> config.colorSafariSparkling = v));
        safari.add(new ColorEntry(contentX, contentWidth, "Bee Nest Color", () -> config.colorSafariNest, v -> config.colorSafariNest = v));
        safari.add(new ColorEntry(contentX, contentWidth, "Floor Spot Color", () -> config.colorCritterSpot, v -> config.colorCritterSpot = v));
        safari.add(new ColorEntry(contentX, contentWidth, "Recatch Spot Color", () -> config.colorSafariRecatch, v -> config.colorSafariRecatch = v));
        safari.add(new ColorEntry(contentX, contentWidth, "Wall Color", () -> config.colorSafariWall, v -> config.colorSafariWall = v));
        safari.add(new InfoEntry(contentX, "Hideyho and legendaries always get a name label"));
        safari.add(new InfoEntry(contentX, "Critter boxes are coloured by shard rarity"));
        categories.put("Critter Safari", safari);

        List<SettingsEntry> shards = new ArrayList<>();
        shards.add(new LabelEntry(contentX, "Hunting Box"));
        shards.add(new ToggleEntry(contentX, contentWidth, "Hunting Box Value", () -> config.huntingBoxValue, v -> config.huntingBoxValue = v));
        shards.add(new InfoEntry(contentX, "Bazaar value of every shard in the box, most valuable first"));
        shards.add(new LabelEntry(contentX, "Shard Overlay"));
        shards.add(new ToggleEntry(contentX, contentWidth, "Shard Overlay", () -> config.shardOverlay, v -> config.shardOverlay = v));
        shards.add(new InfoEntry(contentX, "Shows in /attributemenu and the bazaar shard pages"));
        shards.add(new DropdownEntry(contentX, contentWidth, "Sort By",
                com.teslamaps.features.hunting.ShardOverlay.SORTS,
                () -> config.shardSort, v -> config.shardSort = v));
        shards.add(new DropdownEntry(contentX, contentWidth, "Price Source",
                com.teslamaps.features.hunting.ShardOverlay.PRICE_SOURCES,
                () -> config.shardPriceSource, v -> config.shardPriceSource = v));
        shards.add(new ToggleEntry(contentX, contentWidth, "Hide Maxed", () -> config.shardHideMaxed, v -> config.shardHideMaxed = v));
        shards.add(new ToggleEntry(contentX, contentWidth, "Only Not Unlocked", () -> config.shardOnlyMissing, v -> config.shardOnlyMissing = v));
        shards.add(new ToggleEntry(contentX, contentWidth, "Count Hunting Box", () -> config.shardIncludeBox, v -> config.shardIncludeBox = v));
        shards.add(new ToggleEntry(contentX, contentWidth, "Count Bazaar Orders", () -> config.shardIncludeOrders, v -> config.shardIncludeOrders = v));
        shards.add(new InfoEntry(contentX, "Open your bazaar orders once so they are known"));
        shards.add(new ToggleEntry(contentX, contentWidth, "Tier As Stack Size", () -> config.shardTierStack, v -> config.shardTierStack = v));
        shards.add(new ToggleEntry(contentX, contentWidth, "Highlight Disabled Attributes", () -> config.shardHighlightDisabled, v -> config.shardHighlightDisabled = v));
        shards.add(new InfoEntry(contentX, "Click a row to look the shard up on the bazaar"));
        shards.add(new ButtonEntry(contentX, contentWidth, "Reset Hunting Box Data",
                com.teslamaps.features.hunting.ShardData::resetBox));
        categories.put("Shards", shards);

        List<SettingsEntry> profiles = new ArrayList<>();
        profiles.add(new InfoEntry(contentX, "Click a profile to switch - the current one is saved first"));
        profiles.add(new ProfileTilesEntry(contentX, contentWidth));
        profiles.add(new ButtonRowEntry(contentX, contentWidth,
                new String[]{"Save", "Duplicate", "Reset To Preset"},
                new Runnable[]{
                        com.teslamaps.config.ConfigProfiles::saveActive,
                        () -> {
                            com.teslamaps.config.ConfigProfiles.copyToNew(nextProfileName());
                            rebuildWidgets();
                        },
                        () -> {
                            com.teslamaps.config.ConfigProfiles.reset(com.teslamaps.config.ConfigProfiles.active());
                            rebuildWidgets();
                        }}));
        profiles.add(new InfoEntry(contentX, "PBs, keybinds and your API key are shared by every profile"));
        profiles.add(new InfoEntry(contentX, "Presets and the active profile have no x - switch away first"));
        // the same three pickers as the Interface category, kept narrow so the controls stay
        // next to their labels instead of at the far edge of a wide window
        int narrow = Math.min(contentWidth, 430);
        profiles.add(new InfoEntry(contentX, "Look & feel - full set under Interface"));
        profiles.add(new DropdownEntry(contentX, narrow, "GUI Style", GuiStyle.NAMES,
                () -> config.guiStyle, v -> config.guiStyle = v));
        profiles.add(new DropdownEntry(contentX, narrow, "HUD Style",
                com.teslamaps.render.gui.HudStyle.NAMES,
                () -> com.teslamaps.render.gui.HudStyle.current(), v -> config.hudStyle = v));
        profiles.add(new DropdownEntry(contentX, narrow, "GUI Sounds", GuiSounds.THEMES,
                () -> config.guiSoundTheme, v -> config.guiSoundTheme = v));
        profiles.add(new SliderEntry(contentX, narrow, "GUI Sound Volume", 0f, 2f,
                () -> config.guiSoundVolume, v -> config.guiSoundVolume = v, 2));
        // reaches every vanilla button in the game, so it belongs where profiles are picked
        profiles.add(new ToggleEntry(contentX, narrow, "Modern Vanilla Buttons",
                () -> config.modernVanillaButtons, v -> config.modernVanillaButtons = v));
        profiles.add(new InfoEntry(contentX, "Restyles the pause menu and every vanilla button"));
        categories.put("Profiles", profiles);

        List<SettingsEntry> ui = new ArrayList<>();
        ui.add(new LabelEntry(contentX, "Style"));
        ui.add(new DropdownEntry(contentX, contentWidth, "GUI Style", GuiStyle.NAMES,
                () -> config.guiStyle, v -> config.guiStyle = v));
        ui.add(new DropdownEntry(contentX, contentWidth, "HUD Style",
                com.teslamaps.render.gui.HudStyle.NAMES,
                () -> com.teslamaps.render.gui.HudStyle.current(), v -> config.hudStyle = v));
        ui.add(new InfoEntry(contentX, "GUI Style skins these screens, HUD Style the in-game panels"));
        ui.add(new LabelEntry(contentX, "Sounds"));
        ui.add(new DropdownEntry(contentX, contentWidth, "GUI Sounds", GuiSounds.THEMES,
                () -> config.guiSoundTheme, v -> config.guiSoundTheme = v));
        ui.add(new ToggleEntry(contentX, contentWidth, "Hover Ticks", () -> config.guiHoverSounds, v -> config.guiHoverSounds = v));
        ui.add(new SliderEntry(contentX, contentWidth, "Volume", 0f, 2f,
                () -> config.guiSoundVolume, v -> config.guiSoundVolume = v, 2));
        ui.add(new LabelEntry(contentX, "Config Hotkey"));
        ui.add(new KeybindEntry(contentX, contentWidth, "Open This Screen",
                () -> config.configScreenKey, v -> config.configScreenKey = v));
        ui.add(new ToggleEntry(contentX, contentWidth, "  Block Other Mods",
                () -> config.configScreenKeyExclusive, v -> config.configScreenKeyExclusive = v));
        ui.add(new InfoEntry(contentX, "Right Shift by default, press again to close"));
        ui.add(new InfoEntry(contentX, "Block Other Mods eats the key before anything else sees it"));
        ui.add(new LabelEntry(contentX, "Updates"));
        ui.add(new ToggleEntry(contentX, contentWidth, "Check For Updates", () -> config.updateCheck, v -> config.updateCheck = v));
        ui.add(new InfoEntry(contentX, "Signed releases only, install via popup or /tmap update"));
        categories.put("Interface", ui);

        addSectionMasters(contentX, contentWidth);

        sections.clear();
        sections.put("General", List.of("Profiles", "Interface", "Legit Mode"));
        sections.put("Dungeon", List.of("Map", "ESP", "Secrets & Waypoints", "Leap",
                "Puzzles & Terminals", "Score & Splits", "Timers", "Croesus"));
        sections.put("Bosses", List.of("Blood Camp", "F4 / M4", "F7 / M7"));
        sections.put("Visual", List.of("Render", "Hide", "Menu & Skybox"));
        sections.put("Items", List.of("Inventory", "Tooltips & Value"));
        sections.put("Chat & Media", List.of("Chat", "Sounds", "Screenshots"));
        sections.put("Party & Automation", List.of("Party", "Auto"));
        sections.put("Skyblock", List.of("Skills", "Combat", "Garden", "Mining", "Fishing", "Foraging",
                "Contest Timers", "Torrhus Canyon", "Critter Safari", "Shards", "Ordered Waypoints"));

        categoryHeaders.clear();
        for (String cat : categories.keySet()) categoryHeaders.put(cat, new LabelEntry(contentX, cat));

        // Mark cheat toggles so they grey out while Legit Mode blocks them.
        java.util.Set<String> cheatLabels = java.util.Set.of(
                "Show Glow", "Filled ESP (vs Outline)", "Box ESP (Hitbox)",
                "Starred Mob ESP", "Tracer When Few Left", "Fel ESP", "Sniper ESP", "Shadow Assassin ESP",
                "Dungeon Bat ESP", "Dungeon Bat Tracers", "Wither Key ESP", "Key Tracers",
                "Wither ESP", "Wither ESP Filled", "Wither ESP Depth Check", "Boss ESP", "Corpse ESP", "Type Colors",
                "Door ESP", "Door Tracers", "Only Show Next Door",
                "Livid Finder", "Livid Tracer", "Mimic Chest ESP", "Mimic Chest Tracers",
                "Highlight Teammates", "Invisible Armor Stand ESP", "Pest ESP", "Pest Tracers",
                "Dropped Item ESP", "Item Pickup Readiness",
                "Miniboss ESP", "Enable Auto GFS", "Auto Wish (Healer Ult)", "Critter ESP",
                "Critter ESP (Safari)", "Bee Nest ESP", "Exploding Frog ESP", "Hidden Critter Walls");
        for (List<SettingsEntry> entries : categories.values()) {
            for (SettingsEntry e : entries) {
                // trim: sub-options carry a leading indent that is not part of the name
                if (e instanceof ToggleEntry te && cheatLabels.contains(te.getLabel().trim())) te.legit();
            }
        }
    }

    private static void orderedFeedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + message));
    }

    /**
     * Magnifier glyph, 11x11. The lens is a real circle via the SDF outline shader - the old
     * version built it from axis-aligned fills, which at 5px reads as a square with a lump.
     */
    private void drawMagnifier(GuiGraphicsExtractor ctx, int x, int y, int argb) {
        com.teslamaps.render.gui.TeslaGui.roundedOutline(ctx, x, y, 8, 8, 4f, 1.2f, argb);
        // handle: stepped 2px blocks along the lens' lower-right diagonal
        for (int i = 0; i < 3; i++) {
            ctx.fill(x + 6 + i, y + 6 + i, x + 8 + i, y + 8 + i, argb);
        }
    }

    /**
     * Puts the master switch at the top of every gated category and greys out the toggles
     * below it while it is off - without this the sectionEnabled gates in the features had
     * no UI at all and every section(...) call was always true.
     */
    private void addSectionMasters(int contentX, int contentWidth) {
        for (String cat : TeslaMapsConfig.SECTIONS) {
            List<SettingsEntry> entries = categories.get(cat);
            if (entries == null) continue;
            for (SettingsEntry e : entries) {
                if (e instanceof ToggleEntry te) te.gatedBy(cat);
            }
            entries.add(0, new ToggleEntry(contentX, contentWidth, "Enable " + cat,
                    () -> TeslaMapsConfig.get().section(cat),
                    v -> TeslaMapsConfig.get().sectionEnabled.put(cat, v)));
        }
    }

    protected static final int CATEGORY_TEXT_X = 14;

    /**
     * Category name in the sidebar. The two longest ("Secrets &amp; Waypoints",
     * "Puzzles &amp; Terminals") are wider than the sidebar, so a label that
     * doesn't fit is scaled down instead of running under the scrollbar.
     */
    protected void drawCategoryLabel(GuiGraphicsExtractor context, String category, int y, int color) {
        drawSidebarText(context, category, CATEGORY_TEXT_X, y, color);
    }

    protected void drawSidebarText(GuiGraphicsExtractor context, String text, int x, int y, int color) {
        int available = SIDEBAR_WIDTH - x - 8;
        int width = font.width(text);
        if (width <= available) {
            context.text(font, text, x, y, color);
            return;
        }

        float scale = available / (float) width;
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y + (font.lineHeight * (1 - scale)) / 2f);
        pose.scale(scale, scale);
        context.text(font, text, 0, 0, color);
        pose.popMatrix();
    }

    /** Scrolls the sidebar so a category is on screen — used when reopening on a remembered one. */
    protected void scrollSidebarTo(String category) {
        int rowY = SIDEBAR_TOP;
        int contentH = 0;
        int hitY = -1;
        for (Map.Entry<String, List<String>> sec : sections.entrySet()) {
            rowY += SECTION_HEADER_H;
            for (String cat : sec.getValue()) {
                if (cat.equals(category)) hitY = rowY;
                rowY += CATEGORY_ROW_H;
            }
            rowY += SECTION_GAP;
        }
        contentH = rowY - SIDEBAR_TOP;
        if (hitY < 0) return;

        int visible = sidebarBottom() - SIDEBAR_TOP;
        int max = Math.max(0, contentH - visible);
        int wanted = hitY - SIDEBAR_TOP - visible / 2 + CATEGORY_ROW_H / 2;
        sidebarScroll = Math.max(0, Math.min(max, wanted));
    }

    /** Puts the thumb center under the cursor, so grabbing it does not jump. */
    protected void dragScrollbar(double my) {
        int contentTop = 50, trackH = this.height - 40 - contentTop;
        int thumbH = Math.max(20, (int) ((float) trackH * trackH / (maxScroll + trackH)));
        int travel = trackH - thumbH;
        if (travel <= 0) return;
        double t = (my - contentTop - thumbH / 2.0) / travel;
        scrollOffset = (int) Math.round(Math.max(0, Math.min(1, t)) * maxScroll);
    }

    protected void dragSidebarScrollbar(double my) {
        int trackH = sidebarBottom() - SIDEBAR_TOP;
        int thumbH = Math.max(20, (int) ((float) trackH * trackH / (sidebarMaxScroll + trackH)));
        int travel = trackH - thumbH;
        if (travel <= 0) return;
        double t = (my - SIDEBAR_TOP - thumbH / 2.0) / travel;
        sidebarScroll = (int) Math.round(Math.max(0, Math.min(1, t)) * sidebarMaxScroll);
    }

    protected String categoryAtY(int my) {
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

    protected List<SettingsEntry> getEntriesToShow() {
        List<SettingsEntry> result = new ArrayList<>();
        if (!searchQuery.isEmpty()) {
            for (Map.Entry<String, List<SettingsEntry>> cat : categories.entrySet()) {
                int before = result.size();
                boolean carry = false;               // a matched parent drags its indented sub-options along
                for (SettingsEntry entry : cat.getValue()) {
                    if (entry.isLabel()) { carry = false; continue; } // the category header replaces group labels here
                    boolean child = entry.getLabel().startsWith(" ");
                    if (entry.matchesSearch(searchQuery)) {
                        result.add(entry);
                        carry = !child;
                    } else if (child && carry) {
                        result.add(entry);
                    } else if (!child) {
                        carry = false;
                    }
                }
                LabelEntry header = categoryHeaders.get(cat.getKey());
                if (result.size() > before && header != null) {
                    header.collapseKey = null;       // headers never fold in search mode
                    result.add(before, header);
                }
            }
        } else {
            List<SettingsEntry> categoryEntries = categories.get(selectedCategory);
            if (categoryEntries == null) return result;
            boolean hidden = false;
            int ord = 0;
            LabelEntry group = null;
            for (SettingsEntry e : categoryEntries) {
                if (e instanceof LabelEntry le) {
                    le.collapseKey = selectedCategory + "#" + (ord++);
                    le.childCount = 0;
                    group = le;
                    hidden = GROUP_COLLAPSED.contains(le.collapseKey);
                    result.add(e);
                } else {
                    if (group != null) group.childCount++;
                    if (!hidden) result.add(e);
                }
            }
        }
        return result;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown; // Calculate BEFORE updating wasMouseDown
        boolean isRightDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean rightClicked = isRightDown && !wasRightDown;
        wasRightDown = isRightDown; // early, the block below has several return paths

        // scrollbar drag — thumbs are 4/2 px wide but get a wider grab zone, otherwise unhittable.
        // mouseDragged does not reach us reliably, so a held thumb is followed per frame instead.
        if (!isMouseDown) { draggingScrollbar = false; draggingSidebarScrollbar = false; }
        if (draggingScrollbar) {
            dragScrollbar(mouseY);
        } else if (draggingSidebarScrollbar) {
            dragSidebarScrollbar(mouseY);
        } else if (clicked) {
            if (maxScroll > 0 && mouseX >= this.width - 12 && mouseY >= 50 && mouseY < this.height - 40) {
                draggingScrollbar = true;
                dragScrollbar(mouseY);
            } else if (sidebarMaxScroll > 0 && mouseX >= SIDEBAR_WIDTH - 7 && mouseX < SIDEBAR_WIDTH
                    && mouseY >= SIDEBAR_TOP && mouseY < sidebarBottom()) {
                draggingSidebarScrollbar = true;
                dragSidebarScrollbar(mouseY);
            }
        }
        if (draggingScrollbar || draggingSidebarScrollbar) {
            clicked = false;
            isMouseDown = false; // sliders must not react while the thumb is held
        }

        if (clicked && mouseX < SIDEBAR_WIDTH && mouseY >= SIDEBAR_TOP && mouseY < sidebarBottom()) {
            String hit = categoryAtY(mouseY);
            if (hit != null) {
                if (!hit.equals(selectedCategory)) GuiSounds.select();
                selectedCategory = hit;
                TeslaMapsConfig.get().lastConfigCategory = hit;
                TeslaMapsConfig.save();
                searchQuery = "";
                searchField.setValue(""); // responder clears lastSearch too
                scrollOffset = 0;
                expandedColorEntry = null;
            }
        }

        int shortcutBtnY = this.height - 89;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= shortcutBtnY && mouseY < shortcutBtnY + 22) {
            GuiSounds.press();
            minecraft.setScreen(new ShortcutScreen());
            wasMouseDown = isMouseDown;
            return;
        }

        int msgBtnY = this.height - 62;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= msgBtnY && mouseY < msgBtnY + 22) {
            GuiSounds.press();
            minecraft.setScreen(new KeybindMessageScreen());
            wasMouseDown = isMouseDown;
            return;
        }

        int tmapBtnY = this.height - 35;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= tmapBtnY && mouseY < tmapBtnY + 22) {
            GuiSounds.press();
            if (minecraft.player != null) {
                minecraft.player.connection.sendCommand("tmap gui");
            }
        }

        int doneX = this.width - 70;
        int doneY = this.height - 30;
        if (clicked && mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 22) {
            GuiSounds.press();
            onClose();
            wasMouseDown = isMouseDown;
            return;
        }

        context.fill(0, 0, this.width, this.height, GuiStyle.background());

        int sfX = SIDEBAR_WIDTH + 14, sfY = 8, sfW = this.width - SIDEBAR_WIDTH - 28, sfH = 22;
        boolean overSearch = mouseX >= sfX && mouseX < sfX + sfW && mouseY >= sfY && mouseY < sfY + sfH;
        if (searchField != null && overSearch) {
            if (rightClicked) {
                if (!searchField.getValue().isEmpty()) GuiSounds.press();
                searchField.setValue(""); // responder clears searchQuery/lastSearch
                scrollOffset = 0;
                expandedColorEntry = null;
            } else if (clicked) {
                searchField.setFocused(true); // the field is only 8px tall, the bar is 22
            }
        }
        boolean sfFocus = searchField != null && searchField.isFocused();
        if (GuiStyle.isGlass()) GuiStyle.panel(context, sfX, sfY, sfW, sfH, sfFocus);
        else context.fill(sfX, sfY, sfX + sfW, sfY + sfH, GuiStyle.input());
        drawBorder(context, sfX, sfY, sfW, sfH, sfFocus ? GuiStyle.accent() : GuiStyle.cardHover());
        drawMagnifier(context, sfX + 6, sfY + (sfH - 11) / 2, GuiStyle.textDim());

        GuiStyle.sidebarPanel(context, 0, 0, SIDEBAR_WIDTH, this.height);
        context.text(font, "TeslaMaps", 10, 8, 0xFFFFFFFF);
        context.text(font, "Categories", 10, 22, GuiStyle.accent());

        int sbTop = SIDEBAR_TOP, sbBottom = sidebarBottom();
        context.enableScissor(0, sbTop, SIDEBAR_WIDTH, sbBottom);
        int rowY = sbTop - sidebarScroll;
        for (Map.Entry<String, List<String>> sec : sections.entrySet()) {
            if (rowY + SECTION_HEADER_H > sbTop && rowY < sbBottom) {
                drawSidebarText(context, sec.getKey().toUpperCase(), 10, rowY + 8, GuiStyle.textFaint());
            }
            rowY += SECTION_HEADER_H;
            for (String category : sec.getValue()) {
                boolean selected = category.equals(selectedCategory) && searchQuery.isEmpty();
                boolean hovered = mouseX < SIDEBAR_WIDTH && mouseY >= rowY && mouseY < rowY + CATEGORY_ROW_H
                        && mouseY >= sbTop && mouseY < sbBottom;
                if (hovered) GuiSounds.hover(category);
                if (rowY + CATEGORY_ROW_H > sbTop && rowY < sbBottom) {
                    if (selected) context.fill(5, rowY, SIDEBAR_WIDTH - 5, rowY + CATEGORY_ROW_H, GuiStyle.rowSelected());
                    else if (hovered) context.fill(5, rowY, SIDEBAR_WIDTH - 5, rowY + CATEGORY_ROW_H, GuiStyle.rowHover());
                    int textColor = selected ? GuiStyle.accent() : (hovered ? 0xFFFFFFFF : GuiStyle.textDim());
                    drawCategoryLabel(context, category, rowY + 8, textColor);
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
            context.fill(SIDEBAR_WIDTH - 3, thumbY, SIDEBAR_WIDTH - 1, thumbY + thumbH, GuiStyle.scrollThumb());
        }

        boolean shortcutHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= shortcutBtnY && mouseY < shortcutBtnY + 22;
        if (shortcutHovered) GuiSounds.hover("btn:shortcut");
        GuiStyle.panel(context, 8, shortcutBtnY, SIDEBAR_WIDTH - 16, 22, shortcutHovered);
        drawBorder(context, 8, shortcutBtnY, SIDEBAR_WIDTH - 16, 22, shortcutHovered ? GuiStyle.accent() : GuiStyle.border());
        context.centeredText(font, "/tmap shortcut", SIDEBAR_WIDTH / 2, shortcutBtnY + 7, GuiStyle.textDim());

        boolean msgHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= msgBtnY && mouseY < msgBtnY + 22;
        if (msgHovered) GuiSounds.hover("btn:hotkeys");
        GuiStyle.panel(context, 8, msgBtnY, SIDEBAR_WIDTH - 16, 22, msgHovered);
        drawBorder(context, 8, msgBtnY, SIDEBAR_WIDTH - 16, 22, msgHovered ? GuiStyle.accent() : GuiStyle.border());
        context.centeredText(font, "/tmap hotkeys", SIDEBAR_WIDTH / 2, msgBtnY + 7, GuiStyle.textDim());

        boolean tmapHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= tmapBtnY && mouseY < tmapBtnY + 22;
        if (tmapHovered) GuiSounds.hover("btn:gui");
        GuiStyle.panel(context, 8, tmapBtnY, SIDEBAR_WIDTH - 16, 22, tmapHovered);
        drawBorder(context, 8, tmapBtnY, SIDEBAR_WIDTH - 16, 22, tmapHovered ? GuiStyle.accent() : GuiStyle.border());
        context.centeredText(font, "/tmap gui", SIDEBAR_WIDTH / 2, tmapBtnY + 7, GuiStyle.textDim());

        int contentTop = 50;
        int contentBottom = this.height - 40;
        int contentLeft = SIDEBAR_WIDTH;
        int contentRight = this.width - 10; // Leave space for scrollbar

        String header = searchQuery.isEmpty() ? selectedCategory : "Search Results";
        context.fill(SIDEBAR_WIDTH, 30, this.width, contentTop, GuiStyle.background()); // Header background
        context.text(font, header, SIDEBAR_WIDTH + 20, 35, 0xFFFFFFFF);

        List<SettingsEntry> entries = getEntriesToShow();
        int totalHeight = 0;
        for (SettingsEntry e : entries) totalHeight += e.getHeight();
        int visibleHeight = contentBottom - contentTop;
        maxScroll = Math.max(0, totalHeight - visibleHeight);

        context.enableScissor(contentLeft, contentTop, contentRight, contentBottom);

        // One click belongs to one row. Picking a dropdown option collapses it mid-pass, which
        // pulls the rows below up under the cursor — without this they would take the click too.
        clickHandled = false;
        int y = contentTop - scrollOffset;
        for (SettingsEntry entry : entries) {
            if (y + entry.getHeight() > contentTop - 50 && y < contentBottom + 50) {
                entry.render(context, this, y, mouseX, mouseY, clicked && !clickHandled, isMouseDown);
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

            context.fill(scrollbarX, contentTop, scrollbarX + scrollbarW, contentBottom, GuiStyle.scrollTrack());
            context.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, GuiStyle.scrollThumb());
        }

        context.fill(SIDEBAR_WIDTH, this.height - 40, this.width, this.height, GuiStyle.background());

        boolean doneHovered = mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 22;
        if (doneHovered) GuiSounds.hover("btn:done");
        GuiStyle.panel(context, doneX, doneY, 60, 22, doneHovered);
        drawBorder(context, doneX, doneY, 60, 22, doneHovered ? GuiStyle.accent() : GuiStyle.border());
        context.centeredText(font, "Done", doneX + 30, doneY + 7, 0xFFFFFFFF);

        GuiSounds.endFrame();
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

    protected void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        GuiSounds.close();
        GuiSounds.resetHover();
        super.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (GuiStyle.blurBackground()) extractBlurredBackground(context);
    }

    protected interface SettingsEntry {
        int getHeight();
        void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown);
        default boolean matchesSearch(String q) { return false; }
        default String getLabel() { return ""; }
        default boolean scrollAt(int mouseX, int mouseY, int rowY, double dir) { return false; }
        // Card layout (MapConfigScreen2) repositions entries into their card before rendering.
        default void reposition(int x, int w) {}
        default boolean isLabel() { return false; }
        default boolean isToggle() { return false; }
    }

    // Plain descriptive text. Never a collapsible group header.
    protected class InfoEntry implements SettingsEntry {
        private int x;
        private final String text;

        InfoEntry(int x, String text) { this.x = x; this.text = text; }

        @Override public int getHeight() { return 14; }
        @Override public String getLabel() { return text; }
        @Override public void reposition(int x, int w) { this.x = x; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            ctx.text(screen.font, text, x + 10, y + 3, GuiStyle.textDim());
        }
    }

    protected class LabelEntry implements SettingsEntry {
        private int x;
        private final String text;
        String collapseKey = null;
        int childCount = 0;

        LabelEntry(int x, String text) { this.x = x; this.text = text; }

        @Override public int getHeight() { return 24; }
        @Override public String getLabel() { return text; }
        @Override public boolean isLabel() { return true; }
        @Override public void reposition(int x, int w) { this.x = x; this.collapseKey = null; }

        private void caret(GuiGraphicsExtractor ctx, int cx, int cy, boolean open, int color) {
            if (open) {
                for (int i = 0; i < 4; i++) ctx.fill(cx + i, cy + i, cx + 7 - i, cy + i + 1, color);
            } else {
                for (int i = 0; i < 4; i++) ctx.fill(cx + i, cy + i, cx + i + 1, cy + 7 - i, color);
            }
        }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            String t = text.trim();
            if (collapseKey == null) {
                ctx.text(screen.font, t, x, y + 9, GuiStyle.accent());
                return;
            }
            boolean collapsed = GROUP_COLLAPSED.contains(collapseKey);
            int rowX = x - 10, rowRight = screen.width - 18;
            boolean hovered = mouseX >= rowX && mouseX < rowRight && mouseY >= y && mouseY < y + getHeight();
            if (hovered && clicked) {
                screen.clickHandled = true;
                if (!GROUP_COLLAPSED.remove(collapseKey)) GROUP_COLLAPSED.add(collapseKey);
                collapsed = !collapsed;
                GuiSounds.fold(!collapsed);
                screen.scrollOffset = Math.min(screen.scrollOffset, screen.maxScroll);
            }
            if (hovered) {
                GuiSounds.hover(this);
                TeslaGui.roundedRect(ctx, rowX, y + 1, rowRight - rowX, getHeight() - 3, 5f,
                        TeslaGui.withAlpha(0xFFFFFF, 16));
            }
            int textY = y + 8;
            caret(ctx, x - 2, textY + (collapsed ? 0 : 2), !collapsed,
                    hovered ? 0xFFFFFFFF : GuiStyle.textDim());
            ctx.text(screen.font, t, x + 10, textY, hovered ? 0xFFFFFFFF : GuiStyle.accent());

            int tail = rowRight - 6;
            if (collapsed && childCount > 0) {
                String n = String.valueOf(childCount);
                int nx = tail - screen.font.width(n);
                ctx.text(screen.font, n, nx, textY, GuiStyle.textFaint());
                tail = nx - 8;
            }
            int lineX = x + 10 + screen.font.width(t) + 8;
            if (lineX < tail) {
                ctx.fill(lineX, textY + 3, tail, textY + 4, TeslaGui.withAlpha(0xFFFFFF, hovered ? 34 : 18));
            }
        }
    }

    protected class ToggleEntry implements SettingsEntry {
        private int x, width;
        private final String label;
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;
        private boolean legitGated = false;
        private String sectionGate = null;

        ToggleEntry(int x, int w, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.x = x; this.width = w; this.label = label; this.getter = getter; this.setter = setter;
        }

        /** Marks this toggle as disabled by Legit Mode (greyed out while it blocks cheats). */
        ToggleEntry legit() { this.legitGated = true; return this; }

        /** Greys the toggle out while its section master is off, since it does nothing then. */
        ToggleEntry gatedBy(String section) { this.sectionGate = section; return this; }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }
        @Override public boolean isToggle() { return true; }
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }
        boolean get() { return getter.get(); }
        void toggle() { setter.accept(!getter.get()); TeslaMapsConfig.save(); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                GuiSounds.hover(this);
                ctx.fill(x, y, x + width, y + ROW_HEIGHT, GuiStyle.rowHover());
            }

            int toggleX = x + width - 44;
            int toggleY = y + 5;
            boolean legitOff = legitGated && com.teslamaps.features.LegitMode.blocksCheats();
            boolean sectionOff = sectionGate != null && !TeslaMapsConfig.get().section(sectionGate);
            boolean gatedOff = legitOff || sectionOff;
            boolean value = getter.get();

            if (gatedOff) {
                ctx.text(screen.font, screen.fitLabel(label, x + 8, toggleX), x + 8, y + 9, 0xFF666666);
                ctx.fill(toggleX, toggleY, toggleX + 36, toggleY + 16, 0xFF2A2A2A);
                drawBorder(ctx, toggleX, toggleY, 36, 16, 0xFF444444);
                ctx.fill(toggleX + 2, toggleY + 2, toggleX + 16, toggleY + 14, 0xFF555555);
                if (hovered) {
                    String tip = legitOff ? "Disabled by Legit Mode" : "\"" + sectionGate + "\" is switched off";
                    int tw = screen.font.width(tip);
                    int tx = Math.max(2, mouseX - tw - 6), ty = y - 2;
                    ctx.fill(tx - 3, ty - 2, tx + tw + 3, ty + 11, 0xEE000000);
                    ctx.text(screen.font, tip, tx, ty, 0xFFFFAA55);
                }
                return;
            }

            ctx.text(screen.font, screen.fitLabel(label, x + 8, toggleX), x + 8, y + 9, GuiStyle.text());

            if (clicked && hovered) {
                screen.clickHandled = true;
                setter.accept(!value);
                TeslaMapsConfig.save();
                value = !value;
                GuiSounds.toggle(value);
            }

            int trackColor = value ? GuiStyle.accent() : GuiStyle.toggleOff();
            ctx.fill(toggleX, toggleY, toggleX + 36, toggleY + 16, trackColor);
            drawBorder(ctx, toggleX, toggleY, 36, 16, GuiStyle.border());

            int knobX = value ? toggleX + 20 : toggleX + 2;
            ctx.fill(knobX, toggleY + 2, knobX + 14, toggleY + 14, GuiStyle.knob());
        }
    }

    protected class ButtonEntry implements SettingsEntry {
        private int x, width;
        private final String label;
        private final Runnable action;
        private boolean active = false;   // draws a check and a green border, for the picked profile

        ButtonEntry(int x, int w, String label, Runnable action) {
            this.x = x; this.width = w; this.label = label; this.action = action;
        }

        ButtonEntry active() { this.active = true; return this; }

        @Override public int getHeight() { return ROW_HEIGHT + 4; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            int btnX = x + 8;
            int btnY = y + 2;
            int btnW = width - 16;
            int btnH = 22;

            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            if (hovered) GuiSounds.hover(this);
            if (clicked && hovered) { screen.clickHandled = true; GuiSounds.press(); action.run(); }

            GuiStyle.panel(ctx, btnX, btnY, btnW, btnH, hovered);
            drawBorder(ctx, btnX, btnY, btnW, btnH,
                    active ? GuiStyle.accent() : hovered ? GuiStyle.borderHover() : GuiStyle.border());
            ctx.centeredText(screen.font, label, btnX + btnW / 2, btnY + 7,
                    active ? GuiStyle.accent() : GuiStyle.text());
            if (active) {
                int textW = screen.font.width(label);
                TeslaGui.checkIcon(ctx, btnX + btnW / 2f - textW / 2f - 14, btnY + 6, 10, GuiStyle.accent());
            }
        }
    }

    /** Several small buttons on one row instead of a full-width one per action. */
    protected class ButtonRowEntry implements SettingsEntry {
        private int x, width;
        private final String[] labels;
        private final Runnable[] actions;
        private final Object[] hoverKeys;

        ButtonRowEntry(int x, int w, String[] labels, Runnable[] actions) {
            this.x = x; this.width = w; this.labels = labels; this.actions = actions;
            this.hoverKeys = new Object[labels.length];
            for (int i = 0; i < labels.length; i++) hoverKeys[i] = new Object();
        }

        @Override public int getHeight() { return 28; }
        @Override public String getLabel() { return String.join(" ", labels); }
        @Override public boolean matchesSearch(String q) { return getLabel().toLowerCase().contains(q); }
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            int gap = 6;
            int total = width - 16;
            // capped so a wide window gets compact buttons instead of stretched bars
            int btnW = Math.min(150, (total - gap * (labels.length - 1)) / labels.length);
            int btnY = y + 3, btnH = 20;

            for (int i = 0; i < labels.length; i++) {
                int btnX = x + 8 + i * (btnW + gap);
                boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
                if (hovered) GuiSounds.hover(hoverKeys[i]);
                if (clicked && hovered && !screen.clickHandled) {
                    screen.clickHandled = true;
                    GuiSounds.press();
                    actions[i].run();
                    return;
                }
                GuiStyle.panel(ctx, btnX, btnY, btnW, btnH, hovered);
                drawBorder(ctx, btnX, btnY, btnW, btnH, hovered ? GuiStyle.borderHover() : GuiStyle.border());
                ctx.centeredText(screen.font, screen.fitLabel(labels[i], btnX + 4, btnX + btnW - 4),
                        btnX + btnW / 2, btnY + 6, GuiStyle.text());
            }
        }
    }

    /** Profile picker: compact tiles in a grid, each with its own delete affordance. */
    protected class ProfileTilesEntry implements SettingsEntry {
        private static final int TILE_H = 24;
        private static final int TILE_MAX_W = 170;
        private static final int GAP = 6;

        private int x, width;
        private final List<String> names;
        private final Object[] hoverKeys;

        ProfileTilesEntry(int x, int w) {
            this.x = x; this.width = w;
            this.names = new ArrayList<>(com.teslamaps.config.ConfigProfiles.list());
            this.hoverKeys = new Object[names.size()];
            for (int i = 0; i < names.size(); i++) hoverKeys[i] = new Object();
        }

        private int columns() {
            int avail = width - 16;
            return Math.max(2, Math.min(5, avail / (TILE_MAX_W + GAP)));
        }

        private int tileWidth() {
            int cols = columns();
            return Math.min(TILE_MAX_W, (width - 16 - GAP * (cols - 1)) / cols);
        }

        @Override public int getHeight() {
            int rows = (names.size() + columns() - 1) / columns();
            return Math.max(1, rows) * (TILE_H + GAP) + 2;
        }

        @Override public String getLabel() { return String.join(" ", names); }
        @Override public boolean matchesSearch(String q) { return "profile".contains(q) || getLabel().toLowerCase().contains(q); }
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            String active = com.teslamaps.config.ConfigProfiles.active();
            int cols = columns(), tileW = tileWidth();

            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                int tileX = x + 8 + (i % cols) * (tileW + GAP);
                int tileY = y + (i / cols) * (TILE_H + GAP);
                boolean isActive = name.equals(active);
                boolean deletable = !isActive && !com.teslamaps.config.ConfigProfiles.isPreset(name);
                boolean confirming = name.equals(screen.pendingDeleteProfile);

                boolean hovered = mouseX >= tileX && mouseX < tileX + tileW && mouseY >= tileY && mouseY < tileY + TILE_H;
                int delX = tileX + tileW - 15;
                boolean onDelete = deletable && hovered && mouseX >= delX - 2;

                if (hovered) GuiSounds.hover(hoverKeys[i]);
                if (clicked && hovered && !screen.clickHandled) {
                    screen.clickHandled = true;
                    if (onDelete) {
                        if (confirming) {
                            GuiSounds.press();
                            com.teslamaps.config.ConfigProfiles.delete(name);
                            screen.pendingDeleteProfile = null;
                        } else {
                            GuiSounds.fold(true);
                            screen.pendingDeleteProfile = name;
                        }
                        rebuildWidgets();
                        return;
                    }
                    screen.pendingDeleteProfile = null;
                    if (!isActive) {
                        GuiSounds.select();
                        com.teslamaps.config.ConfigProfiles.switchTo(name);
                        rebuildWidgets();
                        return;
                    }
                }

                GuiStyle.panel(ctx, tileX, tileY, tileW, TILE_H, hovered);
                drawBorder(ctx, tileX, tileY, tileW, TILE_H,
                        confirming ? AppleColors.ACCENT_RED
                                : isActive ? GuiStyle.accent()
                                : hovered ? GuiStyle.borderHover() : GuiStyle.border());

                int labelRight = deletable ? delX - 4 : tileX + tileW - 6;
                int labelLeft = tileX + (isActive ? 20 : 8);
                ctx.text(screen.font, screen.fitLabel(confirming ? "delete?" : name, labelLeft, labelRight),
                        labelLeft, tileY + 8,
                        confirming ? AppleColors.ACCENT_RED : isActive ? GuiStyle.accent() : GuiStyle.text());
                if (isActive) TeslaGui.checkIcon(ctx, tileX + 7, tileY + 7, 9, GuiStyle.accent());

                if (deletable) {
                    ctx.text(screen.font, "x", delX + 2, tileY + 8,
                            onDelete || confirming ? AppleColors.ACCENT_RED : GuiStyle.textFaint());
                }
            }
        }
    }

    protected class ClassFilterEntry implements SettingsEntry {
        private int x, width;
        private final String label;
        private final TeslaMapsConfig.ClassFilter filter;

        ClassFilterEntry(int x, int w, String label, TeslaMapsConfig.ClassFilter filter) {
            this.x = x; this.width = w; this.label = label; this.filter = filter;
        }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q) || "class".contains(q); }
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                GuiSounds.hover(this);
                ctx.fill(x, y, x + width, y + ROW_HEIGHT, GuiStyle.rowHover());
            }

            int btnW = controlWidth(150, width), btnX = x + width - btnW - 4, btnY = y + 3, btnH = 20;
            ctx.text(screen.font, screen.fitLabel(label, x + 8, btnX), x + 8, y + 9, GuiStyle.text());

            boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            if (clicked && btnHover) {
                screen.clickHandled = true;
                GuiSounds.press();
                screen.minecraft.setScreen(new ClassFilterScreen(screen, label, filter));
            }
            GuiStyle.panel(ctx, btnX, btnY, btnW, btnH, btnHover);
            drawBorder(ctx, btnX, btnY, btnW, btnH, GuiStyle.border());
            ctx.centeredText(screen.font, filter.summary(), btnX + btnW / 2, btnY + 6, 0xFFE0E0E0);
        }
    }

    protected class KeybindEntry implements SettingsEntry {
        private int x, width;
        private final String label;
        private final IntSupplier getter;
        private final IntConsumer setter;

        KeybindEntry(int x, int w, String label, IntSupplier getter, IntConsumer setter) {
            this.x = x; this.width = w; this.label = label; this.getter = getter; this.setter = setter;
        }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        void setKey(int key) { setter.accept(key); TeslaMapsConfig.save(); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                GuiSounds.hover(this);
                ctx.fill(x, y, x + width, y + ROW_HEIGHT, GuiStyle.rowHover());
            }

            int btnW = controlWidth(96, width), btnX = x + width - btnW - 4, btnY = y + 3, btnH = 20;
            ctx.text(screen.font, screen.fitLabel(label, x + 8, btnX), x + 8, y + 9, GuiStyle.text());

            boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            if (clicked && btnHover) {
                screen.clickHandled = true;
                GuiSounds.press();
                screen.listeningKeybindEntry = this;
                screen.keybindListenStart = System.currentTimeMillis();
                if (screen.searchField != null) screen.searchField.setFocused(false); // don't type the key into the search bar
            }

            boolean listening = screen.listeningKeybindEntry == this;
            String txt = listening ? "press a key..." : keyName(getter.getAsInt());
            GuiStyle.panel(ctx, btnX, btnY, btnW, btnH, btnHover || listening);
            drawBorder(ctx, btnX, btnY, btnW, btnH, listening ? GuiStyle.accent() : GuiStyle.border());
            ctx.centeredText(screen.font, txt, btnX + btnW / 2, btnY + 6, 0xFFE0E0E0);
        }
    }

    protected class ColorEntry implements SettingsEntry {
        private int x, width;
        private final String label;
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        ColorEntry(int x, int w, String label, Supplier<String> getter, Consumer<String> setter) {
            this.x = x; this.width = w; this.label = label; this.getter = getter; this.setter = setter;
        }

        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        @Override
        public int getHeight() {
            return expandedColorEntry == this ? ROW_HEIGHT + COLOR_PICKER_HEIGHT : ROW_HEIGHT;
        }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean isExpanded = expandedColorEntry == this;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                GuiSounds.hover(this);
                ctx.fill(x, y, x + width, y + ROW_HEIGHT, GuiStyle.rowHover());
            }

            int swatchX = x + width - 30;
            int swatchY = y + 5;
            int color = TeslaMapsConfig.parseColor(getter.get());

            String hexLabel = "#" + getter.get().substring(0, Math.min(6, getter.get().length()));
            int hexX = swatchX - screen.font.width(hexLabel) - 6;
            ctx.text(screen.font, screen.fitLabel(label, x + 8, hexX), x + 8, y + 9, GuiStyle.text());

            if (clicked && hovered) {
                screen.clickHandled = true;
                GuiSounds.fold(!isExpanded);
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
            drawBorder(ctx, swatchX, swatchY, 22, 16, isExpanded ? GuiStyle.accent() : 0xFF555555);

            ctx.text(screen.font, hexLabel, hexX, y + 9, GuiStyle.textDim());

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

    protected class SliderEntry implements SettingsEntry {
        private int x, width;
        private final String label;
        private final float min, max;
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final int decimals;
        private int lastNotch = Integer.MIN_VALUE;

        SliderEntry(int x, int w, String label, float min, float max, Supplier<Float> getter, Consumer<Float> setter) {
            this(x, w, label, min, max, getter, setter, 1);
        }

        SliderEntry(int x, int w, String label, float min, float max, Supplier<Float> getter, Consumer<Float> setter, int decimals) {
            this.x = x; this.width = w; this.label = label;
            this.min = min; this.max = max; this.getter = getter; this.setter = setter; this.decimals = decimals;
        }

        private int sliderWidth() { return controlWidth(80, width); }
        private int sliderLeft() { return x + width - sliderWidth() - 48; }

        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        @Override public boolean scrollAt(int mouseX, int mouseY, int rowY, double dir) {
            int sliderX = sliderLeft();
            if (mouseX < sliderX - 4 || mouseX > x + width) return false; // only over the track/value
            float step = (float) Math.pow(10, -decimals);
            float v = Math.max(min, Math.min(max, getter.get() + (float) (dir > 0 ? step : -step)));
            setter.accept(v);
            TeslaMapsConfig.save();
            GuiSounds.step((v - min) / (max - min));
            return true;
        }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                GuiSounds.hover(this);
                ctx.fill(x, y, x + width, y + ROW_HEIGHT, GuiStyle.rowHover());
            }

            int sliderX = sliderLeft();
            int sliderY = y + 10;
            int sliderW = sliderWidth();
            int sliderH = 6;

            ctx.text(screen.font, screen.fitLabel(label, x + 8, sliderX), x + 8, y + 9, GuiStyle.text());

            float value = getter.get();
            float ratio = (value - min) / (max - min);

            if (mouseDown && mouseX >= sliderX && mouseX < sliderX + sliderW && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                ratio = Math.max(0, Math.min(1, (float)(mouseX - sliderX) / sliderW));
                float newValue = min + ratio * (max - min);
                setter.accept(newValue);
                TeslaMapsConfig.save();
                float step = (float) Math.pow(10, -decimals);
                int notch = Math.round(newValue / step);
                if (notch != lastNotch) {          // one tick per visible step, not per frame
                    lastNotch = notch;
                    GuiSounds.step(ratio);
                }
            }

            ctx.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, GuiStyle.toggleOff());
            ctx.fill(sliderX, sliderY, sliderX + (int)(sliderW * ratio), sliderY + sliderH, GuiStyle.accent());

            int knobX = sliderX + (int)(sliderW * ratio) - 3;
            ctx.fill(knobX, sliderY - 2, knobX + 6, sliderY + sliderH + 2, GuiStyle.knob());

            int valX = sliderX + sliderW + 8;
            boolean editing = screen.editingSlider == this;
            boolean valHover = mouseX >= valX - 2 && mouseX <= valX + 44 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (clicked && valHover && !editing) { screen.clickHandled = true; GuiSounds.press(); screen.startSliderEdit(this); }
            if (editing) {
                ctx.fill(valX - 2, y + 7, valX + 44, y + 19, GuiStyle.rowSelected());
                ctx.text(screen.font, screen.sliderEditText + "_", valX, y + 9, GuiStyle.text());
            } else {
                String valueStr = String.format("%." + decimals + "f", getter.get());
                ctx.text(screen.font, valueStr, valX, y + 9, valHover ? GuiStyle.text() : GuiStyle.textDim());
            }
        }
    }

    protected class DropdownEntry implements SettingsEntry {
        private int x, width;
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
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                GuiSounds.hover(this);
                ctx.fill(x, y, x + width, y + ROW_HEIGHT, GuiStyle.rowHover());
            }

            int btnW = controlWidth(110, width);
            int btnX = x + width - btnW - 10;
            int btnY = y + 4;
            int btnH = 18;

            ctx.text(screen.font, screen.fitLabel(label, x + 8, btnX), x + 8, y + 9, GuiStyle.text());

            String currentValue = getter.get();
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

            if (clicked && btnHovered) {
                screen.clickHandled = true;
                expanded = !expanded;
                GuiSounds.fold(expanded);
            }

            GuiStyle.panel(ctx, btnX, btnY, btnW, btnH, btnHovered);
            drawBorder(ctx, btnX, btnY, btnW, btnH, expanded ? GuiStyle.accent() : GuiStyle.border());

            String display = screen.fitLabel(currentValue, btnX + 4, btnX + btnW - 14);
            ctx.text(screen.font, display, btnX + 4, btnY + 5, GuiStyle.text());
            ctx.text(screen.font, expanded ? "▲" : "▼", btnX + btnW - 12, btnY + 5, GuiStyle.textDim());

            if (expanded) {
                int optY = y + ROW_HEIGHT;
                for (String option : options) {
                    boolean optHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= optY && mouseY < optY + 18;

                    if (clicked && optHovered) {
                        screen.clickHandled = true;
                        setter.accept(option);
                        TeslaMapsConfig.save();
                        expanded = false;
                        GuiSounds.select();
                    }

                    if (option.equals(currentValue)) ctx.fill(btnX, optY, btnX + btnW, optY + 18, GuiStyle.accent());
                    else GuiStyle.panel(ctx, btnX, optY, btnW, 18, optHovered);
                    drawBorder(ctx, btnX, optY, btnW, 18, GuiStyle.border());

                    String optDisplay = screen.fitLabel(option, btnX + 4, btnX + btnW - 4);
                    ctx.text(screen.font, optDisplay, btnX + 4, optY + 5, GuiStyle.text());
                    optY += 18;
                }
            }
        }
    }

    protected class SoundDropdownEntry implements SettingsEntry {
        private int x, width;
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
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                GuiSounds.hover(this);
                ctx.fill(x, y, x + width, y + ROW_HEIGHT, GuiStyle.rowHover());
            }

            int btnW = controlWidth(110, width), btnX = x + width - btnW - 10, btnY = y + 4, btnH = 18;
            ctx.text(screen.font, screen.fitLabel(label, x + 8, btnX), x + 8, y + 9, GuiStyle.text());

            boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            if (clicked && btnHovered) {
                screen.clickHandled = true;
                GuiSounds.press();
                Minecraft.getInstance().setScreen(new SoundPickerScreen(screen, label, options, getter.get(),
                        v -> { setter.accept(v); TeslaMapsConfig.save(); }));
            }

            GuiStyle.panel(ctx, btnX, btnY, btnW, btnH, btnHovered);
            drawBorder(ctx, btnX, btnY, btnW, btnH, btnHovered ? GuiStyle.accent() : GuiStyle.border());
            String cur = getter.get();
            String display = screen.fitLabel(cur, btnX + 4, btnX + btnW - 4);
            ctx.text(screen.font, display, btnX + 4, btnY + 5, GuiStyle.text());
        }
    }
}
