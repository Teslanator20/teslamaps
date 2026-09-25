package com.teslamaps.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.features.ItemNumbers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public class ItemNumbersMixin {

    @ModifyVariable(method = "itemCount", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String teslamaps$itemNumber(String text, @Local(argsOnly = true) ItemStack stack) {
        if (text != null || !ItemNumbers.on || stack.getCount() != 1) return text;
        return ItemNumbers.tip(stack);
    }
}
