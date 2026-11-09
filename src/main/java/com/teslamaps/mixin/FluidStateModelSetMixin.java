package com.teslamaps.mixin;

import com.teslamaps.features.TranslucentLava;
import java.util.Map;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidStateModelSet.class)
public class FluidStateModelSetMixin {
    @Inject(method = "bake", at = @At("RETURN"))
    private static void onBake(MaterialBaker baker, CallbackInfoReturnable<Map<Fluid, FluidModel>> cir) {
        FluidModel waterModel = TranslucentLava.getWaterUnbaked().bake(baker, () -> "teslamaps:water_lava");
        FluidModel vanillaModel = TranslucentLava.getLavaUnbaked().bake(baker, () -> "teslamaps:vanilla_lava");
        TranslucentLava.setBakedModels(waterModel, vanillaModel);
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void onGet(FluidState state, CallbackInfoReturnable<FluidModel> cir) {
        Fluid fluid = state.getType();
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            FluidModel model = TranslucentLava.getCurrentModel();
            if (model != null) {
                cir.setReturnValue(model);
            }
        }
    }
}
