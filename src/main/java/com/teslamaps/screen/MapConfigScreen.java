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
    public static final String[] LOADOUT_NAMES = {
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
    // Folded sidebar sections, by section name.
    protected static final java.util.Set<String> SECTION_COLLAPSED = new java.util.HashSet<>();

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
    protected int pickerAlpha = 255;
    protected ColorEntry editingColor = null;     // color whose hex is being typed
    protected String colorEditText = "";

    protected KeybindEntry listeningKeybindEntry = null;
    protected long keybindListenStart = 0;
    protected SliderEntry editingSlider = null;   // slider whose value is being typed
    protected String sliderEditText = "";
    private static final long DESC_DELAY_MS = 500;
    private SettingsEntry descHoverEntry = null;
    protected SearchListEntry editingList = null;  // list whose search/colour field takes keys
    protected boolean rightClickedNow = false;
    private long descHoverStart = 0;

    public boolean isBindingKey() {
        return listeningKeybindEntry != null || editingSlider != null || editingColor != null || editingList != null;
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
            editingList = null;
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
        if (editingList != null) { editingList.key(event.key()); return true; }
        if (editingColor != null) {
            int k = event.key();
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { if (editingColor.commitHex(colorEditText)) editingColor = null; return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { editingColor = null; return true; }
            if (k == GLFW.GLFW_KEY_BACKSPACE) {
                if (!colorEditText.isEmpty()) colorEditText = colorEditText.substring(0, colorEditText.length() - 1);
                return true;
            }
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
        if (editingList != null) { editingList.chr((char) event.codepoint()); return true; }
        if (editingColor != null) {
            char c = Character.toUpperCase((char) event.codepoint());
            if (((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) && colorEditText.length() < 8) colorEditText += c;
            return true;
        }
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

    public static String nextProfileName() {
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
        }
        for (Map.Entry<String, List<String>> s : sections.entrySet()) {
            if (s.getValue().contains(selectedCategory)) SECTION_COLLAPSED.remove(s.getKey());
        }
        scrollSidebarTo(selectedCategory);

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

    /** Rebuilds all rows, e.g. after a profile switch. */
    public void rebuild() {
        rebuildWidgets();
    }

    private void category(com.teslamaps.screen.config.ConfigBuilder b, String name,
                          java.util.function.BiConsumer<com.teslamaps.screen.config.ConfigBuilder, TeslaMapsConfig> build) {
        List<SettingsEntry> list = new ArrayList<>();
        b.into(list);
        build.accept(b, TeslaMapsConfig.get());
        categories.put(name, list);
    }

    protected void buildCategories() {
        buildCount++;
        categories.clear();
        TeslaMapsConfig config = TeslaMapsConfig.get();

        int contentWidth = Math.max(150, this.width - SIDEBAR_WIDTH - 50);
        int contentX = SIDEBAR_WIDTH + 25;

        com.teslamaps.screen.config.ConfigBuilder b = new com.teslamaps.screen.config.ConfigBuilder(this, contentX, contentWidth);
        category(b, "Legit Mode", com.teslamaps.screen.config.LegitModeCategory::build);
        category(b, "Map", com.teslamaps.screen.config.MapCategory::build);
        category(b, "ESP", com.teslamaps.screen.config.ESPCategory::build);
        category(b, "Render", com.teslamaps.screen.config.RenderCategory::build);
        category(b, "Menu & Skybox", com.teslamaps.screen.config.MenuSkyboxCategory::build);
        category(b, "Hide", com.teslamaps.screen.config.HideCategory::build);
        category(b, "Combat", com.teslamaps.screen.config.CombatCategory::build);
        category(b, "Skills", com.teslamaps.screen.config.SkillsCategory::build);
        category(b, "Fishing", com.teslamaps.screen.config.FishingCategory::build);
        category(b, "Crimson Isle", com.teslamaps.screen.config.CrimsonIsleCategory::build);
        category(b, "Mob Visuals", com.teslamaps.screen.config.MobVisualsCategory::build);
        category(b, "The End", com.teslamaps.screen.config.TheEndCategory::build);
        category(b, "Puzzles & Terminals", com.teslamaps.screen.config.PuzzlesTerminalsCategory::build);
        category(b, "F7 / M7", com.teslamaps.screen.config.F7M7Category::build);
        category(b, "F4 / M4", com.teslamaps.screen.config.F4M4Category::build);
        category(b, "Sounds", com.teslamaps.screen.config.SoundsCategory::build);
        category(b, "Auto", com.teslamaps.screen.config.AutoCategory::build);
        category(b, "Leap", com.teslamaps.screen.config.LeapCategory::build);
        category(b, "Party", com.teslamaps.screen.config.PartyCategory::build);
        category(b, "Score & Splits", com.teslamaps.screen.config.ScoreSplitsCategory::build);
        category(b, "Timers", com.teslamaps.screen.config.TimersCategory::build);
        category(b, "Blood Camp", com.teslamaps.screen.config.BloodCampCategory::build);
        category(b, "Secrets & Waypoints", com.teslamaps.screen.config.SecretsWaypointsCategory::build);
        category(b, "Ordered Waypoints", com.teslamaps.screen.config.OrderedWaypointsCategory::build);
        category(b, "Croesus", com.teslamaps.screen.config.CroesusCategory::build);
        category(b, "Screenshots", com.teslamaps.screen.config.ScreenshotsCategory::build);
        category(b, "Inventory", com.teslamaps.screen.config.InventoryCategory::build);
        category(b, "Tooltips & Value", com.teslamaps.screen.config.TooltipsValueCategory::build);
        category(b, "Chat", com.teslamaps.screen.config.ChatCategory::build);
        category(b, "Mining", com.teslamaps.screen.config.MiningCategory::build);
        category(b, "Garden", com.teslamaps.screen.config.GardenCategory::build);
        category(b, "Foraging", com.teslamaps.screen.config.ForagingCategory::build);
        category(b, "Contest Timers", com.teslamaps.screen.config.ContestTimersCategory::build);
        category(b, "Torrhus Canyon", com.teslamaps.screen.config.TorrhusCanyonCategory::build);
        category(b, "Critter Safari", com.teslamaps.screen.config.CritterSafariCategory::build);
        category(b, "Shards", com.teslamaps.screen.config.ShardsCategory::build);
        category(b, "Profiles", com.teslamaps.screen.config.ProfilesCategory::build);
        category(b, "Interface", com.teslamaps.screen.config.InterfaceCategory::build);
        com.teslamaps.registry.FeatureRegistry.addConfig(b, categories);

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
                "Contest Timers", "Torrhus Canyon", "Critter Safari", "Crimson Isle", "The End",
                "Mob Visuals", "Shards", "Ordered Waypoints"));
        com.teslamaps.registry.FeatureRegistry.addSidebar(sections);

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
                "Highlight Teammates", "Invisible Armor Stand ESP",
                "Dropped Item ESP", "Item Pickup Readiness",
                "Miniboss ESP", "Enable Auto GFS", "Auto Wish (Healer Ult)", "Critter ESP",
                "Critter ESP (Safari)", "Bee Nest ESP", "Exploding Frog ESP", "Hidden Critter Walls",
                // only the master: the sub-labels ("Glow", "Tracers", ...) collide with other
                // categories, and MobVisuals.active() already refuses to run under Legit Mode
                "Mob Visuals");
        java.util.Set<String> seenCheat = new java.util.HashSet<>();
        for (List<SettingsEntry> entries : categories.values()) {
            for (SettingsEntry e : entries) {
                // trim: sub-options carry a leading indent that is not part of the name
                if (e instanceof ToggleEntry te && cheatLabels.contains(te.getLabel().trim())) {
                    te.legit();
                    seenCheat.add(te.getLabel().trim());
                }
            }
        }
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
            for (String l : cheatLabels) {
                if (!seenCheat.contains(l)) com.teslamaps.TeslaMaps.LOGGER.error("cheatLabels: no toggle named \"{}\" - renamed?", l);
            }
        }
    }

    public static void orderedFeedback(String message) {
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
        List<String> gated = new ArrayList<>(TeslaMapsConfig.SECTIONS);
        gated.addAll(com.teslamaps.registry.FeatureRegistry.gatedCategories());
        for (String cat : gated) {
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
            if (!SECTION_COLLAPSED.contains(sec.getKey())) {
                for (String cat : sec.getValue()) {
                    if (cat.equals(category)) hitY = rowY;
                    rowY += CATEGORY_ROW_H;
                }
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
            if (SECTION_COLLAPSED.contains(sec.getKey())) { rowY += SECTION_GAP; continue; }
            for (String cat : sec.getValue()) {
                if (my >= rowY && my < rowY + CATEGORY_ROW_H) return cat;
                rowY += CATEGORY_ROW_H;
            }
            rowY += SECTION_GAP;
        }
        return null;
    }

    protected String sectionAtY(int my) {
        int rowY = SIDEBAR_TOP - sidebarScroll;
        for (Map.Entry<String, List<String>> sec : sections.entrySet()) {
            if (my >= rowY && my < rowY + SECTION_HEADER_H) return sec.getKey();
            rowY += SECTION_HEADER_H;
            if (!SECTION_COLLAPSED.contains(sec.getKey())) rowY += sec.getValue().size() * CATEGORY_ROW_H;
            rowY += SECTION_GAP;
        }
        return null;
    }

    /** Click folds one section; shift-click shows only that one (again = all back). */
    protected void toggleSection(String name, boolean solo) {
        if (solo) {
            boolean othersOpen = false;
            for (String s : sections.keySet()) if (!s.equals(name) && !SECTION_COLLAPSED.contains(s)) othersOpen = true;
            SECTION_COLLAPSED.clear();
            if (othersOpen) for (String s : sections.keySet()) if (!s.equals(name)) SECTION_COLLAPSED.add(s);
        } else if (!SECTION_COLLAPSED.remove(name)) {
            SECTION_COLLAPSED.add(name);
        }
        GuiSounds.fold(!SECTION_COLLAPSED.contains(name));
        sidebarScroll = 0;
    }

    private List<SettingsEntry> searchCache;
    private String searchCacheQuery;
    private int searchCacheBuild = -1;
    private int buildCount;

    protected List<SettingsEntry> getEntriesToShow() {
        if (!searchQuery.isEmpty() && searchCache != null && searchQuery.equals(searchCacheQuery) && searchCacheBuild == buildCount) {
            for (SettingsEntry e : searchCache) if (e instanceof LabelEntry le) le.collapseKey = null;
            return searchCache;
        }
        List<SettingsEntry> result = new ArrayList<>();
        if (!searchQuery.isEmpty()) {
            for (Map.Entry<String, List<SettingsEntry>> cat : categories.entrySet()) {
                int before = result.size();
                boolean carry = false;               // a matched parent drags its indented sub-options along
                boolean groupHit = false;            // a matched group label brings its whole group
                for (SettingsEntry entry : cat.getValue()) {
                    if (entry.isLabel()) { carry = false; groupHit = searchHit(entry, searchQuery); continue; }
                    boolean child = entry.getLabel().startsWith(" ");
                    if (groupHit || searchHit(entry, searchQuery)) {
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
            searchCache = result;
            searchCacheQuery = searchQuery;
            searchCacheBuild = buildCount;
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
            String sec = sectionAtY(mouseY);
            String hit = sec != null ? null : categoryAtY(mouseY);
            if (sec != null) {
                toggleSection(sec, Minecraft.getInstance().hasShiftDown());
            } else if (hit != null) {
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
            boolean open = !SECTION_COLLAPSED.contains(sec.getKey());
            if (rowY + SECTION_HEADER_H > sbTop && rowY < sbBottom) {
                boolean secHover = mouseX < SIDEBAR_WIDTH && mouseY >= rowY && mouseY < rowY + SECTION_HEADER_H
                        && mouseY >= sbTop && mouseY < sbBottom;
                if (secHover) {
                    GuiSounds.hover("sec:" + sec.getKey());
                    context.fill(5, rowY + 1, SIDEBAR_WIDTH - 5, rowY + SECTION_HEADER_H - 1, GuiStyle.rowHover());
                }
                int secColor = secHover ? 0xFFFFFFFF : GuiStyle.textFaint();
                int cy = rowY + 7 + (open ? 2 : 0);
                if (open) for (int i = 0; i < 4; i++) context.fill(10 + i, cy + i, 17 - i, cy + i + 1, secColor);
                else for (int i = 0; i < 4; i++) context.fill(10 + i, cy + i, 11 + i, cy + 7 - i, secColor);
                drawSidebarText(context, sec.getKey().toUpperCase(), 21, rowY + 8, secColor);
                if (!open) {
                    String n = String.valueOf(sec.getValue().size());
                    context.text(font, n, SIDEBAR_WIDTH - 10 - font.width(n), rowY + 8, GuiStyle.textFaint());
                }
            }
            rowY += SECTION_HEADER_H;
            if (!open) { rowY += SECTION_GAP; continue; }
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
        rightClickedNow = rightClicked;
        int y = contentTop - scrollOffset;
        SettingsEntry hoverEntry = null;
        boolean inContent = mouseX >= contentLeft && mouseX < contentRight && mouseY >= contentTop && mouseY < contentBottom;
        for (SettingsEntry entry : entries) {
            if (y + entry.getHeight() > contentTop - 50 && y < contentBottom + 50) {
                entry.render(context, this, y, mouseX, mouseY, clicked && !clickHandled, isMouseDown);
            }
            if (inContent && mouseY >= y && mouseY < y + Math.min(entry.getHeight(), ROW_HEIGHT + 4)) hoverEntry = entry;
            y += entry.getHeight();
        }
        if (entries.isEmpty() && !searchQuery.isEmpty()) {
            context.centeredText(font, "No results found.", (contentLeft + contentRight) / 2, contentTop + 20, GuiStyle.textDim());
        }

        if (clicked && !clickHandled) editingList = null;
        context.disableScissor();

        String desc = hoverEntry != null ? hoverEntry.description() : null;
        if (desc == null || hoverEntry != descHoverEntry) {
            descHoverEntry = desc == null ? null : hoverEntry;
            descHoverStart = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - descHoverStart >= DESC_DELAY_MS && !isMouseDown) {
            context.setTooltipForNextFrame(font, font.split(Component.literal(desc), 220), mouseX, mouseY);
        }

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

    public interface SettingsEntry {
        int getHeight();
        void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown);
        default boolean matchesSearch(String q) { return false; }
        default String getLabel() { return ""; }
        default boolean scrollAt(int mouseX, int mouseY, int rowY, double dir) { return false; }
        // Card layout (MapConfigScreen2) repositions entries into their card before rendering.
        default void reposition(int x, int w) {}
        default boolean isLabel() { return false; }
        default boolean isToggle() { return false; }
        default String description() { return null; }
        default boolean matchesMeta(String q) { return false; }
    }

    static boolean searchHit(SettingsEntry e, String q) {
        return e.matchesSearch(q) || e.matchesMeta(q);
    }

    /** Hover description and extra search words: {@code .desc("...")}, {@code .tags("esp", ...)}. */
    @SuppressWarnings("unchecked")
    public abstract class DescribedEntry<T extends DescribedEntry<T>> implements SettingsEntry {
        private String description;
        private String descLower;
        private String[] tags;

        public T desc(String d) {
            description = d;
            descLower = d == null ? null : d.toLowerCase();
            return (T) this;
        }

        public T tags(String... t) {
            tags = new String[t.length];
            for (int i = 0; i < t.length; i++) tags[i] = t[i].toLowerCase();
            return (T) this;
        }

        @Override public String description() { return description; }

        @Override public boolean matchesMeta(String q) {
            if (descLower != null && descLower.contains(q)) return true;
            if (tags != null) for (String t : tags) if (t.contains(q)) return true;
            return false;
        }
    }

    // Plain descriptive text. Never a collapsible group header.
    public class InfoEntry implements SettingsEntry {
        private int x;
        private final String text;

        public InfoEntry(int x, String text) { this.x = x; this.text = text; }

        @Override public int getHeight() { return 14; }
        @Override public String getLabel() { return text; }
        @Override public void reposition(int x, int w) { this.x = x; }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            ctx.text(screen.font, text, x + 10, y + 3, GuiStyle.textDim());
        }
    }

    public class LabelEntry extends DescribedEntry<LabelEntry> {
        private int x;
        private final String text;
        String collapseKey = null;
        int childCount = 0;

        private final String textLower;

        public LabelEntry(int x, String text) { this.x = x; this.text = text; this.textLower = text.toLowerCase(); }

        @Override public int getHeight() { return 24; }
        @Override public String getLabel() { return text; }
        @Override public boolean isLabel() { return true; }
        @Override public boolean matchesSearch(String q) { return textLower.contains(q); }
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

    static final String CHEAT_MARK = "⚠ ";
    static final String CHEAT_TIP = "Cheat — may break Hypixel rules (ban risk)";
    static final int CHEAT_COLOR = 0xFFFF5555;

    public class ToggleEntry extends DescribedEntry<ToggleEntry> {
        private int x, width;
        private final String label;
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;
        private boolean legitGated = false;
        private boolean cheat = false;
        private String sectionGate = null;

        public ToggleEntry(int x, int w, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.x = x; this.width = w; this.label = label; this.getter = getter; this.setter = setter;
        }

        /** Marks this toggle as disabled by Legit Mode (greyed out while it blocks cheats). */
        public ToggleEntry legit() { this.legitGated = true; return this; }

        /** Cheat-Warnung: red label with a warning sign, ban-risk tooltip, greyed out by Legit Mode. */
        public ToggleEntry cheat() { this.cheat = true; this.legitGated = true; return this; }

        public boolean isCheat() { return cheat; }

        /** Greys the toggle out while its section master is off, since it does nothing then. */
        public ToggleEntry gatedBy(String section) { this.sectionGate = section; return this; }

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

            int labelX = x + 8;
            if (cheat) {
                ctx.text(screen.font, CHEAT_MARK, labelX, y + 9, gatedOff ? 0xFF666666 : CHEAT_COLOR);
                labelX += screen.font.width(CHEAT_MARK);
            }

            if (gatedOff) {
                ctx.text(screen.font, screen.fitLabel(label, labelX, toggleX), labelX, y + 9, 0xFF666666);
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

            ctx.text(screen.font, screen.fitLabel(label, labelX, toggleX), labelX, y + 9, cheat ? CHEAT_COLOR : GuiStyle.text());
            if (cheat && hovered) {
                int tw = screen.font.width(CHEAT_TIP);
                int tx = Math.max(SIDEBAR_WIDTH + 5, mouseX - tw - 6), ty = y - 2;
                ctx.fill(tx - 3, ty - 2, tx + tw + 3, ty + 11, 0xEE000000);
                ctx.text(screen.font, CHEAT_TIP, tx, ty, CHEAT_COLOR);
            }

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

    public class ButtonEntry extends DescribedEntry<ButtonEntry> {
        private int x, width;
        private final String label;
        private final Runnable action;
        private boolean active = false;   // draws a check and a green border, for the picked profile

        public ButtonEntry(int x, int w, String label, Runnable action) {
            this.x = x; this.width = w; this.label = label; this.action = action;
        }

        public ButtonEntry active() { this.active = true; return this; }

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
    public class ButtonRowEntry extends DescribedEntry<ButtonRowEntry> {
        private int x, width;
        private final String[] labels;
        private final Runnable[] actions;
        private final Object[] hoverKeys;

        public ButtonRowEntry(int x, int w, String[] labels, Runnable[] actions) {
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
    public class ProfileTilesEntry implements SettingsEntry {
        private static final int TILE_H = 24;
        private static final int TILE_MAX_W = 170;
        private static final int GAP = 6;

        private int x, width;
        private final List<String> names;
        private final Object[] hoverKeys;

        public ProfileTilesEntry(int x, int w) {
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

    public class ClassFilterEntry extends DescribedEntry<ClassFilterEntry> {
        private int x, width;
        private final String label;
        private final TeslaMapsConfig.ClassFilter filter;

        public ClassFilterEntry(int x, int w, String label, TeslaMapsConfig.ClassFilter filter) {
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

    public class KeybindEntry extends DescribedEntry<KeybindEntry> {
        private int x, width;
        private final String label;
        private final IntSupplier getter;
        private final IntConsumer setter;

        public KeybindEntry(int x, int w, String label, IntSupplier getter, IntConsumer setter) {
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

    public class ColorEntry extends DescribedEntry<ColorEntry> {
        private int x, width;
        private final String label;
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        public ColorEntry(int x, int w, String label, Supplier<String> getter, Consumer<String> setter) {
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

            boolean editing = screen.editingColor == this;
            String hexLabel = editing ? "#" + screen.colorEditText + "_" : hexOf(color);
            int hexW = Math.max(screen.font.width(hexLabel), screen.font.width("#DDDDDDDD_"));
            int hexX = swatchX - hexW - 6;
            ctx.text(screen.font, screen.fitLabel(label, x + 8, hexX), x + 8, y + 9, GuiStyle.text());

            boolean hexHover = mouseX >= hexX - 2 && mouseX < swatchX - 2 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (clicked && hexHover) {
                screen.clickHandled = true;
                GuiSounds.press();
                if (!editing) {
                    screen.editingColor = this;
                    screen.colorEditText = hexOf(color).substring(1);
                    if (screen.searchField != null) screen.searchField.setFocused(false);
                }
            } else if (clicked && hovered) {
                screen.clickHandled = true;
                if (editing) screen.editingColor = null;
                GuiSounds.fold(!isExpanded);
                if (isExpanded) {
                    expandedColorEntry = null;
                } else {
                    expandedColorEntry = this;
                    loadPicker(color);
                }
            }

            ctx.fill(swatchX, swatchY, swatchX + 22, swatchY + 16, color);
            drawBorder(ctx, swatchX, swatchY, 22, 16, isExpanded ? GuiStyle.accent() : 0xFF555555);

            int hexColor = editing ? (validHex(screen.colorEditText) ? GuiStyle.text() : 0xFFFF5555)
                    : hexHover ? GuiStyle.text() : GuiStyle.textDim();
            if (editing) ctx.fill(hexX - 2, y + 7, swatchX - 2, y + 19, GuiStyle.rowSelected());
            ctx.text(screen.font, hexLabel, hexX, y + 9, hexColor);

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

                int alphaX = hueX + hueWidth + 6;
                for (int cy = 0; cy < pickerSize; cy += 4) {
                    for (int cx = 0; cx < hueWidth; cx += 4) {
                        int c = ((cx + cy) / 4) % 2 == 0 ? 0xFFCCCCCC : 0xFF888888;
                        ctx.fill(alphaX + cx, pickerY + cy, alphaX + cx + 4, pickerY + cy + 4, c);
                    }
                }
                int[] base = hsvToRgb(pickerHue, pickerSat, pickerBright);
                int rgbOnly = (base[0] << 16) | (base[1] << 8) | base[2];
                for (int cy = 0; cy < pickerSize; cy += 4) {
                    int a = 255 - (int) ((cy + 2f) / pickerSize * 255);
                    ctx.fill(alphaX, pickerY + cy, alphaX + hueWidth, pickerY + cy + 4, (a << 24) | rgbOnly);
                }

                if (mouseDown) {
                    if (mouseX >= alphaX && mouseX < alphaX + hueWidth &&
                        mouseY >= pickerY && mouseY < pickerY + pickerSize) {
                        pickerAlpha = 255 - Math.round(Math.max(0, Math.min(1, (float)(mouseY - pickerY) / pickerSize)) * 255);
                        updateColorFromPicker();
                    }
                    else if (mouseX >= pickerX && mouseX < pickerX + pickerSize &&
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
                int alphaCursorY = pickerY + (int)((255 - pickerAlpha) / 255f * pickerSize);
                ctx.fill(alphaX - 1, alphaCursorY - 1, alphaX + hueWidth + 1, alphaCursorY + 2, 0xFFFFFFFF);

                int newColor = (pickerAlpha << 24) | rgbOnly;
                int previewX = alphaX + hueWidth + 15;
                ctx.fill(previewX, pickerY, previewX + 20, pickerY + 25, 0xFFCCCCCC);
                ctx.fill(previewX + 20, pickerY, previewX + 40, pickerY + 25, 0xFF888888);
                ctx.fill(previewX, pickerY, previewX + 40, pickerY + 25, newColor);
                drawBorder(ctx, previewX, pickerY, 40, 25, 0xFF555555);

                ctx.text(screen.font, hexOf(newColor), previewX, pickerY + 30, 0xFFFFFFFF);
            }
        }

        private void updateColorFromPicker() {
            int[] rgb = hsvToRgb(pickerHue, pickerSat, pickerBright);
            // opaque stays RRGGBB so untouched colors keep their stored form
            String hex = pickerAlpha == 255
                    ? String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2])
                    : String.format("%02X%02X%02X%02X", pickerAlpha, rgb[0], rgb[1], rgb[2]);
            setter.accept(hex);
            TeslaMapsConfig.save();
        }

        private void loadPicker(int color) {
            float[] hsv = rgbToHsv((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
            pickerHue = hsv[0];
            pickerSat = hsv[1];
            pickerBright = hsv[2];
            pickerAlpha = (color >>> 24) & 0xFF;
        }

        /** 6 or 8 hex digits; false keeps the field open. */
        boolean commitHex(String text) {
            if (!validHex(text)) return false;
            setter.accept(text.length() == 8 && text.startsWith("FF") ? text.substring(2) : text);
            TeslaMapsConfig.save();
            GuiSounds.select();
            if (expandedColorEntry == this) loadPicker(TeslaMapsConfig.parseColor(getter.get()));
            return true;
        }
    }

    private static String hexOf(int argb) {
        int a = (argb >>> 24) & 0xFF;
        return a == 255 ? String.format("#%06X", argb & 0xFFFFFF) : String.format("#%08X", argb);
    }

    private static boolean validHex(String t) {
        return (t.length() == 6 || t.length() == 8) && t.chars().allMatch(c -> Character.digit(c, 16) >= 0);
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

    public class SliderEntry extends DescribedEntry<SliderEntry> {
        private int x, width;
        private final String label;
        private final float min, max;
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final int decimals;
        private int lastNotch = Integer.MIN_VALUE;

        public SliderEntry(int x, int w, String label, float min, float max, Supplier<Float> getter, Consumer<Float> setter) {
            this(x, w, label, min, max, getter, setter, 1);
        }

        public SliderEntry(int x, int w, String label, float min, float max, Supplier<Float> getter, Consumer<Float> setter, int decimals) {
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

    public class DropdownEntry extends DescribedEntry<DropdownEntry> {
        private int x, width;
        private final String label;
        private final String[] options;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private boolean expanded = false;

        public DropdownEntry(int x, int w, String label, String[] options, Supplier<String> getter, Consumer<String> setter) {
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

    public class SoundDropdownEntry extends DescribedEntry<SoundDropdownEntry> {
        private int x, width;
        private final String label;
        private final String[] options;
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        public SoundDropdownEntry(int x, int w, String label, String[] options, Supplier<String> getter, Consumer<String> setter) {
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

    /**
     * Long id list with search, optional icons, display names and a colour swatch per row.
     * Click toggles, right-click edits the colour, selected rows sit on top.
     */
    public class SearchListEntry extends DescribedEntry<SearchListEntry> {
        private static final int ROW = 18, VIEW_ROWS = 8, SEARCH_H = 20, ACTIONS_H = 22, EDIT_H = 20;
        private int x, width;
        private final String label;
        private final Supplier<List<String>> options;
        private final java.util.function.Predicate<String> selected;
        private final Consumer<String> onToggle;
        private java.util.function.Function<String, String> displayName = id -> id;
        private java.util.function.Function<String, net.minecraft.world.item.ItemStack> icon = null;
        private java.util.function.Function<String, String> color = null;
        private java.util.function.BiConsumer<String, String> onColor = null;
        private Supplier<String> pick = null;
        private Runnable clearAll = null;

        private boolean expanded = false;
        private int scroll = 0;
        private String query = "";
        private String colorId = null;
        private String colorText = "";
        private boolean typingColor = false;
        private long clearArmed = 0;
        private final Object[] actionHover = {new Object(), new Object()};

        public SearchListEntry(int x, int w, String label, Supplier<List<String>> options,
                               java.util.function.Predicate<String> selected, Consumer<String> onToggle) {
            this.x = x; this.width = w; this.label = label;
            this.options = options; this.selected = selected; this.onToggle = onToggle;
        }

        public SearchListEntry names(java.util.function.Function<String, String> f) { this.displayName = f; return this; }
        public SearchListEntry icons(java.util.function.Function<String, net.minecraft.world.item.ItemStack> f) { this.icon = f; return this; }
        public SearchListEntry colors(java.util.function.Function<String, String> get, java.util.function.BiConsumer<String, String> set) {
            this.color = get; this.onColor = set; return this;
        }
        /** "Pick from World": returns the id to add, or null. */
        public SearchListEntry pick(Supplier<String> f) { this.pick = f; return this; }
        public SearchListEntry clearAll(Runnable r) { this.clearAll = r; return this; }

        private boolean hasActions() { return pick != null || clearAll != null; }

        @Override public int getHeight() {
            if (!expanded) return ROW_HEIGHT;
            return ROW_HEIGHT + SEARCH_H + VIEW_ROWS * ROW + 4 + (colorId != null ? EDIT_H : 0) + (hasActions() ? ACTIONS_H : 0);
        }
        @Override public String getLabel() { return label; }
        @Override public boolean matchesSearch(String q) { return label.toLowerCase().contains(q); }
        @Override public void reposition(int x, int w) { this.x = x; this.width = w; }

        private List<String> rowsCache;
        private final java.util.Set<String> selectedCache = new java.util.HashSet<>();
        private int countCache;
        private String rowsQuery;
        private long rowsAt;

        private void invalidateRows() { rowsCache = null; }

        private boolean isOn(String id) { return selectedCache.contains(id); }

        private List<String> rows() {
            long now = System.currentTimeMillis();
            if (rowsCache != null && query.equals(rowsQuery) && now - rowsAt < 1000) return rowsCache;
            String q = query.toLowerCase();
            List<String> on = new ArrayList<>(), off = new ArrayList<>();
            selectedCache.clear();
            int count = 0;
            for (String id : options.get()) {
                boolean sel = selected.test(id);
                if (sel) { count++; selectedCache.add(id); }
                if (!q.isEmpty() && !id.toLowerCase().contains(q) && !displayName.apply(id).toLowerCase().contains(q)) continue;
                (sel ? on : off).add(id);
            }
            on.addAll(off);
            rowsCache = on;
            rowsQuery = query;
            rowsAt = now;
            countCache = count;
            return on;
        }

        @Override public boolean scrollAt(int mouseX, int mouseY, int rowY, double dir) {
            if (!expanded) return false;
            int viewY = rowY + ROW_HEIGHT + SEARCH_H;
            if (mouseY < viewY || mouseY >= viewY + VIEW_ROWS * ROW) return false;
            int max = Math.max(0, rows().size() - VIEW_ROWS);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dir)));
            return true;
        }

        void key(int k) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { stopTyping(); return; }
            boolean enter = k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER;
            if (typingColor) {
                if (enter) {
                    if (colorText.length() == 6 && onColor != null) { onColor.accept(colorId, colorText); TeslaMapsConfig.save(); GuiSounds.select(); }
                    colorId = null;
                    stopTyping();
                } else if (k == GLFW.GLFW_KEY_BACKSPACE && !colorText.isEmpty()) {
                    colorText = colorText.substring(0, colorText.length() - 1);
                }
                return;
            }
            if (enter) { stopTyping(); return; }
            if (k == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) { query = query.substring(0, query.length() - 1); scroll = 0; }
        }

        void chr(char c) {
            if (typingColor) {
                c = Character.toUpperCase(c);
                if (((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) && colorText.length() < 6) colorText += c;
            } else if (c >= ' ' && query.length() < 40) {
                query += c;
                scroll = 0;
            }
        }

        private void stopTyping() {
            typingColor = false;
            if (editingList == this) editingList = null;
        }

        private void startTyping(boolean colorField) {
            typingColor = colorField;
            editingList = this;
            if (searchField != null) searchField.setFocused(false);
        }

        private int swatchColor(String id) {
            String hex = color == null ? null : color.apply(id);
            return hex == null ? 0xFF555555 : TeslaMapsConfig.parseColor(hex);
        }

        @Override
        public void render(GuiGraphicsExtractor ctx, MapConfigScreen screen, int y, int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                GuiSounds.hover(this);
                ctx.fill(x, y, x + width, y + ROW_HEIGHT, GuiStyle.rowHover());
            }
            List<String> all = rows();
            int count = countCache;
            String cnt = count + " selected " + (expanded ? "▲" : "▼");
            int cntX = x + width - 10 - screen.font.width(cnt);
            ctx.text(screen.font, screen.fitLabel(label, x + 8, cntX), x + 8, y + 9, GuiStyle.text());
            ctx.text(screen.font, cnt, cntX, y + 9, GuiStyle.textDim());
            if (clicked && hovered) {
                screen.clickHandled = true;
                expanded = !expanded;
                if (!expanded) { colorId = null; stopTyping(); }
                GuiSounds.fold(expanded);
                return;
            }
            if (!expanded) return;

            int left = x + 8, right = x + width - 8;
            int sy = y + ROW_HEIGHT;
            boolean typingQuery = editingList == this && !typingColor;
            boolean overSearch = mouseX >= left && mouseX < right && mouseY >= sy && mouseY < sy + SEARCH_H - 2;
            ctx.fill(left, sy, right, sy + SEARCH_H - 2, GuiStyle.input());
            drawBorder(ctx, left, sy, right - left, SEARCH_H - 2, typingQuery ? GuiStyle.accent() : GuiStyle.border());
            boolean hint = query.isEmpty() && !typingQuery;
            ctx.text(screen.font, hint ? "Search..." : query + (typingQuery ? "_" : ""), left + 5, sy + 5,
                    hint ? GuiStyle.textFaint() : GuiStyle.text());
            if (clicked && overSearch) { screen.clickHandled = true; startTyping(false); }

            int viewY = sy + SEARCH_H;
            int viewH = VIEW_ROWS * ROW;
            int max = Math.max(0, all.size() - VIEW_ROWS);
            if (scroll > max) scroll = max;
            if (all.isEmpty()) ctx.text(screen.font, "Nothing here", left + 5, viewY + 5, GuiStyle.textFaint());
            for (int i = 0; i < VIEW_ROWS && scroll + i < all.size(); i++) {
                String id = all.get(scroll + i);
                boolean on = isOn(id);
                int ry = viewY + i * ROW;
                boolean rh = mouseX >= left && mouseX < right - 6 && mouseY >= ry && mouseY < ry + ROW;
                if (on) ctx.fill(left, ry, right - 6, ry + ROW, GuiStyle.rowSelected());
                else if (rh) ctx.fill(left, ry, right - 6, ry + ROW, GuiStyle.rowHover());
                int ix = left + 2;
                net.minecraft.world.item.ItemStack st = icon == null ? null : icon.apply(id);
                if (st != null && !st.isEmpty()) ctx.item(st, ix, ry + 1);
                else if (icon != null) ctx.fill(ix + 2, ry + 3, ix + 14, ry + 15, 0xFF3A3A3A);
                int tx = icon != null ? ix + 20 : ix + 4;
                int checkX = right - 20;
                int swX = color != null ? checkX - 18 : checkX;
                ctx.text(screen.font, screen.fitLabel(displayName.apply(id), tx, swX), tx, ry + 5, on ? GuiStyle.text() : GuiStyle.textDim());
                if (color != null) {
                    ctx.fill(swX, ry + 4, swX + 12, ry + 14, swatchColor(id));
                    drawBorder(ctx, swX, ry + 4, 12, 10, 0xFF555555);
                }
                drawBorder(ctx, checkX, ry + 4, 10, 10, GuiStyle.accent());
                if (on) ctx.fill(checkX + 2, ry + 6, checkX + 8, ry + 12, GuiStyle.accent());
                if (rh && clicked && !screen.clickHandled) {
                    screen.clickHandled = true;
                    onToggle.accept(id);
                    TeslaMapsConfig.save();
                    invalidateRows();
                    GuiSounds.toggle(!on);
                } else if (rh && screen.rightClickedNow && color != null) {
                    screen.rightClickedNow = false;
                    colorId = id;
                    colorText = String.format("%06X", swatchColor(id) & 0xFFFFFF);
                    startTyping(true);
                    GuiSounds.fold(true);
                }
            }
            if (all.size() > VIEW_ROWS) {
                int thumbH = Math.max(12, viewH * VIEW_ROWS / all.size());
                int thumbY = viewY + (viewH - thumbH) * scroll / max;
                ctx.fill(right - 3, viewY, right - 1, viewY + viewH, GuiStyle.scrollTrack());
                ctx.fill(right - 3, thumbY, right - 1, thumbY + thumbH, GuiStyle.scrollThumb());
            }

            int below = viewY + viewH + 4;
            if (colorId != null) {
                boolean typing = editingList == this && typingColor;
                int prev = colorText.length() == 6 ? TeslaMapsConfig.parseColor(colorText) : 0xFF555555;
                ctx.fill(left, below, left + 14, below + 14, prev);
                String t = displayName.apply(colorId) + ": #" + colorText + (typing ? "_" : "") + "  (Enter = apply)";
                ctx.text(screen.font, screen.fitLabel(t, left + 20, right), left + 20, below + 4,
                        colorText.length() == 6 ? GuiStyle.text() : 0xFFFF5555);
                below += EDIT_H;
            }
            if (!hasActions()) return;
            List<String> labels = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            if (pick != null) {
                labels.add("Pick from World");
                acts.add(() -> {
                    String id = pick.get();
                    if (id != null && !selected.test(id)) { onToggle.accept(id); TeslaMapsConfig.save(); }
                    invalidateRows();
                });
            }
            if (clearAll != null) {
                boolean armed = System.currentTimeMillis() - clearArmed < 3000;
                labels.add(armed ? "Sure? Click again" : "Clear All");
                acts.add(() -> {
                    if (System.currentTimeMillis() - clearArmed < 3000) { clearAll.run(); TeslaMapsConfig.save(); clearArmed = 0; invalidateRows(); }
                    else clearArmed = System.currentTimeMillis();
                });
            }
            int gap = 6, bw = Math.min(150, (right - left - gap * (labels.size() - 1)) / labels.size());
            for (int i = 0; i < labels.size(); i++) {
                int bx = left + i * (bw + gap);
                boolean bh = mouseX >= bx && mouseX < bx + bw && mouseY >= below && mouseY < below + 18;
                if (bh) GuiSounds.hover(actionHover[i]);
                GuiStyle.panel(ctx, bx, below, bw, 18, bh);
                drawBorder(ctx, bx, below, bw, 18, bh ? GuiStyle.borderHover() : GuiStyle.border());
                ctx.centeredText(screen.font, labels.get(i), bx + bw / 2, below + 5, GuiStyle.text());
                if (clicked && bh && !screen.clickHandled) { screen.clickHandled = true; GuiSounds.press(); acts.get(i).run(); }
            }
        }
    }
}
