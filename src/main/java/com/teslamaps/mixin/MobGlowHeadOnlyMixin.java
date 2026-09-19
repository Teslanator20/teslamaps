package com.teslamaps.mixin;

import com.teslamaps.features.mobvisuals.MobVisuals;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hypixel builds most mobs out of an armor stand wearing a skull. On a non-marker stand the
 * outline would also trace the arms, body and legs, which is far bigger than the mob looks.
 * A marker stand renders equipment only, so highlighted stands are treated as markers for the
 * draw alone - hitboxes and clicks are untouched. Ported from GanKura.
 */
@Mixin(ArmorStandRenderer.class)
public class MobGlowHeadOnlyMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$headOnlyOutline(ArmorStand entity, ArmorStandRenderState renderState,
                                           float partialTicks, CallbackInfo ci) {
        if (MobVisuals.headOnlyGlow.isEmpty()) return;
        if (MobVisuals.headOnlyGlow.contains(entity)) renderState.isMarker = true;
    }
}
