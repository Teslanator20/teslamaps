package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.features.ToolkitCropIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public class ToolkitIconMixin {
    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void teslamaps$toolkitIcon(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y, int seed,
                                       Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        ItemStack web = com.teslamaps.features.gardenqol.MutationsWebsite.replacement(slot);
        original.call(ctx, web != null ? web : com.teslamaps.features.GardenMenuHelpers.icon(slot,
                com.teslamaps.features.StereoHarmony.icon(slot, ToolkitCropIcons.icon(slot, stack))), x, y, seed);
    }
}
