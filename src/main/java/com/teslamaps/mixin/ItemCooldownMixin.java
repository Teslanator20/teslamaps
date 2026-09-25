package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.features.SbItemCooldowns;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public class ItemCooldownMixin {
    @ModifyExpressionValue(method = "itemCooldown", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;getCooldownPercent(Lnet/minecraft/world/item/ItemStack;F)F"))
    private float teslamaps$sbCooldown(float original, @Local(argsOnly = true) ItemStack stack) {
        return SbItemCooldowns.percent(stack, com.teslamaps.features.VisualTweaks.vanillaCooldown(original));
    }
}
