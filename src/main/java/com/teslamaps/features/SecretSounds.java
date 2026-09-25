package com.teslamaps.features;

// Devonian SecretsSound.kt + DungeonEvent.SecretClicked (skull uuids), NoammAddons Secrets.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.util.UUID;

public class SecretSounds {

    private static final UUID WITHER_ESSENCE = UUID.fromString("e0f3e929-869e-3dca-9504-54c666ee6f23");
    private static final UUID REDSTONE_KEY = UUID.fromString("fed95410-aba1-39df-9b95-1d4f361eb66e");
    private static long lastBat = 0;

    /** False when "only real secrets" filters this click out. */
    public static boolean counts(String type, BlockPos pos) {
        if (!TeslaMapsConfig.get().secretSoundRealOnly) return true;
        if (type.equals("button")) return false;
        if (!type.equals("skull") || pos == null) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof SkullBlockEntity skull)) return false;
        var owner = skull.getOwnerProfile();
        if (owner == null) return false;
        UUID id = owner.partialProfile().id();
        return WITHER_ESSENCE.equals(id) || REDSTONE_KEY.equals(id);
    }

    /** Plays the lever sound instead of the normal one; true if it did. */
    public static boolean lever(String type) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!type.equals("lever") || !c.secretSoundLeverSeparate) return false;
        LoudSound.play(SoundOptions.resolve(c.secretSoundLeverType), c.secretSoundLeverVolume, c.secretSoundLeverPitch);
        return true;
    }

    /** Main thread only. True = cancel the vanilla bat sound. */
    public static boolean onSoundPacket(ClientboundSoundPacket packet) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretSoundBatKill || !c.secretSound || !c.section("Sounds") || !DungeonManager.isInDungeon()) return false;
        var sound = packet.getSound().value();
        if ((sound != SoundEvents.BAT_HURT && sound != SoundEvents.BAT_DEATH) || packet.getVolume() != 0.1f) return false;
        long now = System.currentTimeMillis();
        if (now - lastBat > 300) {
            lastBat = now;
            LoudSound.play(SoundOptions.resolve(c.secretSoundType), c.secretSoundVolume, c.secretSoundPitch);
        }
        return true;
    }

    /** True if the custom locked-chest sound replaced the default anvil. */
    public static boolean onLocked() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretSoundLockedCustom || !c.section("Sounds")) return false;
        LoudSound.play(SoundOptions.resolve(c.secretSoundLockedType), c.secretSoundLockedVolume, c.secretSoundLockedPitch);
        return true;
    }
}
