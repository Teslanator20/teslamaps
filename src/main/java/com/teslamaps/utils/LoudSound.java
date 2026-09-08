package com.teslamaps.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
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
        // packet hooks call this from the netty thread; SoundEngine maps are not thread-safe
        if (!mc.isSameThread()) {
            mc.execute(() -> play(sound, volume, pitch));
            return;
        }

        // the bundled sounds are not in the registry, so the event carries the only id there is
        Identifier soundId = sound.location();
        if (soundId == null) return;

        // no player on the title screen, so the sound plays on the listener instead
        boolean atListener = mc.player == null;
        double x = atListener ? 0 : mc.player.getX();
        double y = atListener ? 0 : mc.player.getY();
        double z = atListener ? 0 : mc.player.getZ();

        SoundInstance instance = new LoudSoundInstance(
                soundId,
                SoundSource.MASTER,  // Use master category to bypass individual volume settings
                volume,                 // CAN exceed 1.0; mixins lift the vanilla clamp + OpenAL AL_MAX_GAIN
                pitch,
                RandomSource.create(),
                false,                  // Not looping
                0,                      // No attenuation delay
                SoundInstance.Attenuation.NONE,  // No distance attenuation = full volume everywhere
                x,
                y,
                z,
                atListener              // relative = at the listener when there is no player
        );

        mc.getSoundManager().play(instance);
    }

    public static void playStacked(SoundEvent sound, float volume, float pitch, int times) {
        for (int i = 0; i < times; i++) {
            play(sound, volume, pitch);
        }
    }
}
