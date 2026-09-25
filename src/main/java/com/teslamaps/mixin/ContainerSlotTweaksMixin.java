package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.ContainerTweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ContainerSlotTweaksMixin {

    @Shadow protected Slot hoveredSlot;

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getNoItemIcon()Lnet/minecraft/resources/Identifier;"))
    private Identifier teslamaps$offhandIcon(Slot slot, Operation<Identifier> original) {
        return ContainerTweaks.noItemIcon(original.call(slot));
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void teslamaps$paneHighlight(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (ContainerTweaks.hideHighlight(hoveredSlot)) ci.cancel();
    }
}
