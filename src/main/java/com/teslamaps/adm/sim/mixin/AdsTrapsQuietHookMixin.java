package com.teslamaps.adm.sim.mixin;

// Ported from ADM's mixin/AdsTrapsQuietHookMixin.java

import com.teslamaps.adm.sim.SimServer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireHookBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Tripwire hooks are silent in the sim's world (Hypixel plays none of vanilla's attach / click / detach sounds). */
@Mixin(TripWireHookBlock.class)
public class AdsTrapsQuietHookMixin {
    // 26.1.2: the sounds are played in emitState (26.3 has them in calculateState)
    @Redirect(
            method = {"emitState", "calculateState"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"),
            require = 0)
    private static void teslamaps$quietHook(Level level, Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        if (SimServer.isSimLevel(level)) return;
        level.playSound(except, pos, sound, source, volume, pitch);
    }
}
