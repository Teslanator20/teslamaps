package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public class ContainerTooltipStackMixin {
    @WrapOperation(method = "extractTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack teslamaps$tooltipStack(Slot slot, Operation<ItemStack> original) {
        ItemStack stack = original.call(slot);
        ItemStack shown = com.teslamaps.features.AnimatedDyeFix.displayStack(slot);
        return shown != slot.getItem() ? shown : stack;
    }
}
