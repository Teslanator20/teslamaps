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
    }

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Style");
        b.dropdown("GUI Style", GuiStyle.NAMES,
                () -> config.guiStyle, v -> config.guiStyle = v);
        b.dropdown("HUD Style",
                com.teslamaps.render.gui.HudStyle.NAMES,
                () -> com.teslamaps.render.gui.HudStyle.current(), v -> config.hudStyle = v);
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
        panelStyles(b, config);
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
        b.label("Performance HUD");
        b.toggle("Performance HUD", () -> config.perfHud, v -> config.perfHud = v);
        b.toggle("  Show FPS", () -> config.perfHudFps, v -> config.perfHudFps = v);
        b.toggle("  Show TPS", () -> config.perfHudTps, v -> config.perfHudTps = v);
        b.toggle("  Show Ping", () -> config.perfHudPing, v -> config.perfHudPing = v);
        b.toggle("  Show CPS", () -> config.perfHudCps, v -> config.perfHudCps = v)
                .desc("Left | right clicks per second");
        b.toggle("  Lag Display (zzz)", () -> config.perfHudLag, v -> config.perfHudLag = v)
                .desc("Adds \"zzz for 1.23s\" while the server has not sent a tick for longer than the threshold")
                .tags("lag", "freeze", "zzz");
        b.slider("    Lag Threshold (ms)", 50f, 1000f, () -> config.perfHudLagThreshold, v -> config.perfHudLagThreshold = v, 0);
        b.toggle("  Show Time", () -> config.perfHudTime, v -> config.perfHudTime = v)
                .desc("Real-world clock (system time)").tags("clock", "time", "uhr");
        b.dropdown("    Time Format", new String[]{"24h", "12h"}, () -> config.perfHudTimeFormat, v -> config.perfHudTimeFormat = v);
        b.toggle("    Seconds", () -> config.perfHudTimeSeconds, v -> config.perfHudTimeSeconds = v);
        b.dropdown("    Time Label", new String[]{"Text", "Symbol", "None"}, () -> config.perfHudTimeLabel, v -> config.perfHudTimeLabel = v);
        b.toggle("  Vertical", () -> config.perfHudVertical, v -> config.perfHudVertical = v);
        b.color("  Label Color", () -> config.perfHudLabelColor, v -> config.perfHudLabelColor = v);
        b.color("  Value Color", () -> config.perfHudValueColor, v -> config.perfHudValueColor = v);
        b.info("Position/scale in /tmap gui, hidden while F3 is open");
        b.label("Profile Viewer");
        b.toggle("Scroll Long Pages", () -> config.pvScrollPages, v -> config.pvScrollPages = v);
        b.info("Full size text with a scrollbar instead of shrinking the page");
        b.label("Updates");
        b.toggle("Check For Updates", () -> config.updateCheck, v -> config.updateCheck = v);
        b.info("Signed releases only, install via popup or /tmap update");
    }
}
