package com.teslamaps.screen.nia;

import com.teslamaps.screen.MapConfigScreen;

import java.util.List;
import java.util.Map;

/**
 * The option rows of the classic settings screen, built by an invisible MapConfigScreen.
 * Rows stay bound to this instance, so the few that are drawn by their own render() get it as screen.
 */
final class NiaModel extends MapConfigScreen {

    static final String TURNED_ON = ACTIVE;

    int builds;

    NiaModel(View view) {
        this.view = view;
    }

    @Override
    protected void init() {
        buildCategories();
        builds++;
    }

    Map<String, List<SettingsEntry>> categories() { return categories; }
    Map<String, List<String>> sections() { return sections; }
    Map<String, List<String>> groups() { return groups; }

    String sidebarItemOf(String category) { return groupOf(category); }

    List<SettingsEntry> turnedOn(String category) {
        List<SettingsEntry> l = categories.get(category);
        return l == null ? List.of() : activeEntries(l);
    }

    int reset(List<SettingsEntry> entries) { return resetEntries(entries); }

    void listen(KeybindEntry e) {
        listeningKeybindEntry = e;
        keybindListenStart = System.currentTimeMillis();
    }

    boolean listening(KeybindEntry e) { return listeningKeybindEntry == e; }

    boolean listeningAny() { return listeningKeybindEntry != null; }

    void cancelEdits() {
        listeningKeybindEntry = null;
        editingSlider = null;
        editingColor = null;
        editingList = null;
    }

    void beginFrame(boolean rightClicked) {
        clickHandled = false;
        rightClickedNow = rightClicked;
    }

    boolean clickHandled() { return clickHandled; }

    static final String NEW_OPTIONS = NEW;

    static boolean newMode() { return newKeys != null; }

    List<SettingsEntry> newRows() { return newEntries(); }

    static void finishNew() {
        if (newKeys == null) return;
        newKeys = null;
        com.teslamaps.features.NewOptions.markSeen();
    }

    /** Category and query of a pending jumpTo, consumed once. */
    static String[] takeJump() {
        if (jumpCategory == null && jumpQuery == null) return null;
        String[] r = {jumpCategory, jumpQuery};
        jumpCategory = jumpQuery = null;
        return r;
    }
}
