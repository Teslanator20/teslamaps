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
        b.toggle("Wither Shield Timer (W-Impact)", () -> config.witherShieldTimer, v -> config.witherShieldTimer = v);
        b.toggle("Tactical Insertion Timer", () -> config.tacticalInsertionTimer, v -> config.tacticalInsertionTimer = v);
        b.toggle("Invincibility Timer", () -> config.invincibilityTimer, v -> config.invincibilityTimer = v);
        b.toggle("  Announce Invincibility", () -> config.invincibilityAnnounce, v -> config.invincibilityAnnounce = v);
        b.label("Dungeon");
        b.toggle("Spirit Pet Reminder (boss)", () -> config.spiritPetReminder, v -> config.spiritPetReminder = v);
        b.toggle("Secret Tick Timer", () -> config.secretTickTimer, v -> config.secretTickTimer = v);
        b.toggle("Bossbar Health (❤)", () -> config.bossbarHealth, v -> config.bossbarHealth = v);
        b.dropdown("Timer Format",
                new String[]{"Seconds", "Ticks", "Both"}, () -> config.tickTimerFormat, v -> config.tickTimerFormat = v);
        b.label("Kicked");
        b.toggle("Kick Handling", () -> config.kickedTimer, v -> config.kickedTimer = v);
        b.toggle("  Send /pc kicked", () -> config.kickedPartyMessage, v -> config.kickedPartyMessage = v);
        b.toggle("  Center Warning (60s)", () -> config.kickedCenterWarning, v -> config.kickedCenterWarning = v);
    }
}
