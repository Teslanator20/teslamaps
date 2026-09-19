package com.teslamaps.features.invbuttons;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Button layouts: the bundled "Empty" one plus whatever the user saved in the editor.
 * Ported from GanKura.
 */
public final class ButtonPresets {

    public static final String BUILT_IN_NAME = "Empty";

    /** More than this no longer fits the preset column. */
    public static final int MAX_SAVED = 12;

    private static final Gson GSON = new Gson();

    private static LinkedHashMap<String, List<InventoryButton>> builtIn = null;

    private ButtonPresets() {}

    public static LinkedHashMap<String, List<InventoryButton>> presets() {
        LinkedHashMap<String, List<InventoryButton>> presets = new LinkedHashMap<>(builtIn());
        presets.putAll(saved());
        return presets;
    }

    public static LinkedHashMap<String, List<InventoryButton>> saved() {
        return TeslaMapsConfig.get().savedButtonPresets;
    }

    public static boolean isBuiltIn(String name) {
        return builtIn().containsKey(name);
    }

    /** Returns why it could not be saved, or null on success. */
    public static String save(String name, List<InventoryButton> buttons) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "Enter a name first.";
        if (isBuiltIn(trimmed)) return "That name is reserved.";

        LinkedHashMap<String, List<InventoryButton>> saved = saved();
        if (!saved.containsKey(trimmed) && saved.size() >= MAX_SAVED) {
            return "No room left. Delete one first.";
        }

        saved.put(trimmed, copyOf(buttons));
        TeslaMapsConfig.save();
        return null;
    }

    public static boolean delete(String name) {
        if (isBuiltIn(name) || saved().remove(name) == null) return false;
        TeslaMapsConfig.save();
        return true;
    }

    public static String nextName() {
        for (int i = 1; ; i++) {
            String name = "Preset " + i;
            if (!saved().containsKey(name)) return name;
        }
    }

    private static LinkedHashMap<String, List<InventoryButton>> builtIn() {
        if (builtIn != null) return builtIn;

        builtIn = new LinkedHashMap<>();
        try (InputStream stream = ButtonPresets.class
                .getResourceAsStream("/assets/teslamaps/invbuttons/presets.json")) {
            if (stream != null) {
                JsonObject json = GSON.fromJson(
                        new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        builtIn.put(entry.getKey(), readButtons(entry.getValue().getAsJsonArray()));
                    }
                }
            }
        } catch (Exception ignored) {}
        return builtIn;
    }

    private static List<InventoryButton> readButtons(JsonArray array) {
        List<InventoryButton> buttons = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            InventoryButton button = GSON.fromJson(element, InventoryButton.class);
            if (button != null) buttons.add(button);
        }
        return buttons;
    }

    // one border outside the crafting grid, which sits at slot (98,18)
    private static final int CRAFTING_COLUMN = 97;
    private static final int CRAFTING_ROW = 17;

    /**
     * Free slots only - every one has an empty command, so nothing shows up
     * until a button is given one in the editor.
     */
    public static List<InventoryButton> defaults() {
        List<InventoryButton> buttons = new ArrayList<>();

        // over the 2x2 crafting grid
        buttons.add(empty(CRAFTING_COLUMN, CRAFTING_ROW, true, false, false));
        buttons.add(empty(CRAFTING_COLUMN + 18, CRAFTING_ROW, true, false, false));
        buttons.add(empty(CRAFTING_COLUMN, CRAFTING_ROW + 18, true, false, false));
        buttons.add(empty(CRAFTING_COLUMN + 18, CRAFTING_ROW + 18, true, false, false));
        // the crafting result slot
        buttons.add(empty(153, 27, true, false, false));

        // around the screen; the lower half anchors to the bottom so tall screens look the same
        for (int i = 0; i < 8; i++) {
            int y = 2 + 20 * i;
            boolean bottom = y >= 80;
            buttons.add(empty(2, bottom ? y - 166 : y, false, true, bottom));
        }
        for (int i = 0; i < 8; i++) buttons.add(empty(4 + 21 * i, -19, false, false, false));
        for (int i = 0; i < 8; i++) {
            int y = 2 + 20 * i;
            boolean bottom = y >= 80;
            buttons.add(empty(-19, bottom ? y - 166 : y, false, false, bottom));
        }
        for (int i = 0; i < 8; i++) buttons.add(empty(4 + 21 * i, 2, false, false, true));

        return buttons;
    }

    private static InventoryButton empty(int x, int y, boolean playerInvOnly,
                                         boolean anchorRight, boolean anchorBottom) {
        return new InventoryButton(x, y, "", playerInvOnly, anchorRight, anchorBottom, 0, "");
    }

    /** Presets are reused, so hand out copies - editing must not touch the source. */
    public static List<InventoryButton> copyOf(List<InventoryButton> buttons) {
        List<InventoryButton> copy = new ArrayList<>();
        for (InventoryButton button : buttons) {
            if (button != null) copy.add(button.copy());
        }
        return copy;
    }
}
