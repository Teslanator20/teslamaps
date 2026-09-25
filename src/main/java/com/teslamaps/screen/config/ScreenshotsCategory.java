package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ScreenshotsCategory {

    private ScreenshotsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Screenshots");
        b.toggle("Auto Copy To Clipboard",
                () -> config.autoCopyScreenshot, v -> config.autoCopyScreenshot = v);
        b.toggle("Chat Actions",
                () -> config.screenshotChatActions, v -> config.screenshotChatActions = v);
        b.info("Clickable buttons under a saved screenshot");
        b.toggle("Hide Vanilla Message",
                () -> config.screenshotHideVanillaMessage, v -> config.screenshotHideVanillaMessage = v);
        b.button("Open Screenshot Browser", () ->
                net.minecraft.client.Minecraft.getInstance().schedule(() ->
                        net.minecraft.client.Minecraft.getInstance().setScreen(new ScreenshotScreen(null))));

        b.label("Shared Images");
        b.toggle("Show Shared Images In Chat",
                () -> config.imageShareReceive, v -> config.imageShareReceive = v);
        b.slider("  Image Height (chat lines)", 3f, 24f,
                () -> (float) config.imageShareChatLines, v -> config.imageShareChatLines = v.intValue());
        b.keybind("  Hold To Enlarge",
                () -> config.imagePeekKey, v -> config.imagePeekKey = v);
        b.info("Hold the key to blow up the newest image");
        b.dropdown("Upload Lifetime",
                new String[]{"1h", "12h", "24h", "72h", "permanent"},
                () -> config.imageShareExpiry, v -> config.imageShareExpiry = v);
        b.toggle("  Remember Uploads", () -> config.imageShareHistory, v -> config.imageShareHistory = v)
                .desc("Copy Ingame reuses the last upload of the same, unchanged screenshot until it expires, and posts a clickable public link.");
    }
}
