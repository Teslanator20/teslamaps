package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class SoundsCategory {

    private SoundsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Wither Key");
        b.toggle("Key Pickup/Spawn Title", () -> config.keyPickupTitle, v -> config.keyPickupTitle = v);
        b.toggle("  Name Who Picked It Up", () -> config.keyPickupWho, v -> config.keyPickupWho = v).fresh()
                .desc("\"You\" or the picker's class in class colour, then \"Picked Up Wither/Blood Key\"");
        b.toggle("Enable Pickup Sound", () -> config.keyPickupSoundEnabled, v -> config.keyPickupSoundEnabled = v);
        b.soundDropdown("  Pickup Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.keyPickupSound, v -> config.keyPickupSound = v);
        b.slider("  Pickup Volume", 0f, 4f,
                () -> config.keyPickupVolume, v -> config.keyPickupVolume = v, 2);
        b.toggle("Enable Spawn Sound", () -> config.keyOnGroundSoundEnabled, v -> config.keyOnGroundSoundEnabled = v);
        b.soundDropdown("  Spawn Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.keyOnGroundSound, v -> config.keyOnGroundSound = v);
        b.slider("  Spawn Volume", 0f, 4f,
                () -> config.keyOnGroundVolume, v -> config.keyOnGroundVolume = v, 2);
        b.classFilter("Key Alerts: Only as Class", config.keySoundClasses);
        b.label("Secret Found");
        b.toggle("Enable Secret Sound", () -> config.secretSound, v -> config.secretSound = v);
        b.soundDropdown("Secret Sound Type",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.secretSoundType, v -> config.secretSoundType = v);
        b.slider("Secret Volume", 0f, 4f,
                () -> config.secretSoundVolume, v -> config.secretSoundVolume = v, 2);
        b.slider("Secret Pitch", 0.5f, 2f,
                () -> config.secretSoundPitch, v -> config.secretSoundPitch = v, 2);
        b.toggle("Secret Item Pickup Sound", () -> config.secretItemPickupSound, v -> config.secretItemPickupSound = v);
        b.toggle("Secret Bat Kill Sound (replaces bat)", () -> config.secretSoundBatKill, v -> config.secretSoundBatKill = v).fresh();
        b.toggle("Only Real Secrets (skull id, no buttons)", () -> config.secretSoundRealOnly, v -> config.secretSoundRealOnly = v).fresh();
        b.toggle("Secret Chime (Rising Pitch)", () -> config.secretChime, v -> config.secretChime = v);
        b.soundDropdown("  Chime Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.secretChimeSound, v -> config.secretChimeSound = v);
        b.slider("  Chime Volume", 0f, 4f,
                () -> config.secretChimeVolume, v -> config.secretChimeVolume = v, 2);
        b.toggle("  Chime on Secret Bat Kill", () -> config.secretChimeBat, v -> config.secretChimeBat = v).fresh();
        b.toggle("  Chime on Secret Item Pickup", () -> config.secretChimeItem, v -> config.secretChimeItem = v).fresh();
        b.toggle("  Mute Hypixel Chest Sound", () -> config.secretMuteChest, v -> config.secretMuteChest = v).fresh()
                .desc("Drops Hypixel's own chest secret sound (exact volume/pitch match)");
        b.toggle("  Mute Hypixel Lever Sound", () -> config.secretMuteLever, v -> config.secretMuteLever = v).fresh()
                .desc("Drops Hypixel's own lever secret sound (exact volume/pitch match)");
        b.label("Etherwarp");
        b.toggle("Custom Etherwarp Sound", () -> config.etherwarpCustomSound, v -> config.etherwarpCustomSound = v);
        b.toggle("  Zero-Ping Etherwarp Sound", () -> config.etherwarpZeroPing, v -> config.etherwarpZeroPing = v).fresh()
                .desc("Plays the sound as you click when the warp will land, instead of waiting for the server.");
        b.soundDropdown("  Etherwarp Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.etherwarpSound, v -> config.etherwarpSound = v);
        b.slider("  Etherwarp Volume", 0f, 20f,
                () -> config.etherwarpSoundVolume, v -> config.etherwarpSoundVolume = v, 1);
        b.slider("  Etherwarp Pitch", 0.5f, 2f,
                () -> config.etherwarpSoundPitch, v -> config.etherwarpSoundPitch = v, 2);
        b.label("Last Breath");
        b.slider("Last Breath Volume", 0f, 5f,
                () -> config.lastBreathVolume, v -> config.lastBreathVolume = v, 2);
        b.slider("Last Breath Threshold (ticks)", 0f, 21f,
                () -> (float) config.lastBreathThreshold, v -> config.lastBreathThreshold = Math.round(v));
        b.label("Sound Manager");
        b.toggle("Sound Manager (volume per sound)", () -> config.soundManager, v -> config.soundManager = v);
        b.button("  Open Sound Manager", () -> net.minecraft.client.Minecraft.getInstance()
                .gui.setScreen(new com.teslamaps.screen.SoundManagerScreen(Minecraft.getInstance().gui.screen())));
        b.info("Recent tab lists the last 100 played sounds");
        b.label("Sound & Particle Tracker");
        b.info("/tmap tracksounds [seconds|end|ignore <id>]");
        b.info("/tmap trackparticles [seconds|end|ignore <id>]");
        b.info("/tmap playsound <id> [pitch] [volume]");
        b.slider("  Shown For (s)", 1f, 20f,
                () -> (float) config.eventTrackerWindow, v -> config.eventTrackerWindow = Math.round(v));
        b.slider("  Max Lines", 1f, 100f,
                () -> (float) config.eventTrackerMaxLines, v -> config.eventTrackerMaxLines = Math.round(v));
        b.keybind("  Track Sounds Key", () -> config.trackSoundsKey, v -> config.trackSoundsKey = v);
        b.keybind("  Track Particles Key", () -> config.trackParticlesKey, v -> config.trackParticlesKey = v);
        b.label("Sound Hiders");
        b.toggle("Mute Bonzo Staff", () -> config.muteBonzoStaff, v -> config.muteBonzoStaff = v);
        b.toggle("  Bonzo: Precise (keep real fireworks)", () -> config.muteBonzoStaffPrecise, v -> config.muteBonzoStaffPrecise = v);
        b.toggle("Mute Rat Pet", () -> config.muteRatPet, v -> config.muteRatPet = v).fresh()
                .desc("Bat squeak and chicken steps near you, only when no real bat/chicken is there");
        b.toggle("  Squeak Only", () -> config.muteRatPetSqueakOnly, v -> config.muteRatPetSqueakOnly = v).fresh();
        b.toggle("Mute Backpack Opening Sound", () -> config.muteBackpackOpen, v -> config.muteBackpackOpen = v).fresh();
        b.toggle("Mute Endermen In The End", () -> config.muteEndermanEnd, v -> config.muteEndermanEnd = v).fresh()
                .desc("Enderman ambient, hurt, death, scream and stare sounds, only on The End island.");
        b.toggle("Mute Blaze Slayer Sounds", () -> config.muteBlazeSounds, v -> config.muteBlazeSounds = v).fresh()
                .desc("Blaze, ghast shot, fire, lava pop and lightning, only on the Crimson Isle.")
                .tags("slayer", "demonlord", "inferno", "blaze", "crimson");
        b.toggle("Mute Wolf Slayer Sounds", () -> config.muteWolfSounds, v -> config.muteWolfSounds = v).fresh()
                .desc("Every wolf sound, only in the Park and the Hub.")
                .tags("slayer", "sven", "wolf");
        b.toggle("Mute Spider Slayer Sounds", () -> config.muteSpiderSounds, v -> config.muteSpiderSounds = v).fresh()
                .desc("Spider, bat, silverfish and skeleton sounds in the Spider's Den and on the Crimson Isle, "
                        + "except while your Inferno Demonlord is up.")
                .tags("slayer", "tarantula", "spider", "skeleton");
        b.toggle("Mute Error Sound (wrong click / no mana)", () -> config.muteErrorSound, v -> config.muteErrorSound = v).fresh()
                .desc("Also the ability cooldown sound (enderman teleport, pitch 0, volume 8)").tags("cooldown");
        b.toggle("Mute Jerry-chine Gun", () -> config.muteJerrychine, v -> config.muteJerrychine = v).fresh()
                .desc("Villager sounds of the Jerry-chine projectiles, matched by their exact volume").tags("jerrygun", "villager");
        b.toggle("Mute Flower Of Truth", () -> config.muteFlowerOfTruth, v -> config.muteFlowerOfTruth = v).fresh()
                .desc("Eating sound of the Flower of Truth projectile, matched by its exact pitch").tags("truth", "eat");
        b.toggle("Quiet F7 Boss Sounds", () -> config.f7BossSounds, v -> config.f7BossSounds = v).fresh();
        b.toggle("  Cancel All Above 1.0", () -> config.f7BossSoundsAboveCap, v -> config.f7BossSoundsAboveCap = v).fresh();
        b.toggle("  Cap at 1.0 Instead of Cancel", () -> config.f7BossSoundsCap, v -> config.f7BossSoundsCap = v).fresh();
        b.toggle("Custom Hype Sound (wither blades)", () -> config.customHypeSound, v -> config.customHypeSound = v);
        b.soundDropdown("  Hype Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.customHypeSoundType, v -> config.customHypeSoundType = v);
        b.slider("  Hype Sound Volume", 0f, 20f,
                () -> config.customHypeSoundVolume, v -> config.customHypeSoundVolume = v, 1);
        b.slider("  Hype Sound Pitch", 0.5f, 2f,
                () -> config.customHypeSoundPitch, v -> config.customHypeSoundPitch = v, 2);
    }
}
