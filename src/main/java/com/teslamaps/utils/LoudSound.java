/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class LoudSound {

    // Marker so the SoundEngine/Channel mixins know to bypass the vanilla 1.0 volume cap for our sounds only
    public static class LoudSoundInstance extends SimpleSoundInstance {
        public LoudSoundInstance(Identifier id, SoundSource source, float volume, float pitch, RandomSource random,
                                 boolean looping, int delay, SoundInstance.Attenuation attenuation, double x, double y, double z, boolean relative) {
            super(id, source, volume, pitch, random, looping, delay, attenuation, x, y, z, relative);
        }
    }

    public static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Identifier soundId = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        if (soundId == null) return;

        SoundInstance instance = new LoudSoundInstance(
                soundId,
                SoundSource.MASTER,  // Use master category to bypass individual volume settings
                volume,                 // CAN exceed 1.0; mixins lift the vanilla clamp + OpenAL AL_MAX_GAIN
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

    public static void playStacked(SoundEvent sound, float volume, float pitch, int times) {
        for (int i = 0; i < times; i++) {
            play(sound, volume, pitch);
        }
    }
}
