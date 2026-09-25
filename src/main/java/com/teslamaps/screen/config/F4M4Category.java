package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class F4M4Category {

    private F4M4Category() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Highlight (F4/M4)");
        b.toggle("Enable Highlight", () -> config.m4Highlight, v -> config.m4Highlight = v);
        b.dropdown("Style", new String[]{"Filled", "Outline", "Filled Outline"},
                () -> config.m4HighlightStyle, v -> config.m4HighlightStyle = v);
        b.toggle("Hide Nametags", () -> config.m4HideNametags, v -> config.m4HideNametags = v);
        b.toggle("Thorn", () -> config.m4HlThorn, v -> config.m4HlThorn = v);
        b.toggle("  Thorn Damage Flash", () -> config.m4ThornDmgFlash, v -> config.m4ThornDmgFlash = v);
        b.toggle("Bear", () -> config.m4HlBear, v -> config.m4HlBear = v);
        b.toggle("  Bear Only On Mage", () -> config.m4BearOnlyMage, v -> config.m4BearOnlyMage = v);
        b.toggle("Cows", () -> config.m4HlCow, v -> config.m4HlCow = v);
        b.toggle("Sheep", () -> config.m4HlSheep, v -> config.m4HlSheep = v);
        b.toggle("Chickens", () -> config.m4HlChicken, v -> config.m4HlChicken = v);
        b.toggle("Bats", () -> config.m4HlBat, v -> config.m4HlBat = v);
        b.toggle("Wolves", () -> config.m4HlWolf, v -> config.m4HlWolf = v);
        b.toggle("Rabbits", () -> config.m4HlRabbit, v -> config.m4HlRabbit = v);
        b.toggle("Spirit Bow", () -> config.m4HlSpiritBow, v -> config.m4HlSpiritBow = v);
        b.label("Highlight Colors");
        b.color("Thorn", () -> config.m4ColorThorn, v -> config.m4ColorThorn = v);
        b.color("Thorn Damaged", () -> config.m4ColorThornDmg, v -> config.m4ColorThornDmg = v);
        b.color("Bear", () -> config.m4ColorBear, v -> config.m4ColorBear = v);
        b.color("Cow", () -> config.m4ColorCow, v -> config.m4ColorCow = v);
        b.color("Sheep", () -> config.m4ColorSheep, v -> config.m4ColorSheep = v);
        b.color("Chicken", () -> config.m4ColorChicken, v -> config.m4ColorChicken = v);
        b.color("Bat", () -> config.m4ColorBat, v -> config.m4ColorBat = v);
        b.color("Wolf", () -> config.m4ColorWolf, v -> config.m4ColorWolf = v);
        b.color("Rabbit", () -> config.m4ColorRabbit, v -> config.m4ColorRabbit = v);
        b.color("Spirit Bow", () -> config.m4ColorSpiritBow, v -> config.m4ColorSpiritBow = v);
        b.label("Decoy & Trajectories");
        b.toggle("Best Decoy Spot", () -> config.m4Decoy, v -> config.m4Decoy = v);
        b.toggle("Trajectories", () -> config.m4Trajectories, v -> config.m4Trajectories = v);
        b.toggle("  Trajectory: Cows", () -> config.m4TrajCow, v -> config.m4TrajCow = v);
        b.toggle("  Trajectory: Sheep", () -> config.m4TrajSheep, v -> config.m4TrajSheep = v);
        b.toggle("  Trajectory: Chickens", () -> config.m4TrajChicken, v -> config.m4TrajChicken = v);
        b.label("Boss Timers");
        b.toggle("Spirit Bear Timer", () -> config.spiritBearTimer, v -> config.spiritBearTimer = v);
        b.toggle("Thorn Stun Timer", () -> config.thornStunTimer, v -> config.thornStunTimer = v);
        b.toggle("  Thorn Stun: Healer Only", () -> config.thornStunHealerOnly, v -> config.thornStunHealerOnly = v);
        b.toggle("Overkill Counter", () -> config.overkillCounter, v -> config.overkillCounter = v);
        b.toggle("  Overkill: Hide Zero", () -> config.overkillHideZero, v -> config.overkillHideZero = v);
        b.label("Titles");
        b.toggle("Bow Missed Title", () -> config.titleBowMiss, v -> config.titleBowMiss = v);
        b.toggle("Bow Pickup Title", () -> config.titleBowPickup, v -> config.titleBowPickup = v);
        b.toggle("Bear Event Titles", () -> config.titleBearEvents, v -> config.titleBearEvents = v);
        b.toggle("Hide Default Titles (boss)", () -> config.hideDefaultTitles, v -> config.hideDefaultTitles = v);
    }
}
