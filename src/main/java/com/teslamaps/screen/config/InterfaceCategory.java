package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class InterfaceCategory {

    private InterfaceCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Style");
        b.dropdown("GUI Style", GuiStyle.NAMES,
                () -> config.guiStyle, v -> config.guiStyle = v);
        b.dropdown("HUD Style",
                com.teslamaps.render.gui.HudStyle.NAMES,
                () -> com.teslamaps.render.gui.HudStyle.current(), v -> config.hudStyle = v);
        b.info("GUI Style skins these screens, HUD Style the in-game panels");
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
