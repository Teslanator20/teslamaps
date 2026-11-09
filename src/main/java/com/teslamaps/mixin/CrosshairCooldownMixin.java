package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teslamaps.features.SbItemCooldowns;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public class CrosshairCooldownMixin {
    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"))
    private float teslamaps$sbCooldown(float original) {
        return SbItemCooldowns.attackStrength(original);
    }
}
