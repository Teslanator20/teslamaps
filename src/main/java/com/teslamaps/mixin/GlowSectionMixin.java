package com.teslamaps.mixin;

import com.teslamaps.features.mobvisuals.MobVisuals;
import com.teslamaps.render.EntityGlow;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// GanKura's BossSectionVisibilityMixin: a glowing mob in an unbuilt section still renders
@Mixin(LevelRenderer.class)
public class GlowSectionMixin {

    @Inject(method = "isSectionCompiledAndVisible", at = @At("HEAD"), cancellable = true)
    private void teslamaps$glowSection(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!com.teslamaps.config.TeslaMapsConfig.get().glowUnbuiltSections) return;
        if (!MobVisuals.glowing.isEmpty()) {
            for (Entity e : MobVisuals.glowing) {
                if (e.blockPosition().equals(pos)) { cir.setReturnValue(true); return; }
            }
        }
        if (!EntityGlow.COLORS.isEmpty()) {
            for (Entity e : EntityGlow.COLORS.keySet()) {
                if (e.blockPosition().equals(pos)) { cir.setReturnValue(true); return; }
            }
        }
    }
}
