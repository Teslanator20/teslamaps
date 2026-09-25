package com.teslamaps.mixin;

import com.teslamaps.features.RarityContour;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class RarityContourRendererMixin {
    @Shadow @Final private GuiRenderState renderState;

    @Inject(method = "submitBlitFromItemAtlas", at = @At("TAIL"))
    private void teslamaps$drawContour(GuiItemRenderState item, GuiItemAtlas.SlotView view, CallbackInfo ci) {
        RarityContour.submit(renderState, item, view);
    }
}
