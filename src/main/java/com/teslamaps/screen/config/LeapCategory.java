package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class LeapCategory {

    private LeapCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.toggle("Enable Leap Overlay", () -> config.leapOverlay, v -> config.leapOverlay = v);
        b.toggle("Show Player Heads", () -> config.leapShowHeads, v -> config.leapShowHeads = v);
        b.toggle("Show Map (click to leap)", () -> config.leapShowMap, v -> config.leapShowMap = v);
        b.slider("Overlay Scale", 0.5f, 2.0f,
                () -> config.leapScale, v -> config.leapScale = v);
        b.toggle("Leap Announce (/pc)", () -> config.leapAnnounce, v -> config.leapAnnounce = v);
        b.dropdown("Sort Mode",
                new String[]{"Odin", "Class A-Z", "Name A-Z", "Custom", "None"},
                () -> config.leapSortMode, v -> config.leapSortMode = v);
        b.button("Edit Custom Order", () -> Minecraft.getInstance().setScreen(new LeapOrderScreen()));
        b.dropdown("Keybind Mode",
                new String[]{"Corners", "Class"}, () -> config.leapKeybindMode, v -> config.leapKeybindMode = v);
        b.label("Corner Keybinds");
        b.keybind("Top Left", () -> config.leapKeyTL, v -> config.leapKeyTL = v);
        b.keybind("Top Right", () -> config.leapKeyTR, v -> config.leapKeyTR = v);
        b.keybind("Bottom Left", () -> config.leapKeyBL, v -> config.leapKeyBL = v);
        b.keybind("Bottom Right", () -> config.leapKeyBR, v -> config.leapKeyBR = v);
        b.label("Class Keybinds");
        b.keybind("Archer", () -> config.leapKeyArcher, v -> config.leapKeyArcher = v);
        b.keybind("Berserk", () -> config.leapKeyBerserk, v -> config.leapKeyBerserk = v);
        b.keybind("Healer", () -> config.leapKeyHealer, v -> config.leapKeyHealer = v);
        b.keybind("Mage", () -> config.leapKeyMage, v -> config.leapKeyMage = v);
        b.keybind("Tank", () -> config.leapKeyTank, v -> config.leapKeyTank = v);
        b.label("Other Keybinds");
        b.keybind("Last Door Opener", () -> config.leapKeyLastDoor, v -> config.leapKeyLastDoor = v);
        b.keybind("Fast Leap (smart)", () -> config.fastLeapKey, v -> config.fastLeapKey = v);
    }
}
