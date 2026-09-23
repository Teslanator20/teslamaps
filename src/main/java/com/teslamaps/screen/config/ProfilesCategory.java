package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ProfilesCategory {

    private ProfilesCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.info("Click a profile to switch - the current one is saved first");
        b.profileTiles();
        b.buttonRow(
                new String[]{"Save", "Duplicate", "Reset To Preset"},
                new Runnable[]{
                        com.teslamaps.config.ConfigProfiles::saveActive,
                        () -> {
                            com.teslamaps.config.ConfigProfiles.copyToNew(MapConfigScreen.nextProfileName());
                            b.screen().rebuild();
                        },
                        () -> {
                            com.teslamaps.config.ConfigProfiles.reset(com.teslamaps.config.ConfigProfiles.active());
                            b.screen().rebuild();
                        }});
        b.info("PBs, keybinds and your API key are shared by every profile");
        b.info("Presets and the active profile have no x - switch away first");
        // the same three pickers as the Interface category, kept narrow so the controls stay
        // next to their labels instead of at the far edge of a wide window
        ConfigBuilder narrow = b.withWidth(Math.min(b.width(), 430));
        b.info("Look & feel - full set under Interface");
        narrow.dropdown("GUI Style", GuiStyle.NAMES,
                () -> config.guiStyle, v -> config.guiStyle = v);
        narrow.dropdown("HUD Style",
                com.teslamaps.render.gui.HudStyle.NAMES,
                () -> com.teslamaps.render.gui.HudStyle.current(), v -> config.hudStyle = v);
        narrow.dropdown("GUI Sounds", GuiSounds.THEMES,
                () -> config.guiSoundTheme, v -> config.guiSoundTheme = v);
        narrow.slider("GUI Sound Volume", 0f, 2f,
                () -> config.guiSoundVolume, v -> config.guiSoundVolume = v, 2);
        // reaches every vanilla button in the game, so it belongs where profiles are picked
        narrow.toggle("Modern Vanilla Buttons",
                () -> config.modernVanillaButtons, v -> config.modernVanillaButtons = v);
        b.info("Restyles the pause menu and every vanilla button");
    }
}
