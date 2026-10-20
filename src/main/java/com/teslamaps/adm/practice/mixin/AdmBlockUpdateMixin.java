package com.teslamaps.adm.practice.mixin;

import com.teslamaps.adm.Adm;
import com.teslamaps.adm.practice.AdmSimonSays;
import com.teslamaps.adm.practice.BrBoxes;
import com.teslamaps.adm.practice.SsPractice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class AdmBlockUpdateMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void teslamaps$admBlock(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide() || !Adm.on()) return;
        BlockState old = level.getBlockState(pos);
        AdmSimonSays.onBlockUpdate(pos, old, newState);
        SsPractice.onBlockUpdate(pos, old, newState);
        BrBoxes.onBlockUpdate(pos, old, newState);
    }
}
