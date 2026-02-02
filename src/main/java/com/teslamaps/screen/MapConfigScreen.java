package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import com.teslamaps.utils.LoudSound;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MapConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 26;
    private static final int SIDEBAR_WIDTH = 110;
    private static final int COLOR_PICKER_HEIGHT = 100;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private final Map<String, List<SettingsEntry>> categories = new LinkedHashMap<>();
    private String selectedCategory = "Map";
    private String searchQuery = "";

    private TextFieldWidget searchField;
    private boolean wasMouseDown = false;

    // Inline color picker state
    private ColorEntry expandedColorEntry = null;
    private float pickerHue = 0, pickerSat = 1, pickerBright = 1;

    public MapConfigScreen() {
        super(Text.literal("TeslaMaps Settings"));
    }

    @Override
    protected void init() {
        buildCategories();

        int searchWidth = this.width - SIDEBAR_WIDTH - 50;
        searchField = new TextFieldWidget(textRenderer, SIDEBAR_WIDTH + 20, 12, searchWidth, 16, Text.literal("Search"));
        searchField.setPlaceholder(Text.literal("Search..."));
        searchField.setChangedListener(query -> {
            searchQuery = query.toLowerCase();
            scrollOffset = 0;
            expandedColorEntry = null;
        });
        addDrawableChild(searchField);
    }

    private void buildCategories() {
        categories.clear();
        TeslaMapsConfig config = TeslaMapsConfig.get();

        int contentWidth = this.width - SIDEBAR_WIDTH - 50;
        int contentX = SIDEBAR_WIDTH + 25;

        // ===== MAP (combined: Map, Rooms, Checkmarks, Players) =====
        List<SettingsEntry> map = new ArrayList<>();
        // General
        map.add(new LabelEntry(contentX, "General"));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Map", () -> config.mapEnabled, v -> config.mapEnabled = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Only In Dungeon", () -> config.onlyShowInDungeon, v -> config.onlyShowInDungeon = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Background", () -> config.showMapBackground, v -> config.showMapBackground = v));
        // Rooms
        map.add(new LabelEntry(contentX, "Rooms"));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Room Names", () -> config.showRoomNames, v -> config.showRoomNames = v));
        map.add(new SliderEntry(contentX, contentWidth, "Room Name Scale", 0.5f, 2.0f,
                () -> config.roomNameScale, v -> config.roomNameScale = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Secrets", () -> config.showSecretCount, v -> config.showSecretCount = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Hide When Done", () -> config.hideSecretsWhenDone, v -> config.hideSecretsWhenDone = v));
        // Checkmarks
        map.add(new LabelEntry(contentX, "Checkmarks"));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Checkmarks", () -> config.showCheckmarks, v -> config.showCheckmarks = v));
        map.add(new ToggleEntry(contentX, contentWidth, "White Checks", () -> config.showWhiteCheckmarks, v -> config.showWhiteCheckmarks = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Green Checks", () -> config.showGreenCheckmarks, v -> config.showGreenCheckmarks = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Failed Checks", () -> config.showFailedCheckmarks, v -> config.showFailedCheckmarks = v));
        // Players
        map.add(new LabelEntry(contentX, "Players"));
        map.add(new ToggleEntry(contentX, contentWidth, "Player Markers", () -> config.showPlayerMarker, v -> config.showPlayerMarker = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Self", () -> config.showSelfMarker, v -> config.showSelfMarker = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Others", () -> config.showOtherPlayers, v -> config.showOtherPlayers = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Show Names", () -> config.showPlayerNames, v -> config.showPlayerNames = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Use Heads (vs Markers)", () -> config.useHeadsInsteadOfMarkers, v -> config.useHeadsInsteadOfMarkers = v));
        map.add(new ToggleEntry(contentX, contentWidth, "Rotate Heads", () -> config.rotatePlayerHeads, v -> config.rotatePlayerHeads = v));
        map.add(new SliderEntry(contentX, contentWidth, "Head Scale", 0.5f, 2.0f,
                () -> config.playerHeadScale, v -> config.playerHeadScale = v));
        categories.put("Map", map);

        // ===== ESP =====
        List<SettingsEntry> esp = new ArrayList<>();
        esp.add(new LabelEntry(contentX, "Dungeon ESP"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Starred Mob ESP", () -> config.starredMobESP, v -> config.starredMobESP = v));
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
        esp.add(new ToggleEntry(contentX, contentWidth, "Only Show Next Door", () -> config.onlyShowNextDoor, v -> config.onlyShowNextDoor = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Livid Finder", () -> config.lividFinder, v -> config.lividFinder = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Livid Tracer", () -> config.lividTracer, v -> config.lividTracer = v));
        esp.add(new LabelEntry(contentX, "Other ESP"));
        esp.add(new ToggleEntry(contentX, contentWidth, "Pest ESP (Garden)", () -> config.pestESP, v -> config.pestESP = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Pest Tracers", () -> config.pestTracers, v -> config.pestTracers = v));
        esp.add(new ToggleEntry(contentX, contentWidth, "Dropped Item ESP", () -> config.droppedItemESP, v -> config.droppedItemESP = v));
        categories.put("ESP", esp);

        // ===== RENDER =====
        List<SettingsEntry> render = new ArrayList<>();
        render.add(new ToggleEntry(contentX, contentWidth, "No Fire Overlay", () -> config.noFire, v -> config.noFire = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Blindness", () -> config.noBlind, v -> config.noBlind = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Nausea", () -> config.noNausea, v -> config.noNausea = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Explosions", () -> config.noExplosions, v -> config.noExplosions = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Arrows", () -> config.noArrows, v -> config.noArrows = v));
        render.add(new ToggleEntry(contentX, contentWidth, "No Stuck Arrows", () -> config.noStuckArrows, v -> config.noStuckArrows = v));
        categories.put("Render", render);

        // ===== SLAYER =====
        List<SettingsEntry> slayer = new ArrayList<>();
        slayer.add(new ToggleEntry(contentX, contentWidth, "Slayer HUD", () -> config.slayerHUD, v -> config.slayerHUD = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Only Own Boss", () -> config.slayerOnlyOwnBoss, v -> config.slayerOnlyOwnBoss = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Boss ESP", () -> config.slayerBossESP, v -> config.slayerBossESP = v));
        slayer.add(new ToggleEntry(contentX, contentWidth, "Miniboss ESP", () -> config.slayerMinibossESP, v -> config.slayerMinibossESP = v));
        categories.put("Slayer", slayer);

        // ===== SOUNDS =====
        List<SettingsEntry> sounds = new ArrayList<>();
        sounds.add(new LabelEntry(contentX, "Key Pickup"));
        sounds.add(new SliderEntry(contentX, contentWidth, "Pickup Volume", 0f, 20f,
                () -> config.keyPickupVolume, v -> config.keyPickupVolume = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Pickup Sound",
                new String[]{"LEVEL_UP", "BLAZE_DEATH", "GHAST_SHOOT", "WITHER_SPAWN", "ENDER_DRAGON_GROWL", "NOTE_PLING"},
                () -> config.keyPickupSound, v -> config.keyPickupSound = v));
        sounds.add(new LabelEntry(contentX, "Key On Ground"));
        sounds.add(new SliderEntry(contentX, contentWidth, "Ground Volume", 0f, 20f,
                () -> config.keyOnGroundVolume, v -> config.keyOnGroundVolume = v));
        sounds.add(new SoundDropdownEntry(contentX, contentWidth, "Ground Sound",
                new String[]{"NOTE_CHIME", "NOTE_PLING", "EXPERIENCE_ORB", "ANVIL_LAND"},
                () -> config.keyOnGroundSound, v -> config.keyOnGroundSound = v));
        categories.put("Sounds", sounds);

        // ===== COLORS (merged) =====
        List<SettingsEntry> colors = new ArrayList<>();
        colors.add(new LabelEntry(contentX, "Room Colors"));
        colors.add(new ColorEntry(contentX, contentWidth, "Background", () -> config.colorBackground, v -> config.colorBackground = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Unexplored", () -> config.colorUnexplored, v -> config.colorUnexplored = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Normal", () -> config.colorNormal, v -> config.colorNormal = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Entrance", () -> config.colorEntrance, v -> config.colorEntrance = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Blood", () -> config.colorBlood, v -> config.colorBlood = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Trap", () -> config.colorTrap, v -> config.colorTrap = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Puzzle", () -> config.colorPuzzle, v -> config.colorPuzzle = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Fairy", () -> config.colorFairy, v -> config.colorFairy = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Miniboss", () -> config.colorMiniboss, v -> config.colorMiniboss = v));
        colors.add(new LabelEntry(contentX, "Door Colors"));
        colors.add(new ColorEntry(contentX, contentWidth, "Normal Door", () -> config.colorDoorNormal, v -> config.colorDoorNormal = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Wither Door", () -> config.colorDoorWither, v -> config.colorDoorWither = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Blood Door", () -> config.colorDoorBlood, v -> config.colorDoorBlood = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Entrance Door", () -> config.colorDoorEntrance, v -> config.colorDoorEntrance = v));
        colors.add(new LabelEntry(contentX, "Text Colors"));
        colors.add(new ColorEntry(contentX, contentWidth, "Unexplored", () -> config.colorTextUnexplored, v -> config.colorTextUnexplored = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Cleared", () -> config.colorTextCleared, v -> config.colorTextCleared = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Green", () -> config.colorTextGreen, v -> config.colorTextGreen = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Secret Count", () -> config.colorSecretCount, v -> config.colorSecretCount = v));
        colors.add(new LabelEntry(contentX, "Checkmark Colors"));
        colors.add(new ColorEntry(contentX, contentWidth, "White Check", () -> config.colorCheckWhite, v -> config.colorCheckWhite = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Green Check", () -> config.colorCheckGreen, v -> config.colorCheckGreen = v));
        colors.add(new ColorEntry(contentX, contentWidth, "Failed Check", () -> config.colorCheckFailed, v -> config.colorCheckFailed = v));
        categories.put("Colors", colors);

        // ===== AUTO GFS =====
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
        categories.put("Auto GFS", autoGFS);

        // ===== ADVANCED =====
        List<SettingsEntry> advanced = new ArrayList<>();
        advanced.add(new ToggleEntry(contentX, contentWidth, "Auto Scan", () -> config.autoScan, v -> config.autoScan = v));
        advanced.add(new ToggleEntry(contentX, contentWidth, "Debug Mode", () -> config.debugMode, v -> config.debugMode = v));
        categories.put("Advanced", advanced);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown; // Calculate BEFORE updating wasMouseDown

        // Handle category clicks
        if (clicked && mouseX < SIDEBAR_WIDTH && mouseY >= 50 && mouseY < this.height - 45) {
            int catY = 50;
            for (String category : categories.keySet()) {
                if (mouseY >= catY && mouseY < catY + 22) {
                    selectedCategory = category;
                    searchQuery = "";
                    searchField.setText("");
                    scrollOffset = 0;
                    expandedColorEntry = null;
                    break;
                }
                catY += 24;
            }
        }

        // Handle /tmap gui button click (at bottom of sidebar)
        int tmapBtnY = this.height - 35;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= tmapBtnY && mouseY < tmapBtnY + 22) {
            // Send /tmap gui command when clicking this button
            if (client.player != null) {
                client.player.networkHandler.sendChatCommand("tmap gui");
            }
        }

        // Handle done button
        int doneX = this.width - 70;
        int doneY = this.height - 30;
        if (clicked && mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 22) {
            close();
            wasMouseDown = isMouseDown;
            return;
        }

        // Background
        context.fill(0, 0, this.width, this.height, 0xFF1C1C1E);

        // Sidebar
        context.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xFF2C2C2E);
        context.drawTextWithShadow(textRenderer, "TeslaMaps", 10, 8, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Categories", 10, 22, 0xFF30D158);

        // Category buttons
        int catY = 50;
        for (String category : categories.keySet()) {
            boolean selected = category.equals(selectedCategory) && searchQuery.isEmpty();
            boolean hovered = mouseX < SIDEBAR_WIDTH && mouseY >= catY && mouseY < catY + 22;

            if (selected) {
                context.fill(5, catY, SIDEBAR_WIDTH - 5, catY + 22, 0x40FFFFFF);
            } else if (hovered) {
                context.fill(5, catY, SIDEBAR_WIDTH - 5, catY + 22, 0x20FFFFFF);
            }

            int textColor = selected ? 0xFF30D158 : (hovered ? 0xFFFFFFFF : 0xFF8E8E93);
            context.drawTextWithShadow(textRenderer, category, 12, catY + 7, textColor);
            catY += 24;
        }

        // /tmap gui button at bottom of sidebar
        boolean tmapHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= tmapBtnY && mouseY < tmapBtnY + 22;
        context.fill(8, tmapBtnY, SIDEBAR_WIDTH - 8, tmapBtnY + 22, tmapHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, 8, tmapBtnY, SIDEBAR_WIDTH - 16, 22, tmapHovered ? 0xFF30D158 : 0xFF48484A);
        context.drawCenteredTextWithShadow(textRenderer, "/tmap gui", SIDEBAR_WIDTH / 2, tmapBtnY + 7, 0xFF8E8E93);

        // Content area - define boundaries for clipping
        int contentTop = 50;
        int contentBottom = this.height - 40;
        int contentLeft = SIDEBAR_WIDTH;
        int contentRight = this.width - 10; // Leave space for scrollbar

        // Content header (above scroll area)
        String header = searchQuery.isEmpty() ? selectedCategory : "Search Results";
        context.fill(SIDEBAR_WIDTH, 30, this.width, contentTop, 0xFF1C1C1E); // Header background
        context.drawTextWithShadow(textRenderer, header, SIDEBAR_WIDTH + 20, 35, 0xFFFFFFFF);

        // Entries
        List<SettingsEntry> entries = getEntriesToShow();
        int totalHeight = 0;
        for (SettingsEntry e : entries) totalHeight += e.getHeight();
        int visibleHeight = contentBottom - contentTop;
        maxScroll = Math.max(0, totalHeight - visibleHeight);

        // Enable scissor for content area clipping
        context.enableScissor(contentLeft, contentTop, contentRight, contentBottom);

        int y = contentTop - scrollOffset;
        for (SettingsEntry entry : entries) {
            if (y + entry.getHeight() > contentTop - 50 && y < contentBottom + 50) {
                entry.render(context, this, y, mouseX, mouseY, clicked, isMouseDown);
            }
            y += entry.getHeight();
        }

        context.disableScissor();

        // Draw scrollbar on the right side (if content exceeds view)
        if (maxScroll > 0) {
            int scrollbarX = this.width - 8;
            int scrollbarW = 4;
            int scrollbarTrackH = visibleHeight;
            float scrollRatio = (float) scrollOffset / maxScroll;
            float thumbRatio = (float) visibleHeight / totalHeight;
            int thumbH = Math.max(20, (int)(scrollbarTrackH * thumbRatio));
            int thumbY = contentTop + (int)((scrollbarTrackH - thumbH) * scrollRatio);

            // Track
            context.fill(scrollbarX, contentTop, scrollbarX + scrollbarW, contentBottom, 0xFF2C2C2E);
            // Thumb
            context.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF5A5A5C);
        }

        // Bottom bar (covers any overflow)
        context.fill(SIDEBAR_WIDTH, this.height - 40, this.width, this.height, 0xFF1C1C1E);

        // Done button
        boolean doneHovered = mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 22;
        context.fill(doneX, doneY, doneX + 60, doneY + 22, doneHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, doneX, doneY, 60, 22, doneHovered ? 0xFF30D158 : 0xFF48484A);
        context.drawCenteredTextWithShadow(textRenderer, "Done", doneX + 30, doneY + 7, 0xFFFFFFFF);

        wasMouseDown = isMouseDown; // Update AFTER processing

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (mouseX > SIDEBAR_WIDTH) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - v * 15));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void close() {
        TeslaMapsConfig.save();
        super.close();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    // Entry types
    private interface SettingsEntry {
        int getHeight();
        void render(DrawContext ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown);
        default boolean matchesSearch(String q) { return false; }
        default String getLabel() { return ""; }
    }

    private class LabelEntry implements SettingsEntry {
        private final int x;
        private final String text;

        LabelEntry(int x, String text) { this.x = x; this.text = text; }

        @Override public int getHeight() { return 22; }
        @Override public String getLabel() { return text; }

        @Override
        public void render(DrawContext ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            ctx.drawTextWithShadow(screen.textRenderer, text, x, y + 8, 0xFF30D158);
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
        public void render(DrawContext ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.drawTextWithShadow(screen.textRenderer, label, x + 8, y + 9, 0xFFFFFFFF);

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
        public void render(DrawContext ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            int btnX = x + 8;
            int btnY = y + 2;
            int btnW = width - 16;
            int btnH = 22;

            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            if (clicked && hovered) action.run();

            ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, hovered ? 0xFF3A3A3C : 0xFF2C2C2E);
            drawBorder(ctx, btnX, btnY, btnW, btnH, hovered ? 0xFF5A5A5C : 0xFF48484A);
            ctx.drawCenteredTextWithShadow(screen.textRenderer, label, btnX + btnW / 2, btnY + 7, 0xFFFFFFFF);
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
        public void render(DrawContext ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean isExpanded = expandedColorEntry == this;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.drawTextWithShadow(screen.textRenderer, label, x + 8, y + 9, 0xFFFFFFFF);

            // Color swatch
            int swatchX = x + width - 30;
            int swatchY = y + 5;
            int color = TeslaMapsConfig.parseColor(getter.get());

            // Toggle expansion on swatch click
            if (clicked && mouseX >= swatchX - 10 && mouseX < swatchX + 22 && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                if (isExpanded) {
                    expandedColorEntry = null;
                } else {
                    expandedColorEntry = this;
                    // Initialize picker from current color
                    float[] hsv = rgbToHsv((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
                    pickerHue = hsv[0];
                    pickerSat = hsv[1];
                    pickerBright = hsv[2];
                }
            }

            ctx.fill(swatchX, swatchY, swatchX + 22, swatchY + 16, color);
            drawBorder(ctx, swatchX, swatchY, 22, 16, isExpanded ? 0xFF30D158 : 0xFF555555);

            // Hex preview
            String hex = "#" + getter.get().substring(0, Math.min(6, getter.get().length()));
            ctx.drawTextWithShadow(screen.textRenderer, hex, swatchX - screen.textRenderer.getWidth(hex) - 6, y + 9, 0xFF8E8E93);

            // Inline color picker when expanded
            if (isExpanded) {
                int pickerY = y + ROW_HEIGHT + 5;
                int pickerX = x + 10;
                int pickerSize = 80;
                int hueWidth = 12;

                // Sat/Bright picker (chunked)
                for (int cy = 0; cy < pickerSize; cy += 8) {
                    for (int cx = 0; cx < pickerSize; cx += 8) {
                        float sat = (cx + 4f) / pickerSize;
                        float bright = 1.0f - (cy + 4f) / pickerSize;
                        int[] rgb = hsvToRgb(pickerHue, sat, bright);
                        int c = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                        ctx.fill(pickerX + cx, pickerY + cy, pickerX + cx + 8, pickerY + cy + 8, c);
                    }
                }

                // Hue slider (chunked)
                int hueX = pickerX + pickerSize + 8;
                for (int cy = 0; cy < pickerSize; cy += 8) {
                    float h = (cy + 4f) / pickerSize;
                    int[] rgb = hsvToRgb(h, 1f, 1f);
                    int c = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                    ctx.fill(hueX, pickerY + cy, hueX + hueWidth, pickerY + cy + 8, c);
                }

                // Handle picker dragging
                if (mouseDown) {
                    // Sat/bright picker
                    if (mouseX >= pickerX && mouseX < pickerX + pickerSize &&
                        mouseY >= pickerY && mouseY < pickerY + pickerSize) {
                        pickerSat = Math.max(0, Math.min(1, (float)(mouseX - pickerX) / pickerSize));
                        pickerBright = 1.0f - Math.max(0, Math.min(1, (float)(mouseY - pickerY) / pickerSize));
                        updateColorFromPicker();
                    }
                    // Hue slider
                    else if (mouseX >= hueX && mouseX < hueX + hueWidth &&
                             mouseY >= pickerY && mouseY < pickerY + pickerSize) {
                        pickerHue = Math.max(0, Math.min(1, (float)(mouseY - pickerY) / pickerSize));
                        updateColorFromPicker();
                    }
                }

                // Cursor on sat/bright picker
                int cursorX = pickerX + (int)(pickerSat * pickerSize);
                int cursorY = pickerY + (int)((1 - pickerBright) * pickerSize);
                ctx.fill(cursorX - 3, cursorY, cursorX + 3, cursorY + 1, 0xFFFFFFFF);
                ctx.fill(cursorX, cursorY - 3, cursorX + 1, cursorY + 4, 0xFFFFFFFF);

                // Cursor on hue slider
                int hueCursorY = pickerY + (int)(pickerHue * pickerSize);
                ctx.fill(hueX - 1, hueCursorY - 1, hueX + hueWidth + 1, hueCursorY + 2, 0xFFFFFFFF);

                // Preview of new color
                int[] newRgb = hsvToRgb(pickerHue, pickerSat, pickerBright);
                int newColor = 0xFF000000 | (newRgb[0] << 16) | (newRgb[1] << 8) | newRgb[2];
                int previewX = hueX + hueWidth + 15;
                ctx.fill(previewX, pickerY, previewX + 40, pickerY + 25, newColor);
                drawBorder(ctx, previewX, pickerY, 40, 25, 0xFF555555);

                // New hex value
                String newHex = String.format("#%02X%02X%02X", newRgb[0], newRgb[1], newRgb[2]);
                ctx.drawTextWithShadow(screen.textRenderer, newHex, previewX, pickerY + 30, 0xFFFFFFFF);
            }
        }

        private void updateColorFromPicker() {
            int[] rgb = hsvToRgb(pickerHue, pickerSat, pickerBright);
            String hex = String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
            setter.accept(hex);
            TeslaMapsConfig.save();
        }
    }

    // HSV conversion helpers
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

        SliderEntry(int x, int w, String label, float min, float max, Supplier<Float> getter, Consumer<Float> setter) {
            this.x = x; this.width = w; this.label = label;
            this.min = min; this.max = max; this.getter = getter; this.setter = setter;
        }

        @Override public int getHeight() { return ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public void render(DrawContext ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.drawTextWithShadow(screen.textRenderer, label, x + 8, y + 9, 0xFFFFFFFF);

            // Slider track
            int sliderX = x + width - 120;
            int sliderY = y + 10;
            int sliderW = 80;
            int sliderH = 6;

            float value = getter.get();
            float ratio = (value - min) / (max - min);

            // Handle dragging
            if (mouseDown && mouseX >= sliderX && mouseX < sliderX + sliderW && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                ratio = Math.max(0, Math.min(1, (float)(mouseX - sliderX) / sliderW));
                float newValue = min + ratio * (max - min);
                setter.accept(newValue);
                TeslaMapsConfig.save();
            }

            // Draw track
            ctx.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF39393D);
            ctx.fill(sliderX, sliderY, sliderX + (int)(sliderW * ratio), sliderY + sliderH, 0xFF30D158);

            // Draw knob
            int knobX = sliderX + (int)(sliderW * ratio) - 3;
            ctx.fill(knobX, sliderY - 2, knobX + 6, sliderY + sliderH + 2, 0xFFFFFFFF);

            // Draw value
            String valueStr = String.format("%.1f", getter.get());
            ctx.drawTextWithShadow(screen.textRenderer, valueStr, sliderX + sliderW + 8, y + 9, 0xFF8E8E93);
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
        public void render(DrawContext ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.drawTextWithShadow(screen.textRenderer, label, x + 8, y + 9, 0xFFFFFFFF);

            // Dropdown button
            int btnX = x + width - 120;
            int btnY = y + 4;
            int btnW = 110;
            int btnH = 18;

            String currentValue = getter.get();
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

            // Toggle expand on click
            if (clicked && btnHovered) {
                expanded = !expanded;
            }

            ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
            drawBorder(ctx, btnX, btnY, btnW, btnH, expanded ? 0xFF30D158 : 0xFF48484A);

            // Show current value (truncated if needed)
            String display = currentValue.length() > 14 ? currentValue.substring(0, 12) + ".." : currentValue;
            ctx.drawTextWithShadow(screen.textRenderer, display, btnX + 4, btnY + 5, 0xFFFFFFFF);
            ctx.drawTextWithShadow(screen.textRenderer, expanded ? "▲" : "▼", btnX + btnW - 12, btnY + 5, 0xFF8E8E93);

            // Draw options when expanded
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
                    ctx.drawTextWithShadow(screen.textRenderer, optDisplay, btnX + 4, optY + 5, 0xFFFFFFFF);
                    optY += 18;
                }
            }
        }
    }

    /**
     * Dropdown entry that plays the sound when an option is selected.
     */
    private class SoundDropdownEntry implements SettingsEntry {
        private final int x, width;
        private final String label;
        private final String[] options;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private boolean expanded = false;

        SoundDropdownEntry(int x, int w, String label, String[] options, Supplier<String> getter, Consumer<String> setter) {
            this.x = x; this.width = w; this.label = label;
            this.options = options; this.getter = getter; this.setter = setter;
        }

        @Override public int getHeight() { return expanded ? ROW_HEIGHT + (options.length * 18) : ROW_HEIGHT; }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }

        @Override
        public void render(DrawContext ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) ctx.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);

            ctx.drawTextWithShadow(screen.textRenderer, label, x + 8, y + 9, 0xFFFFFFFF);

            // Dropdown button
            int btnX = x + width - 120;
            int btnY = y + 4;
            int btnW = 110;
            int btnH = 18;

            String currentValue = getter.get();
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

            // Toggle expand on click
            if (clicked && btnHovered) {
                expanded = !expanded;
            }

            ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
            drawBorder(ctx, btnX, btnY, btnW, btnH, expanded ? 0xFF30D158 : 0xFF48484A);

            // Show current value (truncated if needed)
            String display = currentValue.length() > 14 ? currentValue.substring(0, 12) + ".." : currentValue;
            ctx.drawTextWithShadow(screen.textRenderer, display, btnX + 4, btnY + 5, 0xFFFFFFFF);
            ctx.drawTextWithShadow(screen.textRenderer, expanded ? "▲" : "▼", btnX + btnW - 12, btnY + 5, 0xFF8E8E93);

            // Draw options when expanded
            if (expanded) {
                int optY = y + ROW_HEIGHT;
                for (String option : options) {
                    boolean optHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= optY && mouseY < optY + 18;

                    if (clicked && optHovered) {
                        setter.accept(option);
                        TeslaMapsConfig.save();
                        expanded = false;
                        // Play the selected sound as preview
                        playPreviewSound(option);
                    }

                    int bgColor = option.equals(currentValue) ? 0xFF30D158 : (optHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
                    ctx.fill(btnX, optY, btnX + btnW, optY + 18, bgColor);
                    drawBorder(ctx, btnX, optY, btnW, 18, 0xFF48484A);

                    String optDisplay = option.length() > 14 ? option.substring(0, 12) + ".." : option;
                    ctx.drawTextWithShadow(screen.textRenderer, optDisplay, btnX + 4, optY + 5, 0xFFFFFFFF);
                    optY += 18;
                }
            }
        }

        private void playPreviewSound(String soundName) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            net.minecraft.sound.SoundEvent sound = switch (soundName) {
                case "BLAZE_DEATH" -> SoundEvents.ENTITY_BLAZE_DEATH;
                case "GHAST_SHOOT" -> SoundEvents.ENTITY_GHAST_SHOOT;
                case "WITHER_SPAWN" -> SoundEvents.ENTITY_WITHER_SPAWN;
                case "ENDER_DRAGON_GROWL" -> SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
                case "NOTE_PLING" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
                case "NOTE_CHIME" -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
                case "EXPERIENCE_ORB" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
                case "ANVIL_LAND" -> SoundEvents.BLOCK_ANVIL_LAND;
                default -> SoundEvents.ENTITY_PLAYER_LEVELUP; // LEVEL_UP
            };

            // Play at volume 2.0 for preview so user can hear it
            LoudSound.play(sound, 2.0f, 1.0f);
        }
    }
}
