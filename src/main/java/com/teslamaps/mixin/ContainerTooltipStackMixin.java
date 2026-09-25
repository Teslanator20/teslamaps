package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ContainerTooltipStackMixin {
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void teslamaps$tooltipHead(GuiGraphicsExtractor ctx, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (com.teslamaps.features.Experimentation.hideTooltip(self)) {
            ci.cancel();
            return;
        }
        com.teslamaps.features.TooltipBorder.prepare(self, ((HandledScreenAccessor) self).getFocusedSlot());
    }

    @WrapOperation(method = "extractTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack teslamaps$tooltipStack(Slot slot, Operation<ItemStack> original) {
        ItemStack stack = original.call(slot);
        ItemStack old = com.teslamaps.features.OldSkyblockMenu.replacement(slot);
        if (old != null) return old;
        ItemStack shown = com.teslamaps.features.AnimatedDyeFix.displayStack(slot);
        return shown != slot.getItem() ? shown : stack;
    }
}
