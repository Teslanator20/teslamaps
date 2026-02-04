package com.teslamaps.mixin;

import com.teslamaps.esp.StarredMobESP;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to enable entity glowing (through walls) for starred mobs.
 */
@Mixin(Entity.class)
public class EntityMixin {

    /**
     * Override isGlowing to make starred mobs glow through walls.
     */
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void injectIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        // Only apply glow if the config option is enabled
        if (!com.teslamaps.config.TeslaMapsConfig.get().showGlow) return;

        Entity self = (Entity) (Object) this;
        if (StarredMobESP.shouldGlow(self)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Override getTeamColorValue to return custom colors for our highlighted entities.
     */
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void getCustomGlowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        int color = StarredMobESP.getGlowColor(self);
        if (color != 0) {
            cir.setReturnValue(color);
        }
    }
}
