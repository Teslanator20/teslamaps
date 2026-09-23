package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class PartyCategory {

    private PartyCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Party Chat & Alerts");
        b.toggle("Party Chat Commands (!8ball etc)", () -> config.chatCommands, v -> config.chatCommands = v);
        b.toggle("Show PBs on Party Finder Join", () -> config.pbOnJoin, v -> config.pbOnJoin = v);
        b.info("Key: /tmap apikey <key> (developer.hypixel.net)");
        b.toggle("Party Duplicate Alert", () -> config.partyDuplicateAlert, v -> config.partyDuplicateAlert = v);
        b.toggle("  Dup: Play Sound", () -> config.partyDuplicateSound, v -> config.partyDuplicateSound = v);
        b.toggle("  Dup: Announce to Party", () -> config.partyDuplicateMessage, v -> config.partyDuplicateMessage = v);
        b.label("Auto Requeue");
        b.toggle("Auto Requeue (dungeon end)", () -> config.autoRequeue, v -> config.autoRequeue = v);
        b.toggle("Requeue on Party \"r\"", () -> config.requeueOnPartyR, v -> config.requeueOnPartyR = v);
        b.slider("Requeue Delay (s)", 0f, 30f,
                () -> (float) config.requeueDelaySeconds, v -> config.requeueDelaySeconds = Math.round(v));
    }
}
