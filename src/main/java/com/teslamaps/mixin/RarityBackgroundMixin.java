package com.teslamaps.mixin;

import com.teslamaps.features.RarityBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the rarity background behind each slot's item (HEAD of extractSlot, before the item). */
@Mixin(AbstractContainerScreen.class)
public class RarityBackgroundMixin {

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void teslamaps$rarityBackground(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        RarityBackground.draw(context, slot.x, slot.y, slot.getItem());
    }
}
