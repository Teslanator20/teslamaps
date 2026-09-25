package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.BigItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public class BigItemsMixin {
    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void teslamaps$bigItems(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y, int seed,
                                    Operation<Void> original) {
        float s = BigItems.scale(stack);
        if (s == 1f) {
            original.call(ctx, stack, x, y, seed);
            return;
        }
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 8f, y + 8f);
        ctx.pose().scale(s, s);
        ctx.pose().translate(-x - 8f, -y - 8f);
        original.call(ctx, stack, x, y, seed);
        ctx.pose().popMatrix();
    }
}
