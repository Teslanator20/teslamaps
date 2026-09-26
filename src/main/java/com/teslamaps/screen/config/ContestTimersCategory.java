package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ContestTimersCategory {

    private ContestTimersCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.info("Foraging: :15 / :35 / :55, Farming: hourly at :15");
        b.toggle("Contest Timer HUD",
                () -> config.contestTimer, v -> config.contestTimer = v);
        b.toggle("  Foraging Timer",
                () -> config.contestShowForaging, v -> config.contestShowForaging = v);
        b.toggle("  Farming Timer",
                () -> config.contestShowFarming, v -> config.contestShowFarming = v);
        b.toggle("    Contest Crops",
                () -> config.contestCrops, v -> config.contestCrops = v)
                .desc("The three crops of the running or next Jacob's contest under the farming line, boosted crop in gold. Read from the tab widget, /calendar or EliteSkyblock")
                .tags("jacob", "crops", "contest", "boosted");
        b.toggle("      Placement (Top x%)",
                () -> config.contestCropsPlacement, v -> config.contestCropsPlacement = v);
        b.toggle("      Fetch From EliteSkyblock",
                () -> config.contestCropsFetch, v -> config.contestCropsFetch = v)
                .desc("Loads this year's contests from api.eliteskyblock.com when none is known");
        b.toggle("  Dark Auction Timer",
                () -> config.contestShowDarkAuction, v -> config.contestShowDarkAuction = v)
                .desc("Time until the next Dark Auction (every hour at :55)");
        b.toggle("  Tab Event Countdown",
                () -> config.contestTabEvent, v -> config.contestTabEvent = v)
                .desc("The event from the tab list Event widget with its \"Ends in\" or \"Starts in\" time (Mining Fiesta, Hoppity, Zoo, Fishing Festival ...). Needs the Event widget in /tab")
                .tags("event", "tab", "countdown", "fiesta", "starts in");
        b.toggle("  SkyBlock Only",
                () -> config.contestTimerSkyblockOnly, v -> config.contestTimerSkyblockOnly = v);
        b.toggle("  Button In Inventory",
                () -> config.contestTimerButton, v -> config.contestTimerButton = v);
        b.info("Drag it in the inventory, or in /tmap gui");
        b.label("Alerts");
        b.toggle("Contest Started Alert",
                () -> config.contestAlertStart, v -> config.contestAlertStart = v);
        b.toggle("Contest Crop Warning",
                () -> config.contestCropWarn, v -> config.contestCropWarn = v)
                .desc("Chat, title and sound before a Jacob's contest with one of the chosen crops starts")
                .tags("jacob", "crops", "contest", "warn");
        b.slider("  Seconds Before", 10f, 600f,
                () -> (float) config.contestCropWarnSeconds, v -> config.contestCropWarnSeconds = Math.round(v), 0);
        b.searchList("  Only For Crops (none = all)",
                () -> java.util.List.of(com.teslamaps.features.JacobContests.CROPS),
                crop -> config.contestCropWarnFor.contains(crop),
                crop -> { if (!config.contestCropWarnFor.remove(crop)) config.contestCropWarnFor.add(crop); });
        b.toggle("  Flash Taskbar When Unfocused",
                () -> config.contestCropWarnFlash, v -> config.contestCropWarnFlash = v);
        b.toggle("Alert if < 5 Minutes left",
                () -> config.contestAlert5Min, v -> config.contestAlert5Min = v);
        b.toggle("Alert if < 1,5 Minutes left",
                () -> config.contestAlert90Sec, v -> config.contestAlert90Sec = v);
        b.toggle("Chat Message",
                () -> config.contestAlertChat, v -> config.contestAlertChat = v);
        b.toggle("Show Title",
                () -> config.contestAlertTitle, v -> config.contestAlertTitle = v);
        b.soundDropdown("Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.contestAlertSound, v -> config.contestAlertSound = v);
        b.slider("Volume", 0f, 4f,
                () -> config.contestAlertVolume, v -> config.contestAlertVolume = v, 2);
        b.info("The two timers only fire while the ticket score is missing");
        b.label("Party Chat");
        b.toggle("Contest Announce",
                () -> config.contestAnnounceParty, v -> config.contestAnnounceParty = v);
        b.info("Sends /pc Contest Started on every contest start");
        b.toggle("Ticket Announce",
                () -> config.contestTicketAnnounce, v -> config.contestTicketAnnounce = v);
        b.slider("  Ticket Score", 50f, 1000f,
                () -> (float) config.contestTicketScore, v -> config.contestTicketScore = Math.round(v), 0);
        b.info("Sends /pc Ticket Acquired! at that score in Miria's Contest");
        b.toggle("Hide Foraging At Ticket Score",
                () -> config.contestHideAtTicket, v -> config.contestHideAtTicket = v);
        b.info("Hidden until the next contest, survives lobby swaps");
    }
}
