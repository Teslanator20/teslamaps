package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimItems;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class ShortbowSimMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void teslamaps$adsShortbowLeftClick(CallbackInfoReturnable<Boolean> cir) {
        SimItems.clientLeftClick();
    }
}
