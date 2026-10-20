package com.teslamaps.adm.practice.mixin;

import com.teslamaps.adm.practice.BrBoxes;
import com.teslamaps.adm.practice.SsPractice;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// SS Practice's device clicks and the BR box wand: handled there, cancelled here before anything is sent.
@Mixin(Minecraft.class)
public class AdmInputMixin {

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void teslamaps$ssPick(float partialTicks, CallbackInfo ci) {
        SsPractice.afterPick(partialTicks);
    }

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$admUse(CallbackInfo ci) {
        if (SsPractice.onUse() || BrBoxes.onUse()) ci.cancel();
    }

    @Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
    private void teslamaps$admAttack(CallbackInfoReturnable<Boolean> cir) {
        if (SsPractice.onAttack() || BrBoxes.onAttack()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack(Z)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$admNoMining(boolean attacking, CallbackInfo ci) {
        if (SsPractice.blocksContinueAttack() || BrBoxes.blocksContinueAttack()) ci.cancel();
    }
}
