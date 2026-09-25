package com.teslamaps.mixin;

import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.mobvisuals.MobVisuals;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void injectIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.GlobalRender.off()) return;
        Entity self = (Entity) (Object) this;

        if (com.teslamaps.config.TeslaMapsConfig.get().showGlow && StarredMobESP.shouldGlow(self)) {
            cir.setReturnValue(true);
            return;
        }
        if (!MobVisuals.glowing.isEmpty() && MobVisuals.glowing.contains(self)) {
            cir.setReturnValue(true);
            return;
        }
        if (!com.teslamaps.render.EntityGlow.COLORS.isEmpty() && com.teslamaps.render.EntityGlow.COLORS.containsKey(self)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.config.TeslaMapsConfig.get().removeGlowEffect) cir.setReturnValue(false);
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void getCustomGlowColor(CallbackInfoReturnable<Integer> cir) {
        if (com.teslamaps.features.GlobalRender.off()) return;
        Entity self = (Entity) (Object) this;

        int color = StarredMobESP.getGlowColor(self);
        if (color != 0) {
            cir.setReturnValue(color);
            return;
        }
        if (!MobVisuals.glowColors.isEmpty()) {
            Integer mobColor = MobVisuals.glowColors.get(self);
            if (mobColor != null) {
                cir.setReturnValue(mobColor);
                return;
            }
        }
        if (com.teslamaps.render.EntityGlow.COLORS.isEmpty()) return;
        Integer glow = com.teslamaps.render.EntityGlow.COLORS.get(self);
        if (glow != null) cir.setReturnValue(glow);
    }

    /**
     * Vanilla stops rendering an entity past "average hitbox edge x 64 x render distance".
     * The outline only exists while the entity renders, so beyond that a Mob Visuals tracer
     * would point at a mob with no glow. Frustum culling is separate, so off-screen entities
     * are still skipped. Ported from GanKura.
     */
    @Inject(method = "shouldRenderAtSqrDistance(D)Z", at = @At("HEAD"), cancellable = true)
    private void keepHighlightedRendered(double distanceSqr, CallbackInfoReturnable<Boolean> cir) {
        if (MobVisuals.glowing.isEmpty()) return;
        if (MobVisuals.glowing.contains((Entity) (Object) this)) cir.setReturnValue(true);
    }
}
