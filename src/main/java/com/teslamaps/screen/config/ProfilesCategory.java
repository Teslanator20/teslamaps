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
        b.button("Share Settings With a Friend", com.teslamaps.config.ConfigShare::share)
                .desc("Uploads this profile and gives you a code. Your friend runs /tmap config import <code>. PBs, keybinds, cape and API keys stay with you.");
        b.info("PBs, keybinds and your API key are shared by every profile");
        b.info("Presets and the active profile have no x - switch away first");
    }
}
