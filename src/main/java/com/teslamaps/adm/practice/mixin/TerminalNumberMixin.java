package com.teslamaps.adm.practice.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.adm.practice.AdmTerms;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Hide Numbers' Numbers: the vanilla stack count on a numbers terminal's own slots.
@Mixin(AbstractContainerScreen.class)
public class TerminalNumberMixin {
    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void teslamaps$hideTerminalCount(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y, String count,
                                             Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        try {
            if (AdmTerms.hidesTerminalCount(slot)) return;
        } catch (Throwable ignored) {
        }
        original.call(g, font, stack, x, y, count);
    }
}
