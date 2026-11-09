package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.screen.MapConfigScreen.SettingsEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MapConfigScreen's option model for alternative config screens: a hidden MapConfigScreen builds the
 * rows, this hands out its categories/sidebar and reads/writes the entries.
 */
public final class ConfigModel {
    public static final String ACTIVE = MapConfigScreen.ACTIVE;
    public static final String NEW = MapConfigScreen.NEW;

    private final MapConfigScreen host;

    public ConfigModel(MapConfigScreen.View view) {
        host = new MapConfigScreen();
        host.view = view;
    }

    /** (Re)builds every row; call from the screen's init(). */
    public void build(int width, int height) {
        host.init(width, height);
    }

    public MapConfigScreen host() { return host; }

    /** Bumps whenever the rows were rebuilt (profile switch, preset, ...). */
    public int buildCount() { return host.buildCount; }

    /** Sidebar sections -> sidebar entries. */
    public Map<String, List<String>> sections() { return Collections.unmodifiableMap(host.sections); }

    /** Sidebar entry -> categories it shows. */
    public Map<String, List<String>> groups() { return Collections.unmodifiableMap(host.groups); }

    public Map<String, List<SettingsEntry>> categories() { return Collections.unmodifiableMap(host.categories); }

    /** Header row of a category (a LabelEntry named after it). */
    public SettingsEntry header(String category) { return host.categoryHeaders.get(category); }

    /**
     * Rows of one sidebar entry, unfolded: a header per category when there are several, only the
     * master while a section is switched off, sub-options of an off toggle left out.
     */
    public List<SettingsEntry> rows(String sidebarEntry) {
        List<SettingsEntry> out = new ArrayList<>();
        if (ACTIVE.equals(sidebarEntry)) {
            for (Map.Entry<String, List<SettingsEntry>> cat : host.categories.entrySet()) {
                List<SettingsEntry> on = host.activeEntries(cat.getValue());
                if (on.isEmpty()) continue;
                SettingsEntry h = header(cat.getKey());
                if (h != null) out.add(h);
                out.addAll(on);
            }
            return out;
        }
        List<String> members = host.groups.getOrDefault(sidebarEntry, List.of(sidebarEntry));
        for (String cat : members) {
            List<SettingsEntry> entries = host.categories.get(cat);
            if (entries == null) continue;
            if (members.size() > 1) {
                SettingsEntry h = header(cat);
                if (h != null) out.add(h);
            }
            if (!entries.isEmpty() && entries.get(0) instanceof MapConfigScreen.ToggleEntry master
                    && master.getLabel().equals("Enable " + cat) && !master.get()) {
                out.add(master);
                continue;
            }
            out.addAll(MapConfigScreen.visibleChildren(entries));
        }
        return out;
    }

    /** Search hits over every category, with a category header above each block. */
    public List<SettingsEntry> search(String query) {
        String q = query.toLowerCase();
        List<SettingsEntry> result = new ArrayList<>();
        for (Map.Entry<String, List<SettingsEntry>> cat : host.categories.entrySet()) {
            int before = result.size();
            boolean carry = false, groupHit = false;
            for (SettingsEntry e : cat.getValue()) {
                if (e.isLabel()) { carry = false; groupHit = MapConfigScreen.searchHit(e, q); continue; }
                boolean child = e.getLabel().startsWith(" ");
                if (groupHit || MapConfigScreen.searchHit(e, q)) {
                    result.add(e);
                    carry = !child;
                } else if (child && carry) {
                    result.add(e);
                } else if (!child) {
                    carry = false;
                }
            }
            SettingsEntry h = header(cat.getKey());
            if (result.size() > before && h != null) result.add(before, h);
        }
        return result;
    }

    public static int indent(SettingsEntry e) {
        String l = e.getLabel();
        int i = 0;
        while (i < l.length() && l.charAt(i) == ' ') i++;
        return i / 2;
    }

    // --- toggles ---
    public static boolean get(MapConfigScreen.ToggleEntry t) { return t.get(); }

    public static void set(MapConfigScreen.ToggleEntry t, boolean v) {
        t.set(v);
        TeslaMapsConfig.save();
    }

    public static boolean isCheat(MapConfigScreen.ToggleEntry t) { return t.cheat; }

    /** Why the toggle is greyed out, null while it is usable. */
    public static String gateReason(MapConfigScreen.ToggleEntry t) {
        if (t.legitGated && com.teslamaps.features.LegitMode.blocksCheats()) return "Disabled by Legit Mode";
        if (t.sectionGate != null && !TeslaMapsConfig.get().section(t.sectionGate)) return "\"" + t.sectionGate + "\" is switched off";
        return null;
    }

    // --- sliders ---
    public static float min(MapConfigScreen.SliderEntry s) { return s.min; }
    public static float max(MapConfigScreen.SliderEntry s) { return s.max; }
    public static int decimals(MapConfigScreen.SliderEntry s) { return s.decimals; }
    public static float get(MapConfigScreen.SliderEntry s) { return (Float) s.readValue(); }

    public static void set(MapConfigScreen.SliderEntry s, float v) {
        s.writeValue(Math.max(s.min, Math.min(s.max, v)));
        TeslaMapsConfig.save();
    }

    // --- dropdowns / sounds / colours (string values) ---
    public static String[] options(MapConfigScreen.DropdownEntry d) { return d.options; }
    public static String[] options(MapConfigScreen.SoundDropdownEntry d) { return d.options; }

    public static String getString(SettingsEntry e) { return String.valueOf(e.readValue()); }

    public static void setString(SettingsEntry e, String v) {
        e.writeValue(v);
        TeslaMapsConfig.save();
    }

    // --- buttons / keybinds ---
    public static void press(MapConfigScreen.ButtonEntry b) { b.action.run(); }
    public static boolean isActive(MapConfigScreen.ButtonEntry b) { return b.active; }
    public static int key(MapConfigScreen.KeybindEntry k) { return k.getter.getAsInt(); }
    public static void setKey(MapConfigScreen.KeybindEntry k, int key) { k.setKey(key); }

    /** Other binds on the same key, null when none. */
    public List<String> conflicts(MapConfigScreen.KeybindEntry k) { return host.conflictsOf(k); }

    // --- rows without a dedicated widget: MapConfigScreen draws them itself ---

    /** Draws the row at (x, y, w) the classic way; true when it consumed the click. */
    public boolean renderClassic(SettingsEntry e, GuiGraphicsExtractor ctx, int x, int w, int y, int mouseX, int mouseY,
                                 boolean clicked, boolean rightClicked, boolean mouseDown) {
        host.clickHandled = false;
        host.rightClickedNow = rightClicked;
        e.reposition(x, w);
        e.render(ctx, host, y, mouseX, mouseY, clicked, mouseDown);
        host.rightClickedNow = false;
        return host.clickHandled;
    }

    /** A classic row (list search, ...) takes the keyboard. */
    public boolean classicTyping() { return host.editingList != null; }

    public void classicKey(KeyEvent e) { host.keyPressed(e); }

    public void classicChar(CharacterEvent e) { host.charTyped(e); }

    public void stopClassicTyping() { host.editingList = null; }
}
