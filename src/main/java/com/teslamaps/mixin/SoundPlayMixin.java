package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The universal client sound funnel — catches ALL sounds (client-played, packet-played, explosions),
// unlike the packet hook in NoSoundsMixin. Drives the sound-debug print and the mutedSounds list.
@Mixin(SoundEngine.class)
public class SoundPlayMixin {

    // Bonzo's Staff impact plays the firework blast/twinkle family (same sounds Patcher/others mute)
    private static final java.util.Set<String> BONZO_STAFF = java.util.Set.of(
            "minecraft:entity.firework_rocket.blast",
            "minecraft:entity.firework_rocket.blast_far",
            "minecraft:entity.firework_rocket.large_blast",
            "minecraft:entity.firework_rocket.large_blast_far",
            "minecraft:entity.firework_rocket.twinkle",
            "minecraft:entity.firework_rocket.twinkle_far");

    private static final java.util.Set<String> LIGHTNING = java.util.Set.of(
            "minecraft:entity.lightning_bolt.thunder",
            "minecraft:entity.lightning_bolt.impact");

    @Inject(method = "play", at = @At("HEAD"))
    private void teslamaps$recordSound(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        com.teslamaps.features.SoundVolumes.record(instance);
    }

    // injected after the SoundInstance is resolved (this.sound non-null), where getPitch/getVolume are safe
    @Inject(method = "play",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F", ordinal = 0),
            cancellable = true)
    private void teslamaps$debugAndMute(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean hasMutes = c.mutedSounds != null && !c.mutedSounds.isEmpty();
        boolean foragingMutes = com.teslamaps.features.foraging.ForagingMutes.enabled(c);
        if (!c.soundDebug && !hasMutes && !c.muteBonzoStaff && !c.muteLightning && !foragingMutes) return;

        String id;
        float pitch, vol;
        try {
            id = instance.getIdentifier().toString();
            pitch = instance.getPitch();
            vol = instance.getVolume();
        } catch (Throwable t) { return; }

        if (c.soundDebug) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§7[Sound] §f" + id + " §8vol=" + String.format("%.2f", vol) + " pitch=" + String.format("%.3f", pitch))); });
        }
        if (c.muteBonzoStaff && BONZO_STAFF.contains(id)) { cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED); return; }
        if (c.muteLightning && LIGHTNING.contains(id)) { cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED); return; }
        if (foragingMutes && com.teslamaps.features.foraging.ForagingMutes.shouldMute(c, id, vol)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
            return;
        }
        if (hasMutes && isMuted(c, id, pitch)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }

    // entries are "id" (any pitch) or "id@pitch" (specific pitch)
    private static boolean isMuted(TeslaMapsConfig c, String id, float pitch) {
        for (String e : c.mutedSounds) {
            if (e == null || e.isBlank()) continue;
            int at = e.indexOf('@');
            if (at < 0) { if (e.equals(id)) return true; }
            else {
                if (!e.substring(0, at).equals(id)) continue;
                try { if (Math.abs(Float.parseFloat(e.substring(at + 1)) - pitch) < 0.01f) return true; } catch (NumberFormatException ignored) {}
            }
        }
        return false;
    }
}
