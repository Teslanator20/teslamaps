package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimServer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The S4 target plate is silent in the sim, as on Hypixel. */
@Mixin(BasePressurePlateBlock.class)
public class PressurePlateSimMixin {
    private static final BlockPos TESLAMAPS$S4_PLATE = new BlockPos(63, 127, 35);

    @Redirect(method = "checkPressed",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;)V"),
            require = 0)
    private void teslamaps$silentS4Plate(Level level, Entity except, BlockPos pos, SoundEvent sound, SoundSource source) {
        if (TESLAMAPS$S4_PLATE.equals(pos) && SimServer.isSimLevel(level)) return;
        level.playSound(except, pos, sound, source);
    }
}
