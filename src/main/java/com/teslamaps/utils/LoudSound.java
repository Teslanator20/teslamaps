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

    public static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Identifier soundId = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        if (soundId == null) return;

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

    public static void playStacked(SoundEvent sound, float volume, float pitch, int times) {
        for (int i = 0; i < times; i++) {
            play(sound, volume, pitch);
        }
    }
}
