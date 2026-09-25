package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class TimersCategory {

    private TimersCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.info("All timers share one movable HUD block (/tmap gui).");
        b.label("Cooldowns");
        b.toggle("Warp Cooldown", () -> config.warpCooldown, v -> config.warpCooldown = v);
        b.toggle("  Per Player", () -> config.warpCooldownPerPlayer, v -> config.warpCooldownPerPlayer = v);
        b.toggle("  Only Own Cooldown", () -> config.warpCooldownOwnOnly, v -> config.warpCooldownOwnOnly = v);
        b.toggle("  Sound When Ready", () -> config.warpCooldownSound, v -> config.warpCooldownSound = v);
        b.slider("    Warp Sound Volume", 0f, 5f, () -> config.warpCooldownVolume, v -> config.warpCooldownVolume = v);
        b.toggle("  Block /joininstance", () -> config.warpCooldownBlockJoin, v -> config.warpCooldownBlockJoin = v)
                .desc("Swallows /joininstance and /instancerequeue while your cooldown runs, also from shortcuts and auto requeue.");
        b.toggle("  \"Cooldown Over\" Message", () -> config.warpCooldownEndMessage, v -> config.warpCooldownEndMessage = v);
        b.toggle("Wither Shield Timer (W-Impact)", () -> config.witherShieldTimer, v -> config.witherShieldTimer = v);
        b.toggle("  Shield HUD", () -> config.witherShieldHud, v -> config.witherShieldHud = v);
        b.toggle("    Hide When Ready", () -> config.witherShieldHideReady, v -> config.witherShieldHideReady = v);
        b.toggle("    Compact", () -> config.witherShieldCompact, v -> config.witherShieldCompact = v);
        b.toggle("  Chime When Ready", () -> config.witherShieldChime, v -> config.witherShieldChime = v);
        b.toggle("Tactical Insertion Timer", () -> config.tacticalInsertionTimer, v -> config.tacticalInsertionTimer = v);
        b.toggle("  Tactical HUD", () -> config.tacticalHud, v -> config.tacticalHud = v);
        b.toggle("  Return Waypoint", () -> config.tacticalWaypoint, v -> config.tacticalWaypoint = v);
        b.espStyle("  Tactical Waypoint", () -> config.tacticalWaypointEsp);
        b.toggle("Invincibility Timer", () -> config.invincibilityTimer, v -> config.invincibilityTimer = v)
                .desc("Shows the cooldowns of Bonzo Mask, Spirit Mask and Phoenix Pet after they saved you.")
                .tags("mask", "bonzo", "spirit", "phoenix");
        b.toggle("  Announce Invincibility", () -> config.invincibilityAnnounce, v -> config.invincibilityAnnounce = v);
        b.toggle("  Announce Counter (n/3)", () -> config.invincibilityAnnounceCount, v -> config.invincibilityAnnounceCount = v);
        b.toggle("  Proc Title", () -> config.invincibilityTitle, v -> config.invincibilityTitle = v);
        b.dropdown("  Show", new String[]{"Default", "Always", "Any", "When Active", "On Cooldown"},
                () -> config.invincibilityShow, v -> config.invincibilityShow = v);
        b.toggle("  Hide Bonzo", () -> config.invincibilityHideBonzo, v -> config.invincibilityHideBonzo = v);
        b.toggle("  Hide Spirit", () -> config.invincibilityHideSpirit, v -> config.invincibilityHideSpirit = v);
        b.toggle("  Hide Phoenix", () -> config.invincibilityHidePhoenix, v -> config.invincibilityHidePhoenix = v);
        b.toggle("  Only In Dungeons", () -> config.invincibilityOnlyDungeon, v -> config.invincibilityOnlyDungeon = v);
        b.toggle("  Only In Boss", () -> config.invincibilityOnlyBoss, v -> config.invincibilityOnlyBoss = v);
        b.toggle("  Equipped Mask Marker", () -> config.invincibilityEquippedMarker, v -> config.invincibilityEquippedMarker = v);
        b.color("    Marker Color", () -> config.invincibilityEquippedColor, v -> config.invincibilityEquippedColor = v);
        b.toggle("  Cooldown On Mask Items", () -> config.invincibilityItemOverlay, v -> config.invincibilityItemOverlay = v);
        b.toggle("    As Durability Bar", () -> config.invincibilityItemDurability, v -> config.invincibilityItemDurability = v);
        b.toggle("    Phoenix On First Slot", () -> config.invincibilityPhoenixSlot, v -> config.invincibilityPhoenixSlot = v);
        b.color("    Item Cooldown Color", () -> config.invincibilityItemColor, v -> config.invincibilityItemColor = v);
        b.label("Dungeon");
        b.toggle("Spirit Pet Reminder (boss)", () -> config.spiritPetReminder, v -> config.spiritPetReminder = v);
        b.toggle("Secret Tick Timer", () -> config.secretTickTimer, v -> config.secretTickTimer = v);
        b.toggle("Death Tick Timer (run start)", () -> config.deathTickTimer, v -> config.deathTickTimer = v);
        b.toggle("  Show During Clear", () -> config.deathTickShowClear, v -> config.deathTickShowClear = v)
                .desc("Keep the 40-tick death cycle up for the whole clear, not only at the start.");
        b.toggle("Livid Invuln Timer (F5/M5)", () -> config.lividTimerHud, v -> config.lividTimerHud = v);
        b.toggle("  Ice Spray Alert", () -> config.lividIceSprayAlert, v -> config.lividIceSprayAlert = v);
        b.toggle("Fire Freeze Timer (F3/M3)", () -> config.fireFreezeTimer, v -> config.fireFreezeTimer = v);
        b.toggle("  Anvil Sound on NOW", () -> config.fireFreezeSound, v -> config.fireFreezeSound = v);
        b.toggle("Guardian HP Labels (F3/M3)", () -> config.guardianHealth, v -> config.guardianHealth = v);
        b.toggle("Bossbar Health (❤)", () -> config.bossbarHealth, v -> config.bossbarHealth = v);
        b.toggle("  Percent for All Other Bosses", () -> config.bossbarHealthPercent, v -> config.bossbarHealthPercent = v)
                .desc("Bosses without a known max HP (and Kuudra) get \" - 25.0%\" on their bar.");
        b.toggle("  Sadan's Interest Countdown (F6/M6)", () -> config.sadanInterest, v -> config.sadanInterest = v)
                .desc("Turns the interest bar into the real time left of the terracotta phase.");
        b.toggle("Scarf Spawn Timer (F2/M2)", () -> config.scarfSpawnTimer, v -> config.scarfSpawnTimer = v)
                .desc("Timer line until Scarf's undeads (7.75s) and Scarf himself (10s) spawn.");
        b.dropdown("Timer Format",
                new String[]{"Seconds", "Ticks", "Both"}, () -> config.tickTimerFormat, v -> config.tickTimerFormat = v);
        b.toggle("  Hide Timer Prefix", () -> config.tickTimerNoPrefix, v -> config.tickTimerNoPrefix = v);
        b.toggle("  Hide Timer Suffix (s/t)", () -> config.tickTimerNoSuffix, v -> config.tickTimerNoSuffix = v);
        b.label("Kicked");
        b.toggle("Kick Handling", () -> config.kickedTimer, v -> config.kickedTimer = v);
        b.toggle("  Send /pc kicked", () -> config.kickedPartyMessage, v -> config.kickedPartyMessage = v);
        b.toggle("  Center Warning", () -> config.kickedCenterWarning, v -> config.kickedCenterWarning = v);
        b.slider("  Wait Time (s)", 10f, 180f, () -> (float) config.kickedWaitSeconds, v -> config.kickedWaitSeconds = Math.round(v), 0);
        b.toggle("  Rejoin Title", () -> config.kickedTitle, v -> config.kickedTitle = v);
    }
}
