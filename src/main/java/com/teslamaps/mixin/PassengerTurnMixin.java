package com.teslamaps.mixin;

import com.teslamaps.features.CameraTweaks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class PassengerTurnMixin {
    @Inject(method = "onPassengerTurned", at = @At("HEAD"))
    private void teslamaps$ridingInputFix(Entity passenger, CallbackInfo ci) {
        CameraTweaks.onPassengerTurned((Entity) (Object) this, passenger);
    }
}
