package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class InterfaceCategory {

    private InterfaceCategory() {}

    private static String pickedPanel = null;

    /** One HUD panel picked from a list, then its own HUD Style ("Default" = the global one). */
    private static void panelStyles(ConfigBuilder b, TeslaMapsConfig config) {
        java.util.List<String> ids = com.teslamaps.registry.FeatureRegistry.hudIds();
        if (ids.isEmpty()) return;
        java.util.Map<String, String> byLabel = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            var e = com.teslamaps.registry.HudEditElement.forHud(id);
            byLabel.putIfAbsent(e != null ? e.label : id, id);
        }
        String[] labels = byLabel.keySet().toArray(new String[0]);
        if (pickedPanel == null || !byLabel.containsKey(pickedPanel)) pickedPanel = labels[0];
        String[] styles = new String[com.teslamaps.render.gui.HudStyle.NAMES.length + 1];
        styles[0] = "Default";
        System.arraycopy(com.teslamaps.render.gui.HudStyle.NAMES, 0, styles, 1, styles.length - 1);
        b.dropdown("Per-Panel Style: Panel", labels, () -> pickedPanel, v -> pickedPanel = v)
                .desc("Pick a HUD panel, then give it its own style below.").tags("panel", "hud style");
        b.dropdown("  Style Of That Panel", styles,
                () -> config.hudPanelStyles.getOrDefault(byLabel.get(pickedPanel), "Default"),
                v -> {
                    String id = byLabel.get(pickedPanel);
                    if ("Default".equals(v)) config.hudPanelStyles.remove(id);
                    else config.hudPanelStyles.put(id, v);
                }).tags("panel", "hud style");
        b.dropdown("  Text Chroma Of That Panel", new String[]{"Default", "Off", "Plain Text", "All Text"},
                () -> config.hudPanelChroma.getOrDefault(byLabel.get(pickedPanel), "Default"),
                v -> {
                    String id = byLabel.get(pickedPanel);
                    if ("Default".equals(v)) config.hudPanelChroma.remove(id);
                    else config.hudPanelChroma.put(id, v);
                }).desc("Rainbow text for this panel. Plain Text leaves coloured parts alone. Speed/size: Tooltips & Value chroma settings.")
                .tags("chroma", "rainbow", "panel");
        perPanel(b, "  Text Of That Panel", com.teslamaps.render.gui.HudTextStyle.SHADOWS, config.hudPanelTextShadow, byLabel,
                "Shadow, a 1 px black outline, or plain text. Default = HUD Text above.");
        perPanel(b, "  Text Align Of That Panel", com.teslamaps.render.gui.HudTextStyle.ALIGNS, config.hudPanelTextAlign, byLabel,
                "Lines of that panel left, centered or right aligned to its widest line.");
        perPanel(b, "  Line Backdrop Of That Panel", com.teslamaps.render.gui.HudTextStyle.BACKDROPS, config.hudPanelLineBackdrop, byLabel,
                "Dark bar behind every text line of that panel.");
    }

    private static void perPanel(ConfigBuilder b, String label, String[] names, java.util.Map<String, String> map,
                                 java.util.Map<String, String> byLabel, String desc) {
        String[] opts = new String[names.length + 1];
        opts[0] = "Default";
        System.arraycopy(names, 0, opts, 1, names.length);
        b.dropdown(label, opts, () -> map.getOrDefault(byLabel.get(pickedPanel), "Default"), v -> {
            String id = byLabel.get(pickedPanel);
            if ("Default".equals(v)) map.remove(id);
            else map.put(id, v);
        }).desc(desc).tags("panel", "text", "outline", "align");
    }

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Style");
        b.dropdown("GUI Style", GuiStyle.NAMES,
                () -> config.guiStyle, v -> config.guiStyle = v);
        b.dropdown("HUD Style",
                com.teslamaps.render.gui.HudStyle.NAMES,
                () -> com.teslamaps.render.gui.HudStyle.current(), v -> config.hudStyle = v);
        b.toggle("  Custom Accent Color", () -> config.guiCustomAccent, v -> config.guiCustomAccent = v)
                .desc("Replaces the style's accent (selection, active headers, toggles) with your own colour.")
                .tags("theme", "accent", "color");
        b.color("    Accent", () -> config.guiAccentColor, v -> config.guiAccentColor = v).tags("theme", "accent");
        b.info("GUI Style skins these screens, HUD Style the in-game panels");
        b.color("  Custom Background", () -> config.hudCustomBg, v -> config.hudCustomBg = v)
                .desc("Fill of HUD Style \"Custom\", alpha included.").tags("panel", "hud style");
        b.color("  Custom Border", () -> config.hudCustomBorder, v -> config.hudCustomBorder = v).tags("panel", "hud style");
        b.dropdown("  Custom Border Mode", com.teslamaps.render.gui.HudStyle.BORDER_MODES,
                () -> config.hudCustomBorderMode, v -> config.hudCustomBorderMode = v)
                .desc("Solid, cycling chroma, or a fade between Border and Fade Color.").tags("chroma", "panel");
        b.color("  Custom Fade Color", () -> config.hudCustomBorderFade, v -> config.hudCustomBorderFade = v).tags("panel");
        b.slider("  Custom Corner Radius", 0f, 12f, () -> config.hudCustomRadius, v -> config.hudCustomRadius = v, 0).tags("panel");
        b.slider("  Custom Border Width", 0f, 4f, () -> config.hudCustomBorderWidth, v -> config.hudCustomBorderWidth = v, 1).tags("panel");
        b.slider("  Custom Padding", 0f, 12f, () -> config.hudCustomPadding, v -> config.hudCustomPadding = v, 0).tags("panel");
        b.dropdown("  Text Chroma (All Panels)", com.teslamaps.render.gui.HudStyle.CHROMA_MODES,
                () -> config.hudChromaAll, v -> config.hudChromaAll = v)
                .desc("Rainbow text on every HUD panel. Plain Text leaves coloured parts alone.").tags("chroma", "rainbow");
        b.dropdown("  HUD Text", com.teslamaps.render.gui.HudTextStyle.SHADOWS,
                () -> config.hudTextShadow, v -> config.hudTextShadow = v)
                .desc("Text of every HUD panel: vanilla shadow, a 1 px black outline (readable on bright ground), or no shadow.")
                .tags("outline", "text", "shadow");
        b.slider("  Custom Blur", 0f, 20f, () -> config.hudCustomBlur, v -> config.hudCustomBlur = v, 0)
                .desc("Blurs the world behind \"Custom\" panels. 0 = off.").tags("panel", "blur", "frosted");
        b.dropdown("    Blur Kernel", new String[]{"Gaussian", "Box"},
                () -> config.hudCustomBlurKernel, v -> config.hudCustomBlurKernel = v).tags("panel", "blur");
        panelStyles(b, config);
        b.label("Layout");
        b.toggle("Column Layout", () -> config.configColumnLayout, v -> config.configColumnLayout = v)
                .desc("Opens settings as movable columns, one per category: drag a header, scroll while dragging to scale, right-click to fold. Takes effect the next time the settings open.")
                .tags("clickgui", "columns", "odin", "layout");
        b.toggle("  Fling Columns", () -> config.configColumnFling, v -> config.configColumnFling = v)
                .desc("A column let go mid-drag keeps sliding and bounces off the screen edges.");
        b.label("HUD Positions");
        b.toggle("Keep HUDs On Screen", () -> config.hudClampToScreen, v -> config.hudClampToScreen = v)
                .desc("A panel that would end up off screen (smaller GUI scale, terminal scale, window resize) is drawn at the edge. The saved position stays.")
                .tags("clamp", "hud", "position", "offscreen");
        b.toggle("Anchor HUDs To Nearest Edge", () -> config.hudEdgeAnchors, v -> {
                    config.hudEdgeAnchors = v;
                    if (v) com.teslamaps.registry.HudEditElement.anchorUnplaced();
                })
                .desc("Panels in the right or bottom half keep their distance to that edge when the screen size or GUI scale changes.")
                .tags("anchor", "hud", "position", "gui scale");
        b.toggle("HUD Editor Helpers", () -> config.hudEditHelpers, v -> config.hudEditHelpers = v)
                .desc("/tmap gui: grid, edge snap, centre guides with coordinates, Ctrl+click multi-drag, corner resize grip. Hold Alt to drag freely.")
                .tags("hud", "grid", "snap", "resize", "editor");
        b.slider("  Grid Size (0 = off)", 0f, 50f, () -> (float) config.hudEditGrid, v -> config.hudEditGrid = Math.round(v), 0);
        b.toggle("  Snap To Other Panels", () -> config.hudEditSnapEdges, v -> config.hudEditSnapEdges = v);
        b.toggle("  Centre Guides", () -> config.hudEditGuides, v -> config.hudEditGuides = v);
        b.toggle("  Ctrl+Click Multi-Drag", () -> config.hudEditMultiDrag, v -> config.hudEditMultiDrag = v);
        b.toggle("  Resize Grip", () -> config.hudEditResize, v -> config.hudEditResize = v);
        b.toggle("HUD Editor Extras", () -> config.hudEditExtras, v -> config.hudEditExtras = v)
                .desc("More controls in /tmap gui. Hover an element to see its position and the keys.")
                .tags("hud", "editor", "reset", "nudge", "tmap gui");
        b.toggle("  Reset (R Key + Reset All)", () -> config.hudEditReset, v -> config.hudEditReset = v)
                .desc("R puts the hovered or last clicked element back to its default position and scale. Reset All asks for a second click.");
        b.toggle("  Arrow Keys Move, +/- Scale", () -> config.hudEditNudge, v -> config.hudEditNudge = v)
                .desc("1 px per press, 10 px with Shift. +/- (numpad too) change the scale by 0.1.");
        b.toggle("  Show Disabled HUDs", () -> config.hudEditShowDisabled, v -> config.hudEditShowDisabled = v)
                .desc("Switched-off elements are drawn as grey boxes, so you can place them anyway.");
        b.toggle("    Right-Click Toggles HUD", () -> config.hudEditRightClickToggle, v -> config.hudEditRightClickToggle = v)
                .desc("Right-click an element to switch its feature on or off.");
        b.toggle("  Middle-Click Opens Settings", () -> config.hudEditJump, v -> config.hudEditJump = v)
                .desc("Middle-click an element to jump to its settings.");
        b.label("Outside SkyBlock");
        b.toggle("Only Run Features In SkyBlock", () -> config.outsideSbGate, v -> config.outsideSbGate = v)
                .desc("Off = unchanged. On = every feature stops outside Hypixel SkyBlock, except the ones picked below.")
                .tags("skyblock", "outside", "lobby");
        b.searchList("  Also Outside SkyBlock", com.teslamaps.registry.FeatureRegistry::featureNames,
                id -> config.outsideSbFeatures.contains(id),
                id -> { if (!config.outsideSbFeatures.remove(id)) config.outsideSbFeatures.add(id); })
                .tags("skyblock", "outside");
        b.label("Sounds");
        b.dropdown("GUI Sounds", GuiSounds.THEMES,
                () -> config.guiSoundTheme, v -> config.guiSoundTheme = v);
        b.toggle("Hover Ticks", () -> config.guiHoverSounds, v -> config.guiHoverSounds = v);
        b.slider("Volume", 0f, 2f,
                () -> config.guiSoundVolume, v -> config.guiSoundVolume = v, 2);
        b.label("Config Hotkey");
        b.keybind("Open This Screen",
                () -> config.configScreenKey, v -> config.configScreenKey = v);
        b.toggle("  Block Other Mods",
                () -> config.configScreenKeyExclusive, v -> config.configScreenKeyExclusive = v);
        b.info("Right Shift by default, press again to close");
        b.info("Block Other Mods eats the key before anything else sees it");
        b.toggle("  Key Conflict Warning", () -> config.keyConflictWarning, v -> config.keyConflictWarning = v)
                .desc("Key buttons turn red when the same key is also bound elsewhere in TeslaMaps, in a hotkey or in the vanilla/other mods' controls. Hover the button to see which.")
                .tags("keybind", "conflict", "duplicate");
        b.toggle("  Quick Mod Menu Switch", () -> config.quickModSwitch, v -> config.quickModSwitch = v)
                .desc("Inside the config screens of known mods (TeslaMaps, Skyblocker, SkyHanni, Devonian, Odin, SkyOcean, Firmament) a list of the other installed ones; a click opens their menu. Position in /tmap gui.")
                .tags("mod menu", "switch");
        b.toggle("    Also In Pause Menu", () -> config.quickModSwitchPause, v -> config.quickModSwitchPause = v);
        com.teslamaps.features.FeatureToggleKeys.config(b, config);
        b.label("Performance HUD");
        b.toggle("Performance HUD", () -> config.perfHud, v -> config.perfHud = v);
        b.toggle("  Show FPS", () -> config.perfHudFps, v -> config.perfHudFps = v);
        b.toggle("  Show TPS", () -> config.perfHudTps, v -> config.perfHudTps = v);
        b.toggle("  Show Ping", () -> config.perfHudPing, v -> config.perfHudPing = v);
        b.toggle("  Show CPS", () -> config.perfHudCps, v -> config.perfHudCps = v)
                .desc("Left | right clicks per second");
        b.toggle("  Show Lobby Day", () -> config.perfHudDay, v -> config.perfHudDay = v)
                .desc("Day: n = how long this lobby has been running (world time / 24000). Fresh lobbies lag less.");
        b.toggle("  Lag Display (zzz)", () -> config.perfHudLag, v -> config.perfHudLag = v)
                .desc("Adds \"zzz for 1.23s\" while the server has not sent a tick for longer than the threshold")
                .tags("lag", "freeze", "zzz");
        b.slider("    Lag Threshold (ms)", 50f, 1000f, () -> config.perfHudLagThreshold, v -> config.perfHudLagThreshold = v, 0);
        b.toggle("  Show Coordinates", () -> config.perfHudCoords, v -> config.perfHudCoords = v)
                .desc("x / y / z (yaw / pitch) of your position")
                .tags("coords", "xyz", "position");
        b.toggle("    Looking At", () -> config.perfHudLookingAt, v -> config.perfHudLookingAt = v)
                .desc("Adds \"Looking at: x / y / z\" for the block in your crosshair");
        b.toggle("  Show Time", () -> config.perfHudTime, v -> config.perfHudTime = v)
                .desc("Real-world clock (system time)").tags("clock", "time", "uhr");
        b.dropdown("    Time Format", new String[]{"24h", "12h"}, () -> config.perfHudTimeFormat, v -> config.perfHudTimeFormat = v);
        b.toggle("    Seconds", () -> config.perfHudTimeSeconds, v -> config.perfHudTimeSeconds = v);
        b.dropdown("    Time Label", new String[]{"Text", "Symbol", "None"}, () -> config.perfHudTimeLabel, v -> config.perfHudTimeLabel = v);
        b.toggle("  Vertical", () -> config.perfHudVertical, v -> config.perfHudVertical = v);
        b.lineOrder("  Order", com.teslamaps.features.PerformanceHud.LINES.id())
                .desc("Drag entries to change their order, click the box to hide one.");
        b.toggle("  Color By Value", () -> config.perfHudColorCoded, v -> config.perfHudColorCoded = v)
                .desc("FPS relative to your FPS limit, TPS and ping green to red");
        b.dropdown("  TPS Value", new String[]{"Average", "Current", "Min", "Max"},
                () -> config.perfHudTpsMode, v -> config.perfHudTpsMode = v);
        b.dropdown("  Ping Value", new String[]{"Instant", "Median", "Mean"},
                () -> config.perfHudPingMode, v -> config.perfHudPingMode = v);
        b.color("  Label Color", () -> config.perfHudLabelColor, v -> config.perfHudLabelColor = v);
        b.color("  Value Color", () -> config.perfHudValueColor, v -> config.perfHudValueColor = v);
        b.info("Position/scale in /tmap gui, hidden while F3 is open");
        b.label("Profile Viewer");
        b.toggle("Scroll Long Pages", () -> config.pvScrollPages, v -> config.pvScrollPages = v);
        b.info("Full size text with a scrollbar instead of shrinking the page");
        b.label("Updates");
        b.toggle("Check For Updates", () -> config.updateCheck, v -> config.updateCheck = v);
        b.toggle("  Show Changelog", () -> config.updateChangelog, v -> config.updateChangelog = v);
        b.info("Signed releases only, install via popup or /tmap update");
    }
}
