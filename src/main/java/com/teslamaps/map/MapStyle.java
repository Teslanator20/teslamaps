package com.teslamaps.map;

// Devonian DungeonMap SETTING_DOC_STYLE / LEG_STYLE / IMPORT_STYLE / EXPORT_STYLE
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.config.TeslaMapsConfig;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class MapStyle {
    private static final Gson GSON = new Gson();

    private static final String[] FIELDS = {
            "showMapBackground", "showRoomNames", "hideEntranceBloodFairyNames", "showNamesOnlyForPuzzles",
            "roomNamesOnlyWithLeap", "playerNamesOnlyWithLeap", "mapHeadBorder", "roomNameScale", "infoTextScale",
            "showSecretCount", "hideOneSecretCount", "hideSecretsWhenDone", "showCrypts", "showTotalCrypts",
            "showCheckmarks", "showWhiteCheckmarks", "showGreenCheckmarks", "showFailedCheckmarks",
            "mapCheckmarkIcons", "mapCheckmarkStyle", "mapCheckmarkSize", "mapCheckmarkCenter",
            "mapCheckmarkUnopened", "mapCheckmarkHideWithName", "showPlayerMarker", "showSelfMarker",
            "showOtherPlayers", "showPlayerNames", "useHeadsInsteadOfMarkers", "rotatePlayerHeads", "mapGlowArrow",
            "playerHeadScale", "colorBackground", "colorUnexplored", "mapDarkenUndiscovered", "mapDarkenPercent",
            "colorNormal", "colorEntrance", "colorBlood", "colorTrap", "colorPuzzle",
            "colorPuzzleUnexplored", "colorFairy", "colorMiniboss", "mapMimicRoomStyle", "colorMimicRoom",
            "colorDoorNormal", "colorDoorWither", "colorDoorBlood", "colorDoorEntrance", "colorTextUnexplored",
            "colorTextCleared", "colorTextGreen", "colorSecretCount", "colorCheckWhite", "colorCheckGreen",
            "colorCheckFailed", "mapRoomSize", "mapDoorWidth", "mapDoorsRoomColor", "mapTextShadow",
            "mapNameAlign", "mapCheckmarkAlign", "mapBorderStyle", "colorMapBorder", "mapBorderWidth",
            "mapBorderChromaSpeed", "mapSelfVanillaArrow", "mapPlayerNameScale", "mapPlayerClassName",
            "mapPlayerNameColor",
    };

    // room colours are left alone, like Devonian's presets
    private static final Map<String, Object> DOC = Map.ofEntries(
            Map.entry("mapRoomSize", 22f), Map.entry("mapDoorWidth", 8f), Map.entry("playerHeadScale", 1.5f),
            Map.entry("mapPlayerNameScale", 1.5f), Map.entry("roomNameScale", 1.25f),
            Map.entry("mapBorderStyle", "None"), Map.entry("mapDarkenUndiscovered", true),
            Map.entry("mapDarkenPercent", 0.3f), Map.entry("mapNameAlign", "Center"),
            Map.entry("mapTextShadow", "Outline"), Map.entry("playerNamesOnlyWithLeap", false),
            Map.entry("useHeadsInsteadOfMarkers", true), Map.entry("mapSelfVanillaArrow", false),
            Map.entry("showRoomNames", true), Map.entry("showNamesOnlyForPuzzles", true),
            Map.entry("hideEntranceBloodFairyNames", false), Map.entry("mapDoorsRoomColor", true),
            Map.entry("showPlayerNames", true), Map.entry("mapPlayerClassName", true),
            Map.entry("mapPlayerNameColor", "Class"), Map.entry("mapCheckmarkIcons", false),
            Map.entry("showSecretCount", true), Map.entry("mapCheckmarkHideWithName", false),
            Map.entry("hideOneSecretCount", false), Map.entry("colorBackground", "03020101"));

    private static final Map<String, Object> LEG = Map.ofEntries(
            Map.entry("mapRoomSize", 22f), Map.entry("mapDoorWidth", 9f), Map.entry("playerHeadScale", 1.5f),
            Map.entry("mapPlayerNameScale", 1.2f), Map.entry("roomNameScale", 1.1f),
            Map.entry("mapBorderStyle", "None"), Map.entry("mapDarkenUndiscovered", true),
            Map.entry("mapDarkenPercent", 0.39f), Map.entry("mapNameAlign", "Center"),
            Map.entry("mapTextShadow", "Outline"), Map.entry("playerNamesOnlyWithLeap", true),
            Map.entry("useHeadsInsteadOfMarkers", true), Map.entry("mapSelfVanillaArrow", true),
            Map.entry("showRoomNames", true), Map.entry("showNamesOnlyForPuzzles", false),
            Map.entry("hideEntranceBloodFairyNames", true), Map.entry("mapDoorsRoomColor", false),
            Map.entry("showPlayerNames", true), Map.entry("mapPlayerClassName", true),
            Map.entry("mapPlayerNameColor", "Class"), Map.entry("mapCheckmarkIcons", false),
            Map.entry("showSecretCount", true), Map.entry("mapCheckmarkHideWithName", true),
            Map.entry("hideOneSecretCount", true), Map.entry("colorBackground", "2E0ED3B9"));

    private MapStyle() {}

    public static void applyDoc() { apply(DOC, "Doc"); }

    public static void applyLeg() { apply(LEG, "Leg"); }

    public static void applyDefault() {
        TeslaMapsConfig def = new TeslaMapsConfig();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        for (String name : FIELDS) {
            try {
                Field f = TeslaMapsConfig.class.getDeclaredField(name);
                f.set(c, f.get(def));
            } catch (ReflectiveOperationException ignored) {}
        }
        TeslaMapsConfig.save();
        msg("§aMap style reset to default");
    }

    private static void apply(Map<String, Object> values, String name) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        values.forEach((k, v) -> {
            try {
                TeslaMapsConfig.class.getDeclaredField(k).set(c, v);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
        });
        TeslaMapsConfig.save();
        msg("§aApplied " + name + " map style");
    }

    public static void exportToClipboard() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        JsonObject o = new JsonObject();
        for (String name : FIELDS) {
            try {
                Field f = TeslaMapsConfig.class.getDeclaredField(name);
                o.add(name, GSON.toJsonTree(f.get(c), f.getGenericType()));
            } catch (ReflectiveOperationException ignored) {}
        }
        String s = Base64.getEncoder().encodeToString(GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
        msg("§aMap style copied to clipboard");
    }

    public static void importFromClipboard() {
        String s = Minecraft.getInstance().keyboardHandler.getClipboard();
        JsonObject o;
        try {
            o = JsonParser.parseString(new String(Base64.getDecoder().decode(s.trim()), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            msg("§cClipboard holds no map style");
            return;
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int n = 0;
        for (String name : FIELDS) {
            JsonElement el = o.get(name);
            if (el == null) continue;
            try {
                Field f = TeslaMapsConfig.class.getDeclaredField(name);
                Object v = GSON.fromJson(el, f.getGenericType());
                if (v != null) { f.set(c, v); n++; }
            } catch (Exception ignored) {}
        }
        TeslaMapsConfig.save();
        msg(n > 0 ? "§aImported map style (" + n + " settings)" : "§cClipboard holds no map style");
    }

    private static void msg(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
