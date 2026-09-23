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
        b.toggle("Enable Pickup Sound", () -> config.keyPickupSoundEnabled, v -> config.keyPickupSoundEnabled = v);
        b.soundDropdown("Pickup Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.keyPickupSound, v -> config.keyPickupSound = v);
        b.slider("Pickup Volume", 0f, 4f,
                () -> config.keyPickupVolume, v -> config.keyPickupVolume = v, 2);
        b.toggle("Enable Spawn Sound", () -> config.keyOnGroundSoundEnabled, v -> config.keyOnGroundSoundEnabled = v);
        b.soundDropdown("Spawn Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.keyOnGroundSound, v -> config.keyOnGroundSound = v);
        b.slider("Spawn Volume", 0f, 4f,
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
        b.toggle("Secret Chime (Rising Pitch)", () -> config.secretChime, v -> config.secretChime = v);
        b.soundDropdown("  Chime Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.secretChimeSound, v -> config.secretChimeSound = v);
        b.slider("  Chime Volume", 0f, 4f,
                () -> config.secretChimeVolume, v -> config.secretChimeVolume = v, 2);
        b.label("Bear Spawn Warning");
        b.toggle("Enable Alert", () -> config.bearSpawnWarning, v -> config.bearSpawnWarning = v);
        b.toggle("Warden Sound", () -> config.bearSpawnWardenSound, v -> config.bearSpawnWardenSound = v);
        b.toggle("Wither Sound", () -> config.bearSpawnWitherSound, v -> config.bearSpawnWitherSound = v);
        b.slider("Alert Volume", 0f, 20f,
                () -> config.bearSpawnVolume, v -> config.bearSpawnVolume = v, 1);
        b.label("Etherwarp");
        b.toggle("Custom Etherwarp Sound", () -> config.etherwarpCustomSound, v -> config.etherwarpCustomSound = v);
        b.soundDropdown("Etherwarp Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.etherwarpSound, v -> config.etherwarpSound = v);
        b.slider("Etherwarp Volume", 0f, 20f,
                () -> config.etherwarpSoundVolume, v -> config.etherwarpSoundVolume = v, 1);
        b.slider("Etherwarp Pitch", 0.5f, 2f,
                () -> config.etherwarpSoundPitch, v -> config.etherwarpSoundPitch = v, 2);
        b.label("Last Breath");
        b.slider("Last Breath Volume", 0f, 5f,
                () -> config.lastBreathVolume, v -> config.lastBreathVolume = v, 2);
        b.slider("Last Breath Threshold (ticks)", 0f, 21f,
                () -> (float) config.lastBreathThreshold, v -> config.lastBreathThreshold = Math.round(v));
        b.label("Sound Hiders");
        b.toggle("No Explosion Sound", () -> config.noExplosionSound, v -> config.noExplosionSound = v);
        b.toggle("No Creeper Hurt Sound", () -> config.noCreeperHurtSound, v -> config.noCreeperHurtSound = v);
        b.toggle("Mute Bonzo Staff", () -> config.muteBonzoStaff, v -> config.muteBonzoStaff = v);
        b.toggle("Mute Lightning", () -> config.muteLightning, v -> config.muteLightning = v);
        b.toggle("Custom Hype Sound (wither blades)", () -> config.customHypeSound, v -> config.customHypeSound = v);
        b.soundDropdown("Hype Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.customHypeSoundType, v -> config.customHypeSoundType = v);
        b.slider("Hype Sound Volume", 0f, 20f,
                () -> config.customHypeSoundVolume, v -> config.customHypeSoundVolume = v, 1);
        b.slider("Hype Sound Pitch", 0.5f, 2f,
                () -> config.customHypeSoundPitch, v -> config.customHypeSoundPitch = v, 2);
    }
}
