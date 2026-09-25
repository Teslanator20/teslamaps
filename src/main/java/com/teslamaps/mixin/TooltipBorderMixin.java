package com.teslamaps.mixin;

import com.teslamaps.features.TooltipBorder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipRenderUtil.class)
public class TooltipBorderMixin {
    @Inject(method = "extractTooltipBackground", at = @At("RETURN"))
    private static void teslamaps$rarityBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Identifier style,
                                               CallbackInfo ci) {
        TooltipBorder.draw(ctx, x, y, w, h);
    }
}
