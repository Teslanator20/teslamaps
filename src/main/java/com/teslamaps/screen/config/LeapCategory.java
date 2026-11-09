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
        b.dropdown("  Box Style", new String[]{"Default", "Class Color", "Custom"},
                () -> config.leapBoxStyle, v -> config.leapBoxStyle = v).fresh();
        b.color("    Custom Box Color", () -> config.leapBoxColor, v -> config.leapBoxColor = v).fresh();
        b.toggle("  Show Keybind On Box", () -> config.leapKeyHint, v -> config.leapKeyHint = v).fresh();
        b.toggle("  Show Class Level", () -> config.leapClassLevel, v -> config.leapClassLevel = v).fresh();
        b.toggle("  Offline Status + Dead Click Message", () -> config.leapOfflineStatus, v -> config.leapOfflineStatus = v).fresh()
                .desc("Grays out disconnected players and says in chat why a dead/offline box does not leap");
        b.toggle("  Highlight Last Door Opener", () -> config.leapDoorOpenerHighlight, v -> config.leapDoorOpenerHighlight = v).fresh();
        b.color("    Door Opener Color", () -> config.leapDoorOpenerColor, v -> config.leapDoorOpenerColor = v).fresh();
        b.toggle("  Leap On Release", () -> config.leapOnRelease, v -> config.leapOnRelease = v).fresh()
                .desc("Mouse and leap keys fire when released instead of pressed");
        b.toggle("  Fixed Class Quadrants", () -> config.leapStaticQuadrants, v -> config.leapStaticQuadrants = v).fresh()
                .desc("Missing classes leave their quadrant empty, so every class always sits in the same spot");
        b.toggle("Show Map (click to leap)", () -> config.leapShowMap, v -> config.leapShowMap = v);
        b.toggle("  Link Button And Map Head On Hover", () -> config.leapHoverLink, v -> config.leapHoverLink = v).fresh();
        b.toggle("  Map Only After Blood Opens", () -> config.leapMapAfterBlood, v -> config.leapMapAfterBlood = v).fresh()
                .desc("Class boxes until the blood door opens, then the leap menu is just the clickable map");
        b.toggle("  Own Map Scale", () -> config.leapMapCustomScale, v -> config.leapMapCustomScale = v).fresh();
        b.slider("    Leap Map Scale", 0.5f, 3.0f, () -> config.leapMapScale, v -> config.leapMapScale = v).fresh();
        b.toggle("Close Overlay When You Die", () -> config.leapCloseOnDeath, v -> config.leapCloseOnDeath = v).fresh();
        b.slider("Overlay Scale", 0.5f, 2.0f,
                () -> config.leapScale, v -> config.leapScale = v);
        b.toggle("Leap Announce (/pc)", () -> config.leapAnnounce, v -> config.leapAnnounce = v);
        b.button("  Edit Announce Text", () -> Minecraft.getInstance().gui.setScreen(new LeapMessageScreen(Minecraft.getInstance().gui.screen()))).fresh();
        b.dropdown("Sort Mode",
                new String[]{"Odin", "Class A-Z", "Name A-Z", "Custom", "None"},
                () -> config.leapSortMode, v -> config.leapSortMode = v);
        b.button("Edit Custom Order", () -> Minecraft.getInstance().gui.setScreen(new LeapOrderScreen()));
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
