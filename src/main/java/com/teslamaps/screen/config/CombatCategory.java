package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class CombatCategory {

    private CombatCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Slayer");
        b.info("Slayer kills/bosses live in the Tracker HUD under Skills");
        b.toggle("Only Own Boss", () -> config.slayerOnlyOwnBoss, v -> config.slayerOnlyOwnBoss = v);
        b.info("Your boss always wins; a boss you hit is tracked too");
        b.toggle("Boss ESP", () -> config.slayerBossESP, v -> config.slayerBossESP = v);
        b.toggle("Miniboss ESP", () -> config.slayerMinibossESP, v -> config.slayerMinibossESP = v);
        b.toggle("Miniboss Tracers", () -> config.slayerMinibossTracer, v -> config.slayerMinibossTracer = v);
        b.toggle("Alerts (Miniboss/Cocoon/Boss)", () -> config.slayerAlerts, v -> config.slayerAlerts = v);
        b.label("Slayer Kill Time");
        b.toggle("Kill Time in Chat", () -> config.slayerKillChat, v -> config.slayerKillChat = v);
        b.info("Timed from the scoreboard's \"Slay the boss!\" line to the kill");
        b.toggle("  Show Server Ticks", () -> config.slayerKillTicks, v -> config.slayerKillTicks = v);
        b.toggle("Personal Bests", () -> config.slayerKillPb, v -> config.slayerKillPb = v);
        b.info("One PB per slayer and tier, per profile. /tmap slayerpb lists them");
        b.channel("  Post New PB", () -> config.slayerPbChannel, v -> config.slayerPbChannel = v);
        b.label("Bestiary");
        b.toggle("Bestiary HUD (kills/h and tier ETA)", () -> config.bestiaryTracker, v -> config.bestiaryTracker = v);
        b.info("Reads the Bestiary block in the tab list");
        b.toggle("  Auto Track Non-Maxed",
                () -> config.bestiaryAutoTrack, v -> config.bestiaryAutoTrack = v);
        b.info("Adds every non-maxed mob the tab shows, drops maxed ones");
        b.info("/tmap be add|remove <mob>, /tmap be auto|clear");
        b.info("Shows nothing until a mob was added");
        b.info("Kills to max ship with the mod, the Bestiary menu corrects them");
    }
}
