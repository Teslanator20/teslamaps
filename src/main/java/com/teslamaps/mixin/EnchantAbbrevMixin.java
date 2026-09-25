package com.teslamaps.mixin;

import com.teslamaps.features.EnchantAbbrev;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class EnchantAbbrevMixin {

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void teslamaps$enchantAbbrev(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        EnchantAbbrev.draw(context, slot.getItem(), slot.x, slot.y);
        com.teslamaps.features.ItemStars.draw(context, slot.getItem(), slot.x, slot.y);
        com.teslamaps.features.TimerTriggers.drawSlot(context, slot);
        com.teslamaps.features.TooltipTweaks.drawSlot(context, slot);
        com.teslamaps.features.ExperimentHelper.drawSlot(context, slot);
    }
}
