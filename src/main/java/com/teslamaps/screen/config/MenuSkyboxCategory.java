package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class MenuSkyboxCategory {

    private MenuSkyboxCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Main Menu");
        b.toggle("Custom Title Screen",
                () -> config.customTitleScreen, v -> config.customTitleScreen = v);
        b.toggle("Quick Join Button (vanilla menu)",
                () -> config.quickJoinButton, v -> config.quickJoinButton = v)
                .desc("Halves the vanilla Multiplayer button and puts a Join button for the IP below next to it. Resource pack is accepted without the prompt.");
        b.button("  Server IP: " + (config.quickJoinIp == null || config.quickJoinIp.isBlank() ? "mc.hypixel.net" : config.quickJoinIp),
                () -> Minecraft.getInstance().setScreen(new TextPromptScreen("Quick Join IP", ip -> {
                    config.quickJoinIp = ip;
                    TeslaMapsConfig.save();
                    Minecraft.getInstance().setScreen(new MapConfigScreen());
                })))
                .desc("Also used by the Hypixel button of the custom title screen. Empty = mc.hypixel.net.");
        b.toggle("Custom Loading Screen",
                () -> config.customLoadingScreen, v -> config.customLoadingScreen = v);
        b.info("Same background while connecting and loading terrain");
        b.toggle("Modern Vanilla Buttons",
                () -> config.modernVanillaButtons, v -> config.modernVanillaButtons = v);
        b.info("Glass style in the pause menu, options and every plain button");
        b.dropdown("Background",
                com.teslamaps.render.gui.MenuBackgroundRenderer.names(),
                () -> config.titleBackground, v -> config.titleBackground = v);
        b.color("Background Tint",
                () -> config.titleBackgroundColor, v -> config.titleBackgroundColor = v);
        b.slider("Image Dim", 0.0f, 0.9f,
                () -> config.titleImageDim, v -> config.titleImageDim = v, 2);
        b.info("Images: config/teslamaps/backgrounds (png/jpg)");
        b.slider("Animation Speed", 0.05f, 2.0f,
                () -> config.titleBackgroundSpeed, v -> config.titleBackgroundSpeed = v, 2);
        b.toggle("Auto Title Color",
                () -> config.titleAccentAuto, v -> config.titleAccentAuto = v);
        b.color("Title Color",
                () -> config.titleAccentColor, v -> config.titleAccentColor = v);
        b.label("In-World Skybox");
        b.toggle("Custom Skybox",
                () -> config.customSkybox, v -> config.customSkybox = v);
        b.toggle("  Only In Skyblock",
                () -> config.skyboxOnlySkyblock, v -> config.skyboxOnlySkyblock = v);
        b.dropdown("  Skybox",
                com.teslamaps.render.gui.MenuBackgroundRenderer.shaderNames(),
                () -> config.skyboxPreset, v -> config.skyboxPreset = v);
        b.color("  Skybox Tint",
                () -> config.skyboxColor, v -> config.skyboxColor = v);
        b.slider("  Skybox Scale", 0.2f, 3.0f,
                () -> config.skyboxScale, v -> config.skyboxScale = v, 2);
        b.slider("  Skybox Speed", 0.05f, 2.0f,
                () -> config.skyboxSpeed, v -> config.skyboxSpeed = v, 2);
    }
}
