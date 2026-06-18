package com.teslamaps.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Utility to play sounds louder than vanilla allows.
 * Vanilla caps volume at 1.0, this bypasses that limit.
 */
public class LoudSound {

    /**
     * Play a sound with volume that can exceed 1.0 (up to 20x or more).
     *
     * @param sound  The sound event to play
     * @param volume Volume multiplier (1.0 = normal, 20.0 = 20x louder)
     * @param pitch  Pitch (1.0 = normal)
     */
    public static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Get the sound identifier from the registry
        Identifier soundId = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        if (soundId == null) return;

        // Create a custom sound instance with overridden volume
        SoundInstance instance = new SimpleSoundInstance(
                soundId,
                SoundSource.MASTER,  // Use master category to bypass individual volume settings
                volume,                 // This CAN exceed 1.0 with PositionedSoundInstance
                pitch,
                RandomSource.create(),
                false,                  // Not looping
                0,                      // No attenuation delay
                SoundInstance.Attenuation.NONE,  // No distance attenuation = full volume everywhere
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                false                   // Not relative
        );

        mc.getSoundManager().play(instance);
    }

    /**
     * Play a sound multiple times to stack volume (alternative method for extreme loudness).
     *
     * @param sound  The sound event to play
     * @param volume Volume multiplier
     * @param pitch  Pitch
     * @param times  How many times to play (stacks volume)
     */
    public static void playStacked(SoundEvent sound, float volume, float pitch, int times) {
        for (int i = 0; i < times; i++) {
            play(sound, volume, pitch);
        }
    }
}
