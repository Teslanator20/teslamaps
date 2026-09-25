package com.teslamaps.mixin;

import com.teslamaps.render.SlotBackgrounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class SlotBackgroundMixin {

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void teslamaps$slotBackgrounds(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!com.teslamaps.features.GlobalRender.off()) SlotBackgrounds.draw(context, slot);
    }
}
