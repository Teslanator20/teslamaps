package com.teslamaps.mixin;

import com.teslamaps.features.PlayerModels;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
public class PlayerModelSubmitMixin {

    @ModifyVariable(method = "submit", at = @At("HEAD"), argsOnly = true)
    private EntityRenderState teslamaps$swapPlayerModel(EntityRenderState state) {
        return PlayerModels.swap(state);
    }
}
