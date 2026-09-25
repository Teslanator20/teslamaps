package com.teslamaps.mixin;

// Ported from SkyBack's TooltipBackgroundMixin
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TooltipRenderUtil.class)
public class TooltipStyleMixin {

    @ModifyVariable(method = "extractTooltipBackground", at = @At("HEAD"), argsOnly = true)
    private static Identifier teslamaps$vanillaTooltipStyle(Identifier style) {
        if (style == null || !TeslaMapsConfig.get().fixTooltipStyle) return style;
        return style.getNamespace().equals("minecraft") ? style : null;
    }
}
