package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.teslamaps.features.CustomCrosshair;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public class CustomCrosshairMixin {

    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean teslamaps$thirdPerson(boolean original) {
        return CustomCrosshair.firstPerson(original);
    }

    @WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
            ordinal = 0))
    private void teslamaps$crosshair(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
                                     int x, int y, int w, int h, Operation<Void> original) {
        int mode = CustomCrosshair.mode();
        if (mode == 2) {
            CustomCrosshair.draw(g, x, y, w, h);
            return;
        }
        boolean shifted = mode == 1 && CustomCrosshair.pushCenter(g, x + w / 2f, y + h / 2f);
        original.call(g, pipeline, sprite, x, y, w, h);
        if (shifted) g.pose().popMatrix();
    }
}
