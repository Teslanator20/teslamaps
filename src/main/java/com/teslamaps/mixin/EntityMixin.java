package com.teslamaps.mixin;

import com.teslamaps.esp.StarredMobESP;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void injectIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (!com.teslamaps.config.TeslaMapsConfig.get().showGlow) return;

        Entity self = (Entity) (Object) this;
        if (StarredMobESP.shouldGlow(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void getCustomGlowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        int color = StarredMobESP.getGlowColor(self);
        if (color != 0) {
            cir.setReturnValue(color);
        }
    }
}
