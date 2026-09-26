package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ForagingCategory {

    private ForagingCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.info("For Moonglade Marsh and Torrhus Canyon");
        b.label("Chat");
        b.toggle("Compact Tree Gifts",
                () -> config.foragingCompactGifts, v -> config.foragingCompactGifts = v);
        b.info("One line instead of the whole gift block, hover kept");
        b.toggle("  Hide Uncommon Drops", () -> config.foragingGiftHideUncommon, v -> config.foragingGiftHideUncommon = v)
                .desc("Stretching Sticks, Deep Root");
        b.toggle("  Hide Enchanted Books", () -> config.foragingGiftHideBooks, v -> config.foragingGiftHideBooks = v)
                .desc("First Impression, Missile, Karma");
        b.toggle("  Hide Boosters", () -> config.foragingGiftHideBoosters, v -> config.foragingGiftHideBoosters = v);
        b.toggle("  Hide Shards", () -> config.foragingGiftHideShards, v -> config.foragingGiftHideShards = v)
                .desc("Hummingbird, Chameleon");
        b.toggle("  Hide Runes", () -> config.foragingGiftHideRunes, v -> config.foragingGiftHideRunes = v);
        b.toggle("  Hide Misc Drops", () -> config.foragingGiftHideMisc, v -> config.foragingGiftHideMisc = v)
                .desc("Tree the Fish, pet item");
        b.toggle("  Hide Mobs", () -> config.foragingGiftHideMobs, v -> config.foragingGiftHideMobs = v)
                .desc("Phantoms and other mobs falling from the tree");
        b.toggle("Hide Sweep Details",
                () -> config.foragingHideSweepDetails, v -> config.foragingHideSweepDetails = v);
        b.info("Toughness / Logs / penalty lines");
        b.toggle("Compact Sweep Details",
                () -> config.foragingCompactSweep, v -> config.foragingCompactSweep = v)
                .desc("One line: sweep, logs, penalties. Hover for the breakdown and pro tip, click for /treegifts.");
        b.toggle("Hide Lottery Buffs",
                () -> config.foragingHideLottery, v -> config.foragingHideLottery = v);
        b.info("Lottery and Beekeeper messages off the foraging islands");
        b.toggle("Beeheemoth Spawn Title", () -> config.beeheemothTitle, v -> config.beeheemothTitle = v)
                .desc("BEEHEEMOTH! title with the sub-area it spawned in");
        b.toggle("Macaw Birdfeeder Title", () -> config.macawTitle, v -> config.macawTitle = v)
                .desc("MACAWS ATTRACTED! title when two Macaws come to the Birdfeeder");
        b.label("Sounds");
        b.toggle("Mute Phantoms",
                () -> config.foragingMutePhantoms, v -> config.foragingMutePhantoms = v);
        b.toggle("Mute Fusion Machine",
                () -> config.foragingMuteFusion, v -> config.foragingMuteFusion = v);
        b.toggle("Mute Tree Breaking",
                () -> config.foragingMuteTreeBreak, v -> config.foragingMuteTreeBreak = v);
        b.info("Mutes it everywhere except Moonglade / Torrhus");
        b.toggle("  Also on Moonglade / Torrhus",
                () -> config.foragingMuteTreeBreakGalatea, v -> config.foragingMuteTreeBreakGalatea = v);
        b.label("Clean Tree View");
        b.toggle("Hide Block Break Effects",
                () -> config.foragingCleanTreeView, v -> config.foragingCleanTreeView = v);
        b.toggle("Hide Rune Effects",
                () -> config.foragingHideRuneEffects, v -> config.foragingHideRuneEffects = v);
        b.label("Honeycomb");
        b.toggle("Lather Reminder",
                () -> config.latherReminder, v -> config.latherReminder = v);
        b.info("12 min per tree, tracked for Helix / Fig / Mangrove");
        b.toggle("  Show Title",
                () -> config.latherReminderTitle, v -> config.latherReminderTitle = v);
        b.soundDropdown("  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.latherReminderSound, v -> config.latherReminderSound = v);
        b.slider("  Volume", 0f, 4f,
                () -> config.latherReminderVolume, v -> config.latherReminderVolume = v, 2);
        b.toggle("Lather Overlay",
                () -> config.latherOverlay, v -> config.latherOverlay = v);
        b.toggle("  Foraging Islands Only",
                () -> config.latherOverlayForagingOnly, v -> config.latherOverlayForagingOnly = v);
        b.toggle("  Hide When Nothing Lathered",
                () -> config.latherOverlayOnlyWhenActive, v -> config.latherOverlayOnlyWhenActive = v);
        b.info("Position and scale in /tmap gui");

        b.label("Floor Drops");
        b.toggle("Floor Drop Highlight", () -> config.floorDrops, v -> config.floorDrops = v)
                .desc("Galatea, Torrhus Canyon and Critter Safari. Gone once you click it.")
                .tags("forest node", "string", "hunting");
        b.toggle("  Safari: Current Biome Only",
                () -> config.floorDropsSafariBiome, v -> config.floorDropsSafariBiome = v);
        b.color("  Floor Drop Color", () -> config.floorDropsColor, v -> config.floorDropsColor = v);

        b.label("Honeyhives");
        b.toggle("Refill Reminder",
                () -> config.honeyhiveReminder, v -> config.honeyhiveReminder = v);
        b.toggle("  Remind Outside Torrhus Canyon",
                () -> config.honeyhiveRemindOutside, v -> config.honeyhiveRemindOutside = v);
        b.toggle("Queen Bee Title",
                () -> config.honeyhiveQueenBeeTitle, v -> config.honeyhiveQueenBeeTitle = v);
        b.toggle("Honeyhive Helper", () -> config.honeyhiveHelper, v -> config.honeyhiveHelper = v)
                .desc("Tracks every hive's refill time from its nametag, per profile.");
        b.toggle("  Hive List", () -> config.honeyhiveDisplay, v -> config.honeyhiveDisplay = v);
        b.info("Open an inventory to scroll it or click a hive for its waypoint");
        b.toggle("    List Outside Torrhus", () -> config.honeyhiveDisplayOutside, v -> config.honeyhiveDisplayOutside = v);
        b.slider("    Max Lines", 1f, 30f, () -> config.honeyhiveMaxLines, v -> config.honeyhiveMaxLines = v, 0);
        b.toggle("  Ready Waypoints", () -> config.honeyhiveReadyWaypoints, v -> config.honeyhiveReadyWaypoints = v);
        b.toggle("  Ready Sound", () -> config.honeyhiveReadySound, v -> config.honeyhiveReadySound = v);
        b.label("Heart of the Forest");
        b.toggle("Highlight Perks",
                () -> config.hotfHighlight, v -> config.hotfHighlight = v);
        b.color("  Enabled Color",
                () -> config.colorHotfEnabled, v -> config.colorHotfEnabled = v);
        b.color("  Disabled Color",
                () -> config.colorHotfDisabled, v -> config.colorHotfDisabled = v);
        b.color("  Locked Color",
                () -> config.colorHotfLocked, v -> config.colorHotfLocked = v);
        b.toggle("Level Stack",
                () -> config.hotfLevelStack, v -> config.hotfLevelStack = v);
        b.toggle("Token Stack",
                () -> config.hotfTokenStack, v -> config.hotfTokenStack = v);
        b.toggle("Rotating Perk Display",
                () -> config.hotfPerkDisplay, v -> config.hotfPerkDisplay = v);
        b.toggle("  Foraging Islands Only",
                () -> config.hotfPerkDisplayForagingOnly, v -> config.hotfPerkDisplayForagingOnly = v);
        b.info("Position and scale in /tmap gui");

        b.label("Lasso");
        b.toggle("Lasso Display",
                () -> config.lassoDisplay, v -> config.lassoDisplay = v);
        b.info("Progress bar and REEL on the HUD, position in /tmap gui");
        b.soundDropdown("  Reel Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.lassoReelSound, v -> config.lassoReelSound = v);
        b.slider("  Volume", 0f, 4f,
                () -> config.lassoReelVolume, v -> config.lassoReelVolume = v, 2);
        b.slider("  Pitch", 0.5f, 2f,
                () -> config.lassoReelPitch, v -> config.lassoReelPitch = v, 2);
    }
}
