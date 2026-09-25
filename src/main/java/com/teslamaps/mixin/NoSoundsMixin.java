package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class NoSoundsMixin {

    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void teslamaps$muteSounds(ClientboundSoundPacket packet, CallbackInfo ci) {
        process(packet.getSound().value(), packet.getVolume(), packet.getPitch(), ci);
        com.teslamaps.features.SecretClickHighlight.onSound(packet.getSound().value(), packet.getVolume(), packet.getX(), packet.getY(), packet.getZ());
        if (TeslaMapsConfig.get().dragonHitCounter) com.teslamaps.dungeon.DragonHitCount.onSound(packet.getSound().value(), packet.getSource(), packet.getVolume());
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"), cancellable = true)
    private void teslamaps$muteEntitySounds(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        process(packet.getSound().value(), packet.getVolume(), packet.getPitch(), ci);
    }

    private static void process(SoundEvent sound, float vol, float pitch, CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();

        com.teslamaps.features.CombatTimers.onSound(sound, vol, pitch);
        com.teslamaps.features.SpringBootsHelper.onSound(sound);
        if (com.teslamaps.features.SadanFeatures.muteSound(sound, vol, pitch)) { ci.cancel(); return; }

        if (com.teslamaps.features.TerminalSounds.cancelPling(sound, vol, pitch)) { ci.cancel(); return; }

        if (c.noExplosionSound && sound == SoundEvents.GENERIC_EXPLODE.value()) { ci.cancel(); return; }
        if (c.noCreeperHurtSound && sound == SoundEvents.CREEPER_HURT) { ci.cancel(); return; }
        if (c.muteBonzoStaff && c.muteBonzoStaffPrecise && sound == SoundEvents.GHAST_AMBIENT
                && vol == 1f && pitch >= 1.3968254f && pitch <= 1.7936507f) { ci.cancel(); return; }

        if (c.customHypeSound && com.teslamaps.features.HypeSound.onSound(sound, vol, pitch)) {
            ci.cancel();
            return;
        }

        if (c.creeperBeamsDing && inCreeperBeams()) {
            boolean hit = (sound == SoundEvents.EXPERIENCE_ORB_PICKUP && approx(pitch, 0.7936508f))
                    || (sound == SoundEvents.ELDER_GUARDIAN_HURT && (approx(pitch, 1.3968254f) || approx(pitch, 2.0f)));
            if (hit) {
                play(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value());
                ci.cancel();
            }
        }
    }

    private static boolean approx(float a, float b) { return Math.abs(a - b) < 0.01f; }

    private static void play(SoundEvent s) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.playSound(s, 1.0f, 1.0f); });
    }

    private static boolean inCreeperBeams() {
        DungeonRoom room = DungeonManager.getCurrentRoom();
        return room != null && "Creeper Beams".equals(room.getName());
    }
}
