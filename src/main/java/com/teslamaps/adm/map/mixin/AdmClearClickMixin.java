package com.teslamaps.adm.map.mixin;

import com.teslamaps.adm.map.clear.ClearBreakdown;
import com.teslamaps.adm.map.clear.Sightings;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class AdmClearClickMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void adm$click(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (ClearBreakdown.enabled()) Sightings.onClick(hit.getBlockPos());
    }
}
